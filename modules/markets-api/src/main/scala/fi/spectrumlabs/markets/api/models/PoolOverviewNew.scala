package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain._
import sttp.tapir.Schema
import tofu.logging.derivation.{loggable, show}

@derive(encoder, decoder, loggable)
final case class PoolOverviewNew(
  id: PoolId,
  lockedX: AssetAmount,
  lockedY: AssetAmount,
  lockedLQ: AssetAmount,
  poolFeeNum: BigDecimal,
  poolFeeDenum: BigDecimal
) {
  def toFront: PoolOverviewFront =
    PoolOverviewFront(
      id,
      AssetAmountFront(lockedX.asset, s"${lockedX.amount.value}"),
      AssetAmountFront(lockedY.asset, s"${lockedY.amount.value}"),
      AssetAmountFront(lockedLQ.asset, s"${lockedLQ.amount.value}"),
      poolFeeNum,
      poolFeeDenum
    )
}

object PoolOverviewNew {
  implicit def schema: Schema[PoolOverviewNew] = Schema.derived

}

@derive(encoder, decoder, loggable)
final case class PoolOverviewFront(
  id: PoolId,
  lockedX: AssetAmountFront,
  lockedY: AssetAmountFront,
  lockedLQ: AssetAmountFront,
  poolFeeNum: BigDecimal,
  poolFeeDenum: BigDecimal
)

object PoolOverviewFront {
  implicit def schema: Schema[PoolOverviewFront] = Schema.derived
}

@derive(decoder, encoder, loggable, show)
final case class AssetAmountFront(asset: AssetClass, amount: String)

object AssetAmountFront {
  implicit def schema: Schema[AssetAmountFront] = Schema.derived
}
