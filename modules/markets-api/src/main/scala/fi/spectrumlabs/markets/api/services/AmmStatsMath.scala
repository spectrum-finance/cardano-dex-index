package fi.spectrumlabs.markets.api.services

import cats.effect.Clock
import cats.{Functor, Monad}
import fi.spectrumlabs.core.models.domain.{Apr, PoolId}
import fi.spectrumlabs.markets.api.v1.endpoints.models.TimeWindow
import tofu.higherKind.{Mid, RepresentableK}
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import tofu.syntax.time.now.millis

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.math.BigDecimal.RoundingMode

trait AmmStatsMath[F[_]] {

  def apr(
    poolId: PoolId,
    tvl: BigDecimal,
    fees: BigDecimal,
    firstSwapTimestamp: Long,
    projectionPeriod: FiniteDuration,
    tw: TimeWindow
  ): F[Apr]
}

object AmmStatsMath {

  implicit def representableK: RepresentableK[AmmStatsMath] =
    tofu.higherKind.derived.genRepresentableK

  def create[I[_]: Functor, F[_]: Monad: Clock](implicit logs: Logs[I, F]): I[AmmStatsMath[F]] =
    logs.forService[AmmStatsMath[F]].map { implicit __ =>
      new Tracing[F] attach new Live[F]
    }

  final class Live[F[_]: Monad: Clock] extends AmmStatsMath[F] {

    def apr(
      poolId: PoolId,
      tvl: BigDecimal,
      fees: BigDecimal,
      firstSwapTimestamp: Long,
      projectionPeriod: FiniteDuration,
      tw: TimeWindow
    ): F[Apr] =
      for {
        windowSizeSeconds <-
          for {
            ub <- tw.to.fold(Clock[F].realTime(TimeUnit.SECONDS))(_.pure[F])
            lb = tw.from.getOrElse(firstSwapTimestamp)
          } yield ub - lb
        periodFees =
          if (windowSizeSeconds > 0) fees * (BigDecimal(projectionPeriod.toSeconds) / windowSizeSeconds)
          else BigDecimal(0)
        periodFeesPercent =
          if (tvl > 0) {
            (periodFees * 100 / tvl).setScale(2, RoundingMode.HALF_UP).toDouble
          } else 0.0
      } yield Apr(BigDecimal(periodFeesPercent).setScale(2).doubleValue)
  }

  final class Tracing[F[_]: Monad: Logging] extends AmmStatsMath[Mid[F, *]] {

    def apr(
      poolId: PoolId,
      tvl: BigDecimal,
      fees: BigDecimal,
      firstSwapTimestamp: Long,
      projectionPeriod: FiniteDuration,
      tw: TimeWindow
    ): Mid[F, Apr] =
      for {
        _ <-
          trace"apr(poolId=$poolId,tvl=${tvl.toString},fees=${fees.toString},firstSwapTimestamp=$firstSwapTimestamp,projectionPeriod=$projectionPeriod)"
        r <- _
        _ <-
          trace"apr(poolId=$poolId,tvl=${tvl.toString},fees=${fees.toString},firstSwapTimestamp=$firstSwapTimestamp,projectionPeriod=$projectionPeriod) -> $r"
      } yield r
  }
}
