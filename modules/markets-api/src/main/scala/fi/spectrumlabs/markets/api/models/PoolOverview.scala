package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{Amount, Apr, AssetAmount, AssetClass, Fee, PoolFee, PoolId}
import fi.spectrumlabs.markets.api.models.db.PoolFeeSnapshot
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class PoolOverview(
  id: PoolId,
  lockedX: AssetAmount,
  lockedY: AssetAmount,
  tvl: Option[BigDecimal],
  volume: Option[BigDecimal],
  fee: PoolFeeSnapshot,
  yearlyFeesPercent: Apr
)

object PoolOverview {
  implicit def schema: Schema[PoolOverview] = Schema.derived
}
