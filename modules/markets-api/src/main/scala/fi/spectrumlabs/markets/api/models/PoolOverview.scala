package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{Amount, AssetClass, Fee, PoolFee, PoolId}
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class PoolOverview(
  id: PoolId,
  x: AssetClass,
  y: AssetClass,
  xReserves: Amount,
  yReserves: Amount,
  statistics: Option[PoolInfo],
  fee: PoolFee
)

object PoolOverview {
  implicit def schema: Schema[PoolOverview] = Schema.derived
}
