package fi.spectrumlabs.markets.api.v1.endpoints.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.{loggable, show}

@derive(encoder, decoder, show, loggable)
final case class TxData(id: String, ts: Long)

object TxData {
  implicit val schema: Schema[TxData] = Schema.derived
}
