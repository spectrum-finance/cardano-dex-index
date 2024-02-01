package fi.spectrumlabs.markets.api.services

import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.syntax.option._
import cats.syntax.parallel._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.{Functor, Monad, Parallel}
import derevo.derive
import fi.spectrumlabs.core.models.{db, domain}
import fi.spectrumlabs.core.models.domain.{Amount, AssetAmount, Pool, PoolFee, PoolId}
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.markets.api.configs.MarketsApiConfig
import fi.spectrumlabs.markets.api.models.db.{PoolDb, PoolDbNew}
import fi.spectrumlabs.markets.api.models.{
  PlatformStats,
  PoolList,
  PoolOverview,
  PoolOverviewNew,
  PoolState,
  PricePoint,
  RealPrice
}
import fi.spectrumlabs.markets.api.repositories.repos.{PoolsRepo, RatesRepo}
import fi.spectrumlabs.markets.api.v1.endpoints.models.TimeWindow
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import tofu.time.Clock

import java.util.concurrent.TimeUnit
import fi.spectrumlabs.core.{AdaAssetClass, AdaDecimal}
import fi.spectrumlabs.markets.api.v1.models.{CMCTicker, CoinGeckoTicker}
import fi.spectrumlabs.rates.resolver.gateways.Tokens

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.math.BigDecimal
import scala.math.BigDecimal.RoundingMode

@derive(representableK)
trait AnalyticsService[F[_]] {

  def getLatestPoolsStates: F[List[PoolOverviewNew]]

  def getPoolsOverview: F[List[PoolOverview]]

  def getPoolInfo(poolId: PoolDb, from: Long): F[Option[PoolOverview]]

  def getPoolInfo(poolId: PoolId, from: Long): F[Option[PoolOverview]]

  def getPoolPriceChart(poolId: PoolId, window: TimeWindow, resolution: Long): F[List[PricePoint]]

  def getPlatformStats: F[PlatformStats]

  def updatePoolsOverview: F[List[PoolOverview]]

  def updateLatestPoolsStates: F[List[PoolOverviewNew]]

  def getPoolList: F[PoolList]

  def getPoolStateByDate(poolId: PoolId, date: Long): F[Option[PoolState]]

  def cgPriceApi: F[List[CoinGeckoTicker]]

  def cmcPriceApi: F[Map[String, CMCTicker]]
}

object AnalyticsService {

  private val MillisInYear: FiniteDuration = 365.days

  def create[I[_]: Functor, F[_]: Monad: Parallel: Clock](
    config: MarketsApiConfig,
    cache: Ref[F, List[PoolOverview]],
    cache2: Ref[F, List[PoolOverviewNew]],
    cache3: Ref[F, Option[BigDecimal]]
  )(implicit
    ratesRepo: RatesRepo[F],
    poolsRepo: PoolsRepo[F],
    ammStatsMath: AmmStatsMath[F],
    tokens: Tokens[F],
    logs: Logs[I, F]
  ): I[AnalyticsService[F]] =
    logs
      .forService[AnalyticsService[F]]
      .map(implicit __ => new Tracing[F] attach new Impl[F](config, cache, cache2, cache3))

  final private class Impl[F[_]: Monad: Parallel: Clock](
    config: MarketsApiConfig,
    cache: Ref[F, List[PoolOverview]],
    cache2: Ref[F, List[PoolOverviewNew]],
    adaRate: Ref[F, Option[BigDecimal]]
  )(implicit
    ratesRepo: RatesRepo[F],
    poolsRepo: PoolsRepo[F],
    ammStatsMath: AmmStatsMath[F],
    tokens: Tokens[F]
  ) extends AnalyticsService[F] {

    def getPoolStateByDate(poolId: PoolId, date: Long): F[Option[PoolState]] =
      (for {
        poolDb <- OptionT(poolsRepo.getPoolStateByDate(poolId, date))
        pool   <- OptionT.pure(Pool.fromDb(poolDb)).filter(_.contains(AdaAssetClass))
        adaAmount = if (pool.x.asset == AdaAssetClass) pool.x.amount else pool.y.amount
      } yield PoolState(pool.id, (adaAmount.withDecimal(AdaDecimal) * 2).setScale(6, RoundingMode.HALF_UP))).value

    def getPoolList: F[PoolList] =
      poolsRepo.getPoolList.map(pools => PoolList(pools, pools.size))

    def getLatestPoolsStates: F[List[PoolOverviewNew]] =
      cache2.get

    def updateLatestPoolsStates: F[List[PoolOverviewNew]] =
      poolsRepo.getPools.map(
        _.map { pool: PoolDbNew =>
          val p = Pool(pool.poolId, AssetAmount(pool.x, pool.xReserves), AssetAmount(pool.y, pool.yReserves))
          PoolOverviewNew(
            p.id,
            p.x,
            p.y,
            AssetAmount(pool.lq, pool.lqReserved),
            pool.feeNum,
            pool.feeDen
          )
        }
      )

    def updatePoolsOverview: F[List[PoolOverview]] = Clock[F].realTime(TimeUnit.SECONDS) >>= { now =>
      poolsRepo.getPoolsOld.flatMap(
        _.parTraverse { p: PoolDb =>
          getPoolInfo(p, now - 24.hours.toSeconds)
        }.map(_.flatten)
      )
    }

    def getPoolsOverview: F[List[PoolOverview]] = cache.get

    def cgPriceApi: F[List[CoinGeckoTicker]] = cache.get.flatMap { pools =>
      pools
        .map { pool =>
          for {
            rateX <- OptionT(ratesRepo.get(pool.lockedX.asset)).map(_.rate).flatMap { x =>
              if (x == BigDecimal(0)) OptionT.none[F, BigDecimal] else OptionT.pure(x)
            }
            rateY <- OptionT(ratesRepo.get(pool.lockedY.asset)).map(_.rate).flatMap { x =>
              if (x == BigDecimal(0)) OptionT.none[F, BigDecimal] else OptionT.pure(x)
            }
            adaRate <- OptionT(adaRate.get)

            xCs = if (pool.lockedX.asset.currencySymbol == "") "ADA" else pool.lockedX.asset.currencySymbol
            yCs = if (pool.lockedY.asset.currencySymbol == "") "ADA" else pool.lockedY.asset.currencySymbol

            ticker = if (xCs == "ADA") s"${yCs}_$xCs" else s"${xCs}_$yCs"

            price =
              if (xCs == "ADA")
                ((rateX * adaRate) / (rateY * adaRate))
              else
                ((rateY * adaRate) / (rateX * adaRate))

            volumeX =
              if (xCs == "ADA")
                (pool.volume.getOrElse(BigDecimal(0)) / rateY).setScale(10, RoundingMode.HALF_UP)
              else (pool.volume.getOrElse(BigDecimal(0)) / rateX).setScale(10, RoundingMode.HALF_UP)
            volumeY =
              if (xCs == "ADA")
                (pool.volume.getOrElse(BigDecimal(0)) / rateX).setScale(10, RoundingMode.HALF_UP)
              else (pool.volume.getOrElse(BigDecimal(0)) / rateY).setScale(10, RoundingMode.HALF_UP)

          } yield CoinGeckoTicker(
            pool_id          = pool.id.value,
            ticker_id        = ticker,
            base_currency    = if (xCs == "ADA") yCs else xCs,
            target_currency  = if (xCs == "ADA") xCs else yCs,
            last_price       = price.setScale(10, RoundingMode.HALF_UP),
            liquidity_in_usd = (pool.tvl.getOrElse(BigDecimal(0)) * adaRate).setScale(10, RoundingMode.HALF_UP),
            base_volume      = if (volumeX == BigDecimal("0E-10")) BigDecimal(0) else volumeX,
            target_volume    = if (volumeY == BigDecimal("0E-10")) BigDecimal(0) else volumeY
          )
        }
        .map(_.value)
        .sequence
        .map(_.flatten)
        .map { pairs =>
          pairs
            .map(x => (x.base_currency, x.target_currency) -> x)
            .groupBy(_._1)
            .map(_._2.maxBy(_._2.liquidity_in_usd))
            .values
            .toList
            .filter(_.liquidity_in_usd != BigDecimal("0"))
        }
        .map(_.sortBy(_.liquidity_in_usd)(Ordering[BigDecimal].reverse))
    }

    def cmcPriceApi: F[Map[String, CMCTicker]] = cache.get.flatMap { pools =>
      tokens.get.flatMap { tokens =>
        pools
          .map { pool =>
            for {
              rateX <- OptionT(ratesRepo.get(pool.lockedX.asset)).flatMap { x =>
                if (x.rate == BigDecimal(0)) OptionT.none[F, ResolvedRate] else OptionT.pure(x)
              }
              rateY <- OptionT(ratesRepo.get(pool.lockedY.asset)).flatMap { x =>
                if (x.rate == BigDecimal(0)) OptionT.none[F, ResolvedRate] else OptionT.pure(x)
              }
              adaRate <- OptionT(adaRate.get)
              tvl       = (pool.tvl.getOrElse(BigDecimal(0)) * adaRate).setScale(10, RoundingMode.HALF_UP)
              xCs       = if (pool.lockedX.asset.currencySymbol.show == "") "ADA" else pool.lockedX.asset.currencySymbol.show
              yCs       = if (pool.lockedY.asset.currencySymbol.show == "") "ADA" else pool.lockedY.asset.currencySymbol.show
              ticker_id = if (xCs == "ADA") s"${yCs}_$xCs" else s"${xCs}_$yCs"
              xBaseName = if (pool.lockedX.asset.tokenName.show == "") "ADA" else pool.lockedX.asset.tokenName.show
              yBaseName = if (pool.lockedY.asset.tokenName.show == "") "ADA" else pool.lockedY.asset.tokenName.show
              xTicker   = tokens.find(info => info.asset == pool.lockedX.asset).map(_.ticker).getOrElse(xBaseName)
              yTicker   = tokens.find(info => info.asset == pool.lockedY.asset).map(_.ticker).getOrElse(yBaseName)
              price =
                if (xCs == "ADA") (rateX.rate * adaRate) / (rateY.rate * adaRate)
                else (rateY.rate * adaRate) / (rateX.rate * adaRate)
              value = CMCTicker(
                if (xCs == "ADA") yCs else xCs,
                if (xCs == "ADA") yBaseName else xBaseName,
                if (xCs == "ADA") yTicker else xTicker,
                if (xCs == "ADA") xCs else yCs,
                if (xCs == "ADA") xBaseName else yBaseName,
                if (xCs == "ADA") xTicker else yTicker,
                price.setScale(10, RoundingMode.HALF_UP).toString(),
                if (xCs == "ADA")
                  pool.lockedY.amount.withDecimal(rateY.decimals).setScale(10, RoundingMode.HALF_UP).toString()
                else pool.lockedX.amount.withDecimal(rateX.decimals).setScale(10, RoundingMode.HALF_UP).toString(),
                if (xCs == "ADA")
                  pool.lockedX.amount.withDecimal(rateX.decimals).setScale(10, RoundingMode.HALF_UP).toString()
                else pool.lockedY.amount.withDecimal(rateY.decimals).setScale(10, RoundingMode.HALF_UP).toString()
              )
            } yield (ticker_id, tvl) -> value
          }
          .map(_.value)
          .sequence
          .map(_.flatten)
          .map { pairs =>
            pairs
              .map(x => (x._2.base_id, x._2.quote_id) -> x)
              .groupBy(_._1)
              .map(_._2.maxBy(_._2._1._2))
              .values
              .toList
              .filter(_._1._2 != BigDecimal("0"))
              .map(x => x._1._1 -> x._2)
              .toMap
          }
      }
    }

    def getPoolInfo(poolId: PoolId, from: Long): F[Option[PoolOverview]] =
      (for {
        poolDb <- OptionT(poolsRepo.getPoolById(poolId, config.minLiquidityValue))
        pool = Pool.fromDb(poolDb)
        rateX <- OptionT(ratesRepo.get(pool.x.asset))
        rateY <- OptionT(ratesRepo.get(pool.y.asset))
        xTvl     = pool.x.amount.withDecimal(rateX.decimals) * rateX.rate
        yTvl     = pool.y.amount.withDecimal(rateY.decimals) * rateY.rate
        totalTvl = (xTvl + yTvl).setScale(6, RoundingMode.HALF_UP)
        poolVolume <- OptionT.liftF(poolsRepo.getPoolVolume(pool, from))
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
        now <- OptionT.liftF(Clock[F].realTime(TimeUnit.SECONDS))
        tw = TimeWindow(Some(from), Some(now))
        firstSwap <- OptionT.liftF(poolsRepo.getFirstPoolSwapTime(pool.id))
        fee       <- OptionT(poolsRepo.fees(pool, tw, poolDb.fees))
        feeX     = Amount(fee.x.toLong).withDecimal(rateX.decimals) * rateX.rate
        feeY     = Amount(fee.y.toLong).withDecimal(rateY.decimals) * rateY.rate
        totalFee = feeX + feeY
        apr <- OptionT.liftF(ammStatsMath.apr(pool.id, totalTvl, totalFee, firstSwap.getOrElse(0), MillisInYear, tw))
      } yield PoolOverview(
        pool.id,
        AssetAmount(poolDb.x, Amount(poolDb.xReserves)),
        AssetAmount(poolDb.y, Amount(poolDb.yReserves)),
        totalTvl.some,
        totalVolume.some,
        fee,
        apr
      )).value

    def getPoolInfo(in: PoolDb, from: Long): F[Option[PoolOverview]] = {
      val poolDb = db.Pool(
        in.poolId.value,
        in.x,
        in.xReserves.value,
        in.y,
        in.yReserves.value,
        PoolFee(
          in.feeNum,
          in.feeDen
        )
      )
      val pool = domain.Pool.fromDb(poolDb)
      (for {
        rateX <- OptionT(ratesRepo.get(pool.x.asset))
        rateY <- OptionT(ratesRepo.get(pool.y.asset))
        xTvl     = pool.x.amount.withDecimal(rateX.decimals) * rateX.rate
        yTvl     = pool.y.amount.withDecimal(rateY.decimals) * rateY.rate
        totalTvl = (xTvl + yTvl).setScale(6, RoundingMode.HALF_UP)
        poolVolume <- OptionT.liftF(poolsRepo.getPoolVolume(pool, from))
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
        now <- OptionT.liftF(Clock[F].realTime(TimeUnit.SECONDS))
        tw = TimeWindow(Some(from), Some(now))
        firstSwap <- OptionT.liftF(poolsRepo.getFirstPoolSwapTime(pool.id))
        fee       <- OptionT(poolsRepo.fees(pool, tw, poolDb.fees))
        feeX     = Amount(fee.x.toLong).withDecimal(rateX.decimals) * rateX.rate
        feeY     = Amount(fee.y.toLong).withDecimal(rateY.decimals) * rateY.rate
        totalFee = feeX + feeY
        apr <- OptionT.liftF(ammStatsMath.apr(pool.id, totalTvl, totalFee, firstSwap.getOrElse(0), MillisInYear, tw))
      } yield PoolOverview(
        pool.id,
        AssetAmount(poolDb.x, Amount(poolDb.xReserves)),
        AssetAmount(poolDb.y, Amount(poolDb.yReserves)),
        totalTvl.some,
        totalVolume.some,
        fee,
        apr
      )).value
    }

    def getPlatformStats: F[PlatformStats] =
      for {
        now <- Clock[F].realTime(TimeUnit.SECONDS)
        period = TimeWindow(from = (now - 24.hours.toSeconds).some, none)
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
        xMeta   <- OptionT.liftF(ratesRepo.get(pool.x))
        yMeta   <- OptionT.liftF(ratesRepo.get(pool.y))
        points = amounts
          .map { amount =>
            val price =
              RealPrice.calculate(amount.amountX, xMeta.map(_.decimals), amount.amountY, yMeta.map(_.decimals))
            PricePoint(amount.avgTimestamp, price.setScale(RealPrice.defaultScale))
          }
          .sortBy(_.timestamp)
      } yield points).value.map(_.toList.flatten)
  }

  final private class Tracing[F[_]: Monad: Logging] extends AnalyticsService[Mid[F, *]] {

    def getPoolStateByDate(poolId: PoolId, date: Long): Mid[F, Option[PoolState]] =
      for {
        _ <- trace"Going to get $poolId pool state by $date"
        r <- _
        _ <- trace"Pool state is $r"
      } yield r

    def getPoolsOverview: Mid[F, List[PoolOverview]] =
      for {
        _ <- trace"Going to get pools overview"
        r <- _
        _ <- trace"Pools overview is $r"
      } yield r

    def getPoolList: Mid[F, PoolList] =
      for {
        _ <- trace"Going to get pools list"
        r <- _
        _ <- trace"Pools list is $r"
      } yield r

    def getPoolInfo(poolId: PoolDb, from: Long): Mid[F, Option[PoolOverview]] =
      for {
        _ <- trace"Going to get pool info for pool $poolId from $from"
        r <- _
        _ <- trace"Pool info is $r"
      } yield r

    def getPoolPriceChart(poolId: PoolId, window: TimeWindow, resolution: Long): Mid[F, List[PricePoint]] =
      for {
        _ <- trace"Going to get pool price chart for pool $poolId for period $resolution seconds within $window"
        r <- _
        _ <- trace"Pool price chart is $r"
      } yield r

    def getPlatformStats: Mid[F, PlatformStats] =
      for {
        _ <- trace"Going to get platform stats"
        r <- _
        _ <- trace"Platform stats are $r"
      } yield r

    def updatePoolsOverview: Mid[F, List[PoolOverview]] =
      for {
        _ <- trace"updatePoolsOverview"
        r <- _
        _ <- trace"updatePoolsOverview -> $r"
      } yield r

    def getLatestPoolsStates: Mid[F, List[PoolOverviewNew]] =
      for {
        _ <- trace"getLatestPoolsStates"
        r <- _
        _ <- trace"getLatestPoolsStates -> $r"
      } yield r

    def getPoolInfo(poolId: PoolId, from: Long): Mid[F, Option[PoolOverview]] =
      for {
        _ <- trace"getPoolInfo"
        r <- _
        _ <- trace"getPoolInfo -> $r"
      } yield r

    def updateLatestPoolsStates: Mid[F, List[PoolOverviewNew]] =
      for {
        _ <- trace"updateLatestPoolsStates"
        r <- _
        _ <- trace"updateLatestPoolsStates -> $r"
      } yield r

    def cgPriceApi: Mid[F, List[CoinGeckoTicker]] =
      for {
        _ <- trace"cgPriceApi"
        r <- _
        _ <- trace"cgPriceApi -> ${r.toString()}"
      } yield r

    def cmcPriceApi =
      for {
        _ <- trace"cmcPriceApi"
        r <- _
        _ <- trace"cmcPriceApi -> ${r.toString()}"
      } yield r

  }
}
