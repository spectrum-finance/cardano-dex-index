package fi.spectrumlabs.markets.api.models.db

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class PoolFeeSnapshot(x: BigDecimal, y: BigDecimal)

object PoolFeeSnapshot {
  implicit def schema: Schema[PoolFeeSnapshot] = Schema.derived
}
