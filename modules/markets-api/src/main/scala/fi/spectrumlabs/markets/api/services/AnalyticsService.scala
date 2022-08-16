package fi.spectrumlabs.markets.api.services

import cats.data.OptionT
import cats.syntax.parallel._
import cats.{Functor, Monad, Parallel}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{Amount, Pool, PoolFee, PoolId}
import fi.spectrumlabs.markets.api.configs.MarketsApiConfig
import fi.spectrumlabs.markets.api.models.{PoolInfo, PoolOverview, PricePoint}
import fi.spectrumlabs.markets.api.repositories.repos.{PoolsRepo, RatesRepo}
import fi.spectrumlabs.markets.api.v1.endpoints.models.TimeWindow
import fi.spectrumlabs.rates.resolver.services.TokenFetcher
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import scala.concurrent.duration.FiniteDuration
import scala.math.BigDecimal.RoundingMode

@derive(representableK)
trait AnalyticsService[F[_]] {
  def getPoolsOverview(period: FiniteDuration): F[List[PoolOverview]]

  def getPoolInfo(poolId: PoolId, period: FiniteDuration): F[Option[PoolInfo]]

  def getPoolPriceChart(poolId: PoolId, window: TimeWindow, resolution: Long): F[List[PricePoint]]
}

object AnalyticsService {

  def create[I[_]: Functor, F[_]: Monad: Parallel](config: MarketsApiConfig)(implicit
    tokenFetcher: TokenFetcher[F],
    ratesRepo: RatesRepo[F],
    poolsRepo: PoolsRepo[F],
    logs: Logs[I, F]
  ): I[AnalyticsService[F]] =
    logs.forService[AnalyticsService[F]].map(implicit __ => new Tracing[F] attach new Impl[F](config))

  final private class Impl[F[_]: Monad: Parallel](config: MarketsApiConfig)(implicit
    tokenFetcher: TokenFetcher[F],
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

    def getPoolPriceChart(poolId: PoolId, window: TimeWindow, resolution: Long): F[List[PricePoint]] =
      (for {
        amounts     <- OptionT.liftF(poolsRepo.getAvgPoolSnapshot(poolId, window, resolution))
        pool        <- OptionT(poolsRepo.getPoolById(poolId, 0))
        assetRate    <- OptionT(ratesRepo.get(pool.x, poolId))
        validTokens <- OptionT.liftF(tokenFetcher.fetchTokens)
        points = if (validTokens.contains(pool.x) && validTokens.contains(pool.y))
                   amounts.map { amount =>
                     PricePoint(amount.timestamp, assetRate.rate).setScale(PricePoint.defaultScale)
                   }
                 else List.empty[PricePoint]
      } yield points).value.map(_.toList.flatten)
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

    def getPoolPriceChart(poolId: PoolId, window: TimeWindow, resolution: Long): Mid[F, List[PricePoint]] =
      for {
        _ <- trace"Going to get pool price chart for pool $poolId for period $resolution seconds within $window"
        r <- _
        _ <- trace"Pool price chart is $r"
      } yield r
  }
}
