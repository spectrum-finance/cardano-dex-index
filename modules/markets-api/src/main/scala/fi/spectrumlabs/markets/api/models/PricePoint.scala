package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(loggable, encoder, decoder)
case class PricePoint(
  timestamp: Long,
  price: RealPrice
)

object PricePoint {

  val defaultScale = 6

  implicit val schema: Schema[PricePoint] = Schema.derived
}
