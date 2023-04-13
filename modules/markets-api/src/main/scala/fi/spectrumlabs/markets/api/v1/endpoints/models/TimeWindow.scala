package fi.spectrumlabs.markets.api.v1.endpoints.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class TimeWindow(from: Option[Long], to: Option[Long])

object TimeWindow {

  val empty: TimeWindow = TimeWindow(None, None)

  implicit val schema: Schema[TimeWindow] = Schema.derived
}
