package fi.spectrumlabs.core.models.rates

import fi.spectrumlabs.core.models.domain.{AssetClass, Pool}
import cats.syntax.eq._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.logging.derivation.loggable

@derive(loggable, encoder, decoder)
final case class ResolvedRate(asset: AssetClass, rate: BigDecimal) {
  def contains(x: AssetClass, y: AssetClass): Boolean =
    asset === x || asset == y
}

object ResolvedRate {

  def apply(pool: Pool, by: AssetClass): ResolvedRate =
    if (pool.x.asset === by)
      ResolvedRate(pool.y.asset, pool.priceByY)
    else ResolvedRate(pool.x.asset, pool.priceByX)

  def apply(pool: Pool, rate: ResolvedRate): ResolvedRate =
    if (pool.x.asset === rate.asset)
      ResolvedRate(pool.y.asset, pool.priceByY * rate.rate)
    else ResolvedRate(pool.x.asset, pool.priceByX * rate.rate)
}
