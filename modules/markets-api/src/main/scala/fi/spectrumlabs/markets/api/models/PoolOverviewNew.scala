package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{Apr, AssetAmount, AssetClass, PoolId}
import fi.spectrumlabs.markets.api.models.PoolOverviewNew.{AssetAmountFront, PoolFeeSnapshotFront, PoolOverviewFront}
import fi.spectrumlabs.markets.api.models.db.PoolFeeSnapshot
import sttp.tapir.Schema
import tofu.logging.derivation.{loggable, show}

@derive(encoder, decoder, loggable)
final case class PoolOverviewNew(
  id: PoolId,
  lockedX: AssetAmount,
  lockedY: AssetAmount,
  lockedLQ: AssetAmount,
  tvl: Option[BigDecimal],
  volume: Option[BigDecimal],
  fee: PoolFeeSnapshot,
  yearlyFeesPercent: Apr,
  poolFeeNum: BigDecimal,
  poolFeeDenum: BigDecimal
) {
  def toFront: PoolOverviewFront =
    PoolOverviewFront(
      id,
      AssetAmountFront(lockedX.asset, s"${lockedX.amount.value}"),
      AssetAmountFront(lockedY.asset, s"${lockedY.amount.value}"),
      AssetAmountFront(lockedLQ.asset, s"${lockedLQ.amount.value}"),
      tvl.map(_.toString()),
      volume.map(_.toString()),
      PoolFeeSnapshotFront(fee.x.toString(), fee.y.toString()),
      yearlyFeesPercent,
      poolFeeNum.toString(),
      poolFeeDenum.toString()
    )
}

object PoolOverviewNew {
  implicit def schema: Schema[PoolOverviewNew] = Schema.derived

  @derive(encoder, decoder, loggable)
  final case class PoolOverviewFront(
    id: PoolId,
    lockedX: AssetAmountFront,
    lockedY: AssetAmountFront,
    lockedLQ: AssetAmountFront,
    tvl: Option[String],
    volume: Option[String],
    fee: PoolFeeSnapshotFront,
    yearlyFeesPercent: Apr,
    poolFeeNum: String,
    poolFeeDenum: String
  )

  object PoolOverviewFront {
    implicit def schema: Schema[PoolOverviewFront] = Schema.derived
  }

  @derive(decoder, encoder, loggable, show)
  final case class AssetAmountFront(asset: AssetClass, amount: String)

  object AssetAmountFront {
    implicit def schema: Schema[AssetAmountFront] = Schema.derived
  }

  @derive(encoder, decoder, loggable)
  final case class PoolFeeSnapshotFront(x: String, y: String)

  object PoolFeeSnapshotFront {
    implicit def schema: Schema[PoolFeeSnapshotFront] = Schema.derived
  }

}
