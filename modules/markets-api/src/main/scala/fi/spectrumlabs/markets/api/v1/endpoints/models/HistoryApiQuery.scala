package fi.spectrumlabs.markets.api.v1.endpoints.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class HistoryApiQuery(
  userPkhs: List[String]
)

object HistoryApiQuery {

  implicit val schema: Schema[HistoryApiQuery] = Schema.derived
}
