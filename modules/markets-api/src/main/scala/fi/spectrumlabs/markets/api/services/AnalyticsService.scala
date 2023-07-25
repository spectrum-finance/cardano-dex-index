package fi.spectrumlabs.markets.api.services

import cats.data.OptionT
import cats.syntax.option._
import cats.syntax.parallel._
import cats.{Functor, Monad, Parallel}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{Amount, AssetAmount, Pool, PoolFee, PoolId}
import fi.spectrumlabs.markets.api.configs.MarketsApiConfig
import fi.spectrumlabs.markets.api.models.{PlatformStats, PoolOverview, PricePoint, RealPrice}
import fi.spectrumlabs.markets.api.repositories.repos.{PoolsRepo, RatesRepo}
import fi.spectrumlabs.markets.api.v1.endpoints.models.TimeWindow
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import cats.syntax.traverse._
import fi.spectrumlabs.markets.api.models.db.{PoolDb, PoolFeeSnapshot}
import tofu.syntax.time.now.millis
import tofu.time.Clock

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.math.BigDecimal.RoundingMode

@derive(representableK)
trait AnalyticsService[F[_]] {
  def getPoolsOverview(period: FiniteDuration): F[List[PoolOverview]]

  def getPoolInfo(poolId: PoolId, period: FiniteDuration): F[Option[PoolOverview]]

  def getPoolPriceChart(poolId: PoolId, window: TimeWindow, resolution: Long): F[List[PricePoint]]

  def getPlatformStats(period: TimeWindow): F[PlatformStats]
}

object AnalyticsService {

  private val MillisInYear: FiniteDuration = 365.days

  def create[I[_]: Functor, F[_]: Monad: Parallel: Clock](config: MarketsApiConfig)(implicit
    ratesRepo: RatesRepo[F],
    poolsRepo: PoolsRepo[F],
    ammStatsMath: AmmStatsMath[F],
    logs: Logs[I, F]
  ): I[AnalyticsService[F]] =
    logs.forService[AnalyticsService[F]].map(implicit __ => new Tracing[F] attach new Impl[F](config))

  final private class Impl[F[_]: Monad: Parallel: Clock](config: MarketsApiConfig)(implicit
    ratesRepo: RatesRepo[F],
    poolsRepo: PoolsRepo[F],
    ammStatsMath: AmmStatsMath[F]
  ) extends AnalyticsService[F] {

    def getPoolsOverview(period: FiniteDuration): F[List[PoolOverview]] =
      poolsRepo.getPools.flatMap(
        _.parTraverse { p: PoolDb =>
          poolsRepo.getFirstPoolSwapTime(p.poolId).flatMap { firstSwap =>
            getPoolInfo(p.poolId, period).flatMap { info =>
              val pool = Pool(p.poolId, AssetAmount(p.x, p.xReserves), AssetAmount(p.y, p.yReserves))
              millis.flatMap { now =>
                val tw = TimeWindow(Some(period.toMillis), Some(now))
                poolsRepo.fees(pool, tw, PoolFee(p.feeNum, p.feeDen)).flatMap { fee =>
                  ammStatsMath
                    .apr(
                      p.poolId,
                      info.flatMap(_.tvl).getOrElse(BigDecimal(0)),
                      fee.map(s => s.x + s.y).getOrElse(BigDecimal(0)),
                      firstSwap.getOrElse(0),
                      MillisInYear,
                      tw
                    )
                    .map { apr =>
                      PoolOverview(
                        p.poolId,
                        AssetAmount(p.x, p.xReserves),
                        AssetAmount(p.y, p.yReserves),
                        info.flatMap(_.tvl),
                        info.flatMap(_.volume),
                        fee.getOrElse(PoolFeeSnapshot(BigDecimal(0), BigDecimal(0))),
                        apr
                      )
                    }
                }
              }
            }
          }
        }
      )

    def getPoolInfo(poolId: PoolId, period: FiniteDuration): F[Option[PoolOverview]] =
      (for {
        poolDb <- OptionT(poolsRepo.getPoolById(poolId, config.minLiquidityValue))
        pool = Pool.fromDb(poolDb)
        rateX <- OptionT(ratesRepo.get(pool.x.asset))
        rateY <- OptionT(ratesRepo.get(pool.y.asset))
        xTvl     = pool.x.amount.withDecimal(rateX.decimals) * rateX.rate
        yTvl     = pool.y.amount.withDecimal(rateY.decimals) * rateY.rate
        totalTvl = (xTvl + yTvl).setScale(6, RoundingMode.HALF_UP)
        poolVolume <- OptionT.liftF(poolsRepo.getPoolVolume(pool, period))
        totalVolume = poolVolume
          .map { volume =>
            val xVolume = volume.xVolume
              .map(r => Amount(r.longValue).withDecimal(rateX.decimals))
              .getOrElse(BigDecimal(0)) * rateX.rate
            val yVolume = volume.yVolume
              .map(r => Amount(r.longValue).withDecimal(rateY.decimals))
              .getOrElse(BigDecimal(0)) * rateY.rate
            xVolume + yVolume
          }
          .getOrElse(BigDecimal(0))
          .setScale(6, RoundingMode.HALF_UP)
        now <- OptionT.liftF(millis)
        tw = TimeWindow(Some(period.toMillis), Some(now))
        firstSwap <- OptionT.liftF(poolsRepo.getFirstPoolSwapTime(poolId))
        fee       <- OptionT(poolsRepo.fees(pool, tw, poolDb.fees))
        apr <- OptionT.liftF(
          ammStatsMath.apr(poolId, totalTvl, fee.x + fee.y, firstSwap.getOrElse(0), MillisInYear, tw)
        )
      } yield PoolOverview(
        poolId,
        AssetAmount(poolDb.x, Amount(poolDb.xReserves)),
        AssetAmount(poolDb.y, Amount(poolDb.yReserves)),
        totalTvl.some,
        totalVolume.some,
        fee,
        apr
      )).value

    def getPlatformStats(period: TimeWindow): F[PlatformStats] =
      for {
        pools  <- poolsRepo.getPools
        xRates <- pools.flatTraverse(pool => ratesRepo.get(pool.x).map(_.map((pool, _)).toList))
        yRates <- pools.flatTraverse(pool => ratesRepo.get(pool.y).map(_.map((pool, _)).toList))
        xTvls = xRates.map { case (pool, rate) =>
          pool.xReserves.dropPenny(rate.decimals) * rate.rate
        }.sum
        yTvls = yRates.map { case (pool, rate) =>
          pool.yReserves.dropPenny(rate.decimals) * rate.rate
        }.sum
        totalTvl = (xTvls + yTvls).setScale(0, RoundingMode.HALF_UP)
        poolVolumes <- poolsRepo.getPoolVolumes(period)
        volumes = xRates.flatMap { case (pool, rate) =>
          poolVolumes.find(_.poolId == pool.poolId).map { volume =>
            if (rate.asset == pool.x) {
              val x = volume.x / BigDecimal(10).pow(rate.decimals) * rate.rate
              x
            } else {
              val y = volume.y / BigDecimal(10).pow(rate.decimals) * rate.rate
              y
            }
          }
        }.sum
        volumesY = yRates.flatMap { case (pool, rate) =>
          poolVolumes.find(_.poolId == pool.poolId).map { volume =>
            if (rate.asset == pool.x) {
              val x = volume.x / BigDecimal(10).pow(rate.decimals) * rate.rate
              x
            } else {
              val y = volume.y / BigDecimal(10).pow(rate.decimals) * rate.rate
              y
            }
          }
        }.sum
        totalVolume = (volumes + volumesY).setScale(0, RoundingMode.HALF_UP)
      } yield PlatformStats(totalTvl, totalVolume)

    def getPoolPriceChart(poolId: PoolId, window: TimeWindow, resolution: Long): F[List[PricePoint]] =
      (for {
        amounts <- OptionT.liftF(poolsRepo.getAvgPoolSnapshot(poolId, window, resolution))
        pool    <- OptionT(poolsRepo.getPoolById(poolId, config.minLiquidityValue))
        points = List.empty[PricePoint]
      } yield points).value.map(_.toList.flatten)
  }

  final private class Tracing[F[_]: Monad: Logging] extends AnalyticsService[Mid[F, *]] {

    def getPoolsOverview(period: FiniteDuration): Mid[F, List[PoolOverview]] =
      for {
        _ <- trace"Going to get pools overview for period $period"
        r <- _
        _ <- trace"Pools overview is $r"
      } yield r

    def getPoolInfo(poolId: PoolId, period: FiniteDuration): Mid[F, Option[PoolOverview]] =
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

    def getPlatformStats(period: TimeWindow): Mid[F, PlatformStats] =
      for {
        _ <- trace"Going to get platform stats for $period period"
        r <- _
        _ <- trace"Platform stats are $r"
      } yield r
  }
}
