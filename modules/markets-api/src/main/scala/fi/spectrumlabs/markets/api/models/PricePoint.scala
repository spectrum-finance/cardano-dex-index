package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

import scala.math.BigDecimal.RoundingMode

@derive(loggable, encoder, decoder)
case class PricePoint(
                       timestamp: Long,
                       price: BigDecimal
                     ) { self =>
  def setScale(scale: Int): PricePoint = self.copy(price = price.setScale(scale, RoundingMode.HALF_UP))
}

object PricePoint {

  val defaultScale = 6

  implicit val schema: Schema[PricePoint] = Schema.derived
}