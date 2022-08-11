package fi.spectrumlabs.markets.api.services

import cats.data.OptionT
import cats.{Functor, Monad, Parallel}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{Amount, AssetClass, Pool, PoolFee, PoolId}
import fi.spectrumlabs.markets.api.configs.MarketsApiConfig
import fi.spectrumlabs.markets.api.models.{PoolInfo, PoolOverview}
import fi.spectrumlabs.markets.api.repositories.repos.{PoolsRepo, RatesRepo}
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import cats.syntax.parallel._
import cats.syntax.option._

import scala.concurrent.duration.FiniteDuration
import scala.math.BigDecimal.RoundingMode

@derive(representableK)
trait AnalyticsService[F[_]] {
  def getPoolsOverview(period: FiniteDuration): F[List[PoolOverview]]

  def getPoolInfo(poolId: PoolId, period: FiniteDuration): F[Option[PoolInfo]]
}

object AnalyticsService {

  def create[I[_]: Functor, F[_]: Monad: Parallel](config: MarketsApiConfig)(implicit
    ratesRepo: RatesRepo[F],
    poolsRepo: PoolsRepo[F],
    logs: Logs[I, F]
  ): I[AnalyticsService[F]] =
    logs.forService[AnalyticsService[F]].map(implicit __ => new Tracing[F] attach new Impl[F](config))

  final private class Impl[F[_]: Monad: Parallel](config: MarketsApiConfig)(implicit
    ratesRepo: RatesRepo[F],
    poolsRepo: PoolsRepo[F]
  ) extends AnalyticsService[F] {

    def getPoolsOverview(period: FiniteDuration): F[List[PoolOverview]] =
      poolsRepo.getPools.flatMap(
        _.parTraverse { p =>
          getPoolInfo(p.poolId, period).map { info =>
            PoolOverview(
              p.poolId,
              p.x,
              p.y,
              p.xReserves,
              p.yReserves,
              info,
              PoolFee(p.feeNum, p.feeDen)
            )
          }
        }
      )

    def getPoolInfo(poolId: PoolId, period: FiniteDuration): F[Option[PoolInfo]] =
      (for {
        poolDb <- OptionT(poolsRepo.getPoolById(poolId, config.minLiquidityValue))
        pool = Pool.fromDb(poolDb)
        rateX <- OptionT(ratesRepo.get(pool.x.asset, pool.id))
        rateY <- OptionT(ratesRepo.get(pool.y.asset, pool.id))
        xTvl     = pool.x.amount.dropPenny(rateX.decimals) * rateX.rate
        yTvl     = pool.y.amount.dropPenny(rateY.decimals) * rateY.rate
        totalTvl = (xTvl + yTvl).setScale(0, RoundingMode.HALF_UP)
        poolVolume <- OptionT(poolsRepo.getPoolVolume(pool, period))
        xVolume = poolVolume.xVolume
                    .map(r => Amount(r.longValue).dropPenny(rateX.decimals))
                    .getOrElse(BigDecimal(0)) * rateX.rate
        yVolume = poolVolume.yVolume
                    .map(r => Amount(r.longValue).dropPenny(rateY.decimals))
                    .getOrElse(BigDecimal(0)) * rateY.rate
        totalVolume = (xVolume + yVolume).setScale(0, RoundingMode.HALF_UP)
      } yield PoolInfo(totalTvl, totalVolume)).value

  }

  final private class Tracing[F[_]: Monad: Logging] extends AnalyticsService[Mid[F, *]] {

    def getPoolsOverview(period: FiniteDuration): Mid[F, List[PoolOverview]] =
      for {
        _ <- trace"Going to get pools overview for period $period"
        r <- _
        _ <- trace"Pools overview is $r"
      } yield r

    def getPoolInfo(poolId: PoolId, period: FiniteDuration): Mid[F, Option[PoolInfo]] =
      for {
        _ <- trace"Going to get pool info for pool $poolId for period $period"
        r <- _
        _ <- trace"Pool info is $r"
      } yield r
  }
}
