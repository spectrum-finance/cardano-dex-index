package fi.spectrumlabs.rates.resolver.services

import cats.{~>, Functor, Monad}
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.core.models.rates.ResolvedRate.AdaRate
import fi.spectrumlabs.core.{AdaAssetClass, DefaultDecimal}
import fi.spectrumlabs.rates.resolver.config.ResolverConfig
import fi.spectrumlabs.rates.resolver.gateways.Tokens
import fi.spectrumlabs.rates.resolver.repositories.Pools
import tofu.higherKind.{Mid, RepresentableK}
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._

trait ResolverService[F[_]] {
  def resolve: F[List[ResolvedRate]]
}

object ResolverService {

  implicit def representableK: RepresentableK[ResolverService] =
    tofu.higherKind.derived.genRepresentableK

  def create[I[_]: Functor, F[_]: Monad, D[_]](
    config: ResolverConfig,
    txr: D ~> F
  )(implicit
    pools: Pools[D],
    tokens: Tokens[F],
    logs: Logs[I, F]
  ): I[ResolverService[F]] =
    logs.forService[ResolverService[F]].map(implicit __ => new Tracing[F] attach new Live[F, D](config, txr))

  final private class Live[F[_]: Monad: Logging, D[_]](config: ResolverConfig, txr: D ~> F)(implicit
    pools: Pools[D],
    tokens: Tokens[F]
  ) extends ResolverService[F] {

    def resolve: F[List[ResolvedRate]] =
      for {
        snapshots <- txr(pools.snapshots(config.minLiquidityValue))
        info      <- tokens.get
      } yield AdaRate :: snapshots
        .filter(_.contains(AdaAssetClass))
        .groupBy(pool => (pool.x.asset, pool.y.asset))
        .map { case ((x, _), value) =>
          if (x == AdaAssetClass) value.maxBy(_.x.amount.value)
          else value.maxBy(_.y.amount.value)
        }
        .map { pool =>
          val xDecimal = info.find(_.asset == pool.x.asset).map(_.decimals).getOrElse(DefaultDecimal)
          val yDecimal = info.find(_.asset == pool.y.asset).map(_.decimals).getOrElse(DefaultDecimal)
          ResolvedRate(pool, AdaAssetClass, xDecimal, yDecimal)
        }
        .toList
  }

  final private class Tracing[F[_]: Monad: Logging] extends ResolverService[Mid[F, *]] {

    def resolve: Mid[F, List[ResolvedRate]] =
      for {
        _ <- trace"resolve()"
        r <- _
        _ <- trace"resolve() -> $r"
      } yield r
  }
}
