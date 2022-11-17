package fi.spectrumlabs.rates.resolver.services

import cats.syntax.eq._
import cats.effect.SyncIO
import fi.spectrumlabs.core.{AdaAssetClass, AdaDecimal}
import fi.spectrumlabs.core.models.domain.{AssetClass, Pool}
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.rates.resolver.config.ResolverConfig
import fi.spectrumlabs.rates.resolver.gateways.Network
import fi.spectrumlabs.rates.resolver.mocks.{MetadataServiceMock, NetworkMock, PoolsServiceMock, TokenFetcherMock}
import fi.spectrumlabs.rates.resolver.models.Models._
import org.scalacheck._
import org.specs2.mutable.Specification
import tofu.logging.Logs

import scala.concurrent.duration.DurationInt

class ResolverServiceTests extends Specification {

  /**  pair x -> y = y / x
    *  pair y -> x = x / y
    */
  implicit val logsNoOp: Logs[SyncIO, SyncIO] = Logs.empty[SyncIO, SyncIO]
  val conf: ResolverConfig                    = ResolverConfig(10.seconds, 10)

  "Resolver should" >> {
    "resolve ada to token pairs correct" in {
      val adaRate: BigDecimal = BigDecimal(0.5)
      val pools: List[Pool] = List(
        genAdaPool.sample.get,
        genAdaPool.sample.get,
        genAdaPool.sample.get,
        genAdaPool.sample.get
      )

      val (resolver, expectedRates) = genState(pools, adaRate)

      val res = resolver.resolve.unsafeRunSync().filter(_.asset =!= AdaAssetClass)

      val toCheck: Option[Boolean] = res
        .map { rate =>
          val pair: ResolvedRate = expectedRates.find(_ === rate).get
          rate.rate === pair.rate
        }
        .find(_ == false)

      toCheck must beNone
    }
    "preserves different(not necessary) rates for same tokens in different pools" in {
      val adaRate: BigDecimal = BigDecimal(0.5)
      val constantPool        = genAdaPool.sample.get
      val pools: List[Pool] = List(
        constantPool,
        constantPool.copy(id = genPoolId.sample.get),
        genAdaPool.sample.get,
        genAdaPool.sample.get
      )

      val (resolver, expectedRates) = genState(pools, adaRate)

      val res = resolver.resolve.unsafeRunSync().filter(_.asset =!= AdaAssetClass)

      val toCheck: Option[Boolean] = res
        .map { rate =>
          val pair: ResolvedRate = expectedRates.find(_ === rate).get
          rate.rate === pair.rate
        }
        .find(_ == false)

      res.length mustEqual 4
      toCheck must beNone
    }
    "resolve rates for tokens without ada pair via ada to token pairs" in {
      val adaRate: BigDecimal = BigDecimal(0.5)
      val adaPool             = genAdaPool.sample.get
      val nonAdaPool          = genPool.sample.get.copy(x = adaPool.x)

      val pools: List[Pool] = List(
        adaPool,
        nonAdaPool
      )

      val (resolver, expectedRates) = genState(pools, adaRate)

      val res = resolver.resolve.unsafeRunSync().filter(_.asset =!= AdaAssetClass)

      val toCheck: Option[Boolean] = res
        .map { rate =>
          val pair: ResolvedRate = expectedRates.find(_ === rate).get
          rate.rate === pair.rate
        }
        .find(_ == false)

      res.length mustEqual 2
      toCheck must beNone
    }

    "non ada pair should be resolved via ada to token pair with the biggest tvl in pool" in {
      val adaRate: BigDecimal = BigDecimal(0.5)
      val adaPool             = genAdaPool.sample.get
      val adaPool2            = genAdaPool.sample.get.copy(x = adaPool.x)
      val nonAdaPool          = genPool.sample.get.copy(x = adaPool.x)

      val pools: List[Pool] = List(
        adaPool,
        adaPool2,
        nonAdaPool
      )

      val (resolver, expectedRates) = genState(pools, adaRate)

      val res = resolver.resolve.unsafeRunSync().filter(_.asset =!= AdaAssetClass)

      val toCheck: Option[Boolean] = res
        .map { rate =>
          val pair: ResolvedRate = expectedRates.find(_ === rate).get
          rate.rate === pair.rate
        }
        .find(_ == false)

      res.length mustEqual 3
      toCheck must beNone
    }
  }

  private def genState(pools: List[Pool], adaRate: BigDecimal): (ResolverService[SyncIO], List[ResolvedRate]) = {
    val assets: List[AssetClass] =
      pools
        .flatMap(p => p.x.asset :: p.y.asset :: Nil)
        .filter(_ =!= AdaAssetClass)
    val assetsWithDecimals: List[(AssetClass, Int)] =
      assets.map(_ -> Gen.choose(6, 9).sample.get)
    implicit val poolsRepo: PoolsService[SyncIO]      = PoolsServiceMock.create[SyncIO](pools)
    implicit val metaService: MetadataService[SyncIO] = MetadataServiceMock.create[SyncIO](assetsWithDecimals)
    implicit val network: Network[SyncIO]             = NetworkMock.create[SyncIO](adaRate)

    val resolver: ResolverService[SyncIO] = ResolverService.create[SyncIO, SyncIO](conf).unsafeRunSync()

    val expectedRates: List[ResolvedRate] =
      pools
        .map { pool =>
          if (pool.contains(AdaAssetClass)) {
            val decimals = assetsWithDecimals.find(_._1 == pool.x.asset).get._2
            val rate     = pool.y.amount.dropPenny(AdaDecimal) / pool.x.amount.dropPenny(decimals)
            ResolvedRate(pool.x.asset, rate * adaRate, decimals, pool.id)
          } else {
            val decimalsX = assetsWithDecimals.find(_._1 == pool.x.asset).get._2
            val decimalsY = assetsWithDecimals.find(_._1 == pool.y.asset).get._2
            val adaToTokenPool = pools.filter(p => p.contains(AdaAssetClass) && p.contains(pool.x.asset)).maxBy { p =>
              p.x.amount.dropPenny(decimalsX) + p.y.amount.dropPenny(decimalsY)
            }
            val rateX     = adaToTokenPool.y.amount.dropPenny(AdaDecimal) / adaToTokenPool.x.amount.dropPenny(decimalsX)
            val resolvedX = ResolvedRate(pool.x.asset, rateX * adaRate, decimalsX, pool.id)
            ResolvedRate(pool, resolvedX, decimalsX, decimalsY)
          }
        }
        .map(_.setScale)

    resolver -> expectedRates
  }
}
