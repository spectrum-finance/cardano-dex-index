package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{Amount, AssetAmount, AssetClass, Fee, PoolFee, PoolId}
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class PoolOverview(
  id: PoolId,
  lockedX: AssetAmount,
  lockedY: AssetAmount,
  tvl: Option[BigDecimal],
  volume: Option[BigDecimal],
  fee: PoolFee,
  APR: Int = 0 // 0 for testing purpose
)

object PoolOverview {
  implicit def schema: Schema[PoolOverview] = Schema.derived
}
