package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(loggable, decoder, encoder)
final case class PoolInfo(totalTvl: BigDecimal, totalVolume: BigDecimal)

object PoolInfo {

  implicit def schema: Schema[PoolInfo] = Schema.derived
}
