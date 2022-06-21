package fi.spectrumlabs.rates.resolver.services

import cats.{Defer, Functor, Monad, Parallel, SemigroupK}
import fi.spectrumlabs.core.models.domain.AssetClass
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.rates.resolver.gateways.NetworkClient
import fi.spectrumlabs.rates.resolver.repositories.{PoolsRepo, RatesRepo}
import tofu.syntax.monadic._
import cats.syntax.eq._
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import tofu.syntax.streams.combineK._
import tofu.syntax.streams.compile._
import tofu.syntax.streams.emits._
import tofu.syntax.streams.evals._
import tofu.syntax.streams.pace._
import tofu.syntax.streams.temporal._
import tofu.optics.interop
import tofu.streams.{Evals, Pace}
import cats.syntax.parallel._
import fi.spectrumlabs.rates.resolver.config.ResolverConfig

trait RatesResolver[S[_]] {
  def run: S[Unit]
}

//todo min liquidity check (id db)

// 100 ada -> 1000 udst.
// 1 ada = 1000 / 100 = 10 udst  byX
// 1 udst = 100 / 1000 = 0.1 ada byY

object RatesResolver {

  final val AdaAssetClass: AssetClass = AssetClass("", "") //todo constant

  def create[
    I[_]: Functor,
    S[_]: Monad: Evals[*[_], F]: SemigroupK: Defer: Pace,
    F[_]: Monad: Parallel
  ](pools: PoolsService[F], repo: RatesRepo[F], networkClient: NetworkClient[F], config: ResolverConfig)(implicit
    logs: Logs[I, F]
  ): I[RatesResolver[S]] =
    logs.forService[RatesResolver[S]].map(implicit __ => new Impl[S, F](pools, repo, networkClient, config))

  final private class Impl[
    S[_]: Monad: Evals[*[_], F]: SemigroupK: Defer: Pace,
    F[_]: Monad: Logging: Parallel
  ](pools: PoolsService[F], repo: RatesRepo[F], networkClient: NetworkClient[F], config: ResolverConfig)
    extends RatesResolver[S] {

    def run: S[Unit] = {
      for {
        _     <- eval(info"Going to update rates.")
        rates <- eval(resolve)
        _     <- eval(rates.parTraverse(repo.put))
        _     <- eval(info"Rates was updated successfully.")
      } yield ()
    }.repeat.throttled(config.throttleRate)

    def resolve: F[List[ResolvedRate]] = networkClient
      .getPrice(AdaAssetClass)
      .flatMap { adaPrice =>
        pools
          .getAllLatest(config.minLiquidityValue)
          .flatTap(pools => trace"Pools from DB are: $pools.")
          .map { pools =>
            val resolvedByAda =
              pools
                .filter(_.contains(AdaAssetClass))
                .map(ResolvedRate(_, AdaAssetClass))

            val resolvedViaAda =
              pools
                .filterNot(_.contains(AdaAssetClass))
                .flatMap { pool =>
                  resolvedByAda
                    .find(_.contains(pool.x.asset, pool.y.asset))
                    .map(ResolvedRate(pool, _))
                }

            (resolvedByAda ::: resolvedViaAda).map(rate => rate.copy(rate.asset, rate.rate * adaPrice.rate))
          }
      }
      .flatTap(resolved => info"Resolved rates are: $resolved.")
  }
}
