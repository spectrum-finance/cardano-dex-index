package fi.spectrumlabs.rates.resolver.services

import cats.Monad
import fi.spectrumlabs.core.models.domain.AssetClass
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.rates.resolver.gateways.NetworkClient
import fi.spectrumlabs.rates.resolver.repositories.{PoolsRepo, RatesRepo}
import tofu.syntax.monadic._

trait RatesResolver[S[_]] {
  def run: S[Unit]
}

//todo min liquidity check (id db)
object RatesResolver {

  final val AdaAsset: AssetClass = AssetClass("", "") //todo constant

  final private class Impl[S[_], F[_]: Monad](
    pools: PoolsService[F],
    repo: RatesRepo[F],
    networkClient: NetworkClient[F]
  ) extends RatesResolver[S] {
    def run: S[Unit] = ???

    def resolve = networkClient.getPrice(AdaAsset) >>= { adaPrice =>
      pools.getAllLatest.map { pools =>
        val pairToAda = pools.filter { pool =>
          pool.x.asset == AdaAsset || pool.y.asset == AdaAsset
        }

        val toAdaPrice = pairToAda.map { pool =>
          if (pool.x.asset == AdaAsset) {
            ResolvedRate(pool.y.asset, pool.y.amount.value / pool.x.amount.value)
          } else
            ResolvedRate(pool.x.asset, pool.x.amount.value / pool.y.amount.value)
        }

        val pairNoAda = pools diff pairToAda

        val resolved2layer =
          pairNoAda.flatMap { pool =>
            val optPrice = toAdaPrice.find(rate => rate.asset == pool.x.asset || pool.y.asset == rate.asset)
            optPrice.map { rate =>
              if (pool.x.asset == rate.asset) {
                val xToRate = pool.x.amount.value / pool.y.amount.value * rate.rate
                ResolvedRate(pool.x.asset, xToRate)
              } else {
                val yToRate = pool.y.amount.value / pool.x.amount.value * rate.rate
                ResolvedRate(pool.y.asset, yToRate)
              }
            }
          }

        toAdaPrice.prependedAll(resolved2layer).map(r => r.copy(r.asset, r.rate * adaPrice.rate))
      }
    }
  }
}
