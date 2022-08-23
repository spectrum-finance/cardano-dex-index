package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(loggable, decoder, encoder)
case class PlatformStats(totalValueLocked: BigDecimal, volume: BigDecimal)

object PlatformStats {

  implicit def schema: Schema[PlatformStats] = Schema.derived
}
