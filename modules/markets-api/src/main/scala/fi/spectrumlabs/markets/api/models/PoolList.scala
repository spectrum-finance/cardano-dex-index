package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.PoolId
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(loggable, decoder, encoder)
final case class PoolList(pools: List[PoolId], poolNum: Int)

object PoolList {

  implicit def schema: Schema[PoolList] = Schema.derived
}
