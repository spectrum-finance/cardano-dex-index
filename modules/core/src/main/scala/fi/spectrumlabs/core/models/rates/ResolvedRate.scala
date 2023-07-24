package fi.spectrumlabs.core.models.rates

import cats.Eq
import fi.spectrumlabs.core.models.domain.{AssetClass, Pool, PoolId}
import cats.syntax.eq._
import cats.syntax.show._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.logging.derivation.loggable

import scala.math.BigDecimal.RoundingMode

@derive(loggable, encoder, decoder)
final case class ResolvedRate(asset: AssetClass, rate: BigDecimal, decimals: Int, poolId: PoolId) { self =>

  def contains(x: AssetClass, y: AssetClass): Boolean =
    asset === x || asset === y

  def find(x: AssetClass, y: AssetClass, pid: PoolId): Boolean =
    contains(x, y) && poolId === pid

  def cacheKey = s"${asset.show}"

  def setScale: ResolvedRate = self.copy(rate = rate.setScale(decimals, RoundingMode.HALF_UP))

  def setRate = this.copy(asset, BigDecimal(1), decimals, poolId)
}

object ResolvedRate {

  implicit val eq: Eq[ResolvedRate] =
    (x: ResolvedRate, y: ResolvedRate) => x.asset === y.asset && x.poolId === y.poolId

  def apply(pool: Pool, by: AssetClass, xDecimal: Int, yDecimal: Int): ResolvedRate = {
    val xAppliedDecimals = pool.x.amount.dropPenny(xDecimal)
    val yAppliedDecimals = pool.y.amount.dropPenny(yDecimal)
    if (pool.x.asset === by)
      ResolvedRate(
        pool.y.asset,
        xAppliedDecimals / yAppliedDecimals,
        yDecimal,
        pool.id
      )
    else
      ResolvedRate(
        pool.x.asset,
        yAppliedDecimals / xAppliedDecimals,
        xDecimal,
        pool.id
      )
  }

  def apply(pool: Pool, rate: ResolvedRate, xDecimal: Int, yDecimal: Int): ResolvedRate = {
    val xAppliedDecimals = pool.x.amount.dropPenny(xDecimal)
    val yAppliedDecimals = pool.y.amount.dropPenny(yDecimal)
    if (pool.x.asset === rate.asset)
      ResolvedRate(pool.y.asset, (xAppliedDecimals / yAppliedDecimals) * rate.rate, yDecimal, pool.id)
    else ResolvedRate(pool.x.asset, (yAppliedDecimals / xAppliedDecimals) * rate.rate, xDecimal, pool.id)
  }
}
