package fi.spectrumlabs.markets.api.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema
import tofu.logging.derivation.loggable
import scala.math.BigDecimal.RoundingMode

@derive(loggable, encoder, decoder)
case class RealPrice(value: BigDecimal) {
  def setScale(scale: Int): RealPrice = RealPrice(value.setScale(scale, RoundingMode.HALF_UP))
}

object RealPrice {

  val defaultScale = 6

  def calculate(
    baseAssetAmount: Long,
    baseAssetDecimals: Option[Int],
    quoteAssetAmount: Long,
    quoteAssetDecimals: Option[Int]
  ): RealPrice =
    RealPrice(
      BigDecimal(quoteAssetAmount) / baseAssetAmount * BigDecimal(10)
        .pow(
          baseAssetDecimals.getOrElse(0) - quoteAssetDecimals.getOrElse(0)
        )
    )

  implicit val realPriceSchema: Schema[RealPrice] = Schema.derived
}
