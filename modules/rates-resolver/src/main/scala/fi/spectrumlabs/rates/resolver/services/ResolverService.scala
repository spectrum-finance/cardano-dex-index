package fi.spectrumlabs.rates.resolver.services

import cats.{Functor, Monad}
import fi.spectrumlabs.core.{AdaAssetClass, DefaultDecimal}
import fi.spectrumlabs.core.models.domain.Pool
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.rates.resolver.config.ResolverConfig
import fi.spectrumlabs.rates.resolver.gateways.Network
import tofu.syntax.monadic._
import tofu.syntax.logging._
import cats.syntax.either._
import cats.syntax.eq._
import tofu.logging.{Logging, Logs}

import scala.math.BigDecimal.RoundingMode

trait ResolverService[F[_]] {
  def resolve: F[List[ResolvedRate]]
}

object ResolverService {

  def create[I[_]: Functor, F[_]: Monad](
    config: ResolverConfig
  )(implicit
    pools: PoolsService[F],
    network: Network[F],
    metadataService: MetadataService[F],
    logs: Logs[I, F]
  ): I[ResolverService[F]] =
    logs.forService[ResolverService[F]].map(implicit __ => new Impl[F](config))

  final private class Impl[F[_]: Monad: Logging](config: ResolverConfig)(implicit
    pools: PoolsService[F],
    network: Network[F],
    metadataService: MetadataService[F]
  ) extends ResolverService[F] {

    def resolve: F[List[ResolvedRate]] =
      network.getAdaPrice
        .flatMap { adaPrice =>
          (for {
            pools <- pools.getAllLatest(config.minLiquidityValue)
            info  <- metadataService.getTokensMeta(pools.flatMap(p => p.x.asset :: p.y.asset :: Nil))
          } yield (pools, info)).map { case (pools, info) =>
            val (poolsWithAda, poolsWithoutAda) = pools.partition(_.contains(AdaAssetClass))

            val resolvedByAda =
              poolsWithAda
                .map { r =>
                  val xDecimal = info.find(_.asset == r.x.asset).map(_.decimals).getOrElse(DefaultDecimal)
                  val yDecimal = info.find(_.asset == r.y.asset).map(_.decimals).getOrElse(DefaultDecimal)
                  ResolvedRate(r, AdaAssetClass, xDecimal, yDecimal)
                }

            val resolvedViaAda =
              poolsWithoutAda
                .flatMap { pool =>
                  Either
                    .catchNonFatal {
                      poolsWithAda
                        .filter(_.contains(pool.x.asset, pool.y.asset))
                        .maxBy { p =>
                          if (p.x.asset === AdaAssetClass)
                            resolvedByAda.find(_.asset === pool.y.asset).map(tvl(p, adaPrice, _))
                          else
                            resolvedByAda.find(_.asset === pool.x.asset).map(tvl(p, _, adaPrice))
                        }
                    }
                    .toOption
                    .flatMap { r =>
                      resolvedByAda.find(_.find(r.x.asset, r.y.asset, r.id))
                    }
                    .map { r =>
                      val xDecimal = info.find(_.asset == pool.x.asset).map(_.decimals).getOrElse(DefaultDecimal)
                      val yDecimal = info.find(_.asset == pool.y.asset).map(_.decimals).getOrElse(DefaultDecimal)
                      val a        = ResolvedRate(pool, r, xDecimal, yDecimal)
                      a
                    }
                }

            val s = adaPrice :: (resolvedByAda ::: resolvedViaAda)
              .map(rate => rate.copy(rate.asset, rate.rate * adaPrice.rate).setScale)
            s
          }
        }
        .flatTap(resolved => info"Resolved rates are: $resolved.")
  }

  def tvl(pool: Pool, rateX: ResolvedRate, rateY: ResolvedRate): BigDecimal = {
    val xTvl = pool.x.amount.dropPenny(rateX.decimals) * rateX.rate
    val yTvl = pool.y.amount.dropPenny(rateY.decimals) * rateY.rate
    (xTvl + yTvl).setScale(0, RoundingMode.HALF_UP)
  }
}
