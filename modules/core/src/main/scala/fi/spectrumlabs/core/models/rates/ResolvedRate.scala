package fi.spectrumlabs.core.models.rates

import cats.Eq
import cats.syntax.eq._
import cats.syntax.show._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.AdaAssetClass
import fi.spectrumlabs.core.models.domain.{AssetClass, Pool}
import tofu.logging.derivation.loggable

import scala.math.BigDecimal.RoundingMode

@derive(loggable, encoder, decoder)
final case class ResolvedRate(asset: AssetClass, rate: BigDecimal, decimals: Int) {
  def cacheKey = s"${asset.show}"
}

object ResolvedRate {

  implicit val eq: Eq[ResolvedRate] =
    (x: ResolvedRate, y: ResolvedRate) => x.asset === y.asset

  def AdaRate: ResolvedRate = ResolvedRate(AdaAssetClass, 1, 6)

  def apply(pool: Pool, by: AssetClass, xDecimal: Int, yDecimal: Int): ResolvedRate = {
    val xAppliedDecimals = pool.x.amount.dropPenny(xDecimal)
    val yAppliedDecimals = pool.y.amount.dropPenny(yDecimal)
    if (pool.x.asset === by)
      ResolvedRate(
        pool.y.asset,
        (xAppliedDecimals / yAppliedDecimals).setScale(6, RoundingMode.HALF_UP),
        yDecimal
      )
    else
      ResolvedRate(
        pool.x.asset,
        (yAppliedDecimals / xAppliedDecimals).setScale(6, RoundingMode.HALF_UP),
        xDecimal
      )
  }
}
