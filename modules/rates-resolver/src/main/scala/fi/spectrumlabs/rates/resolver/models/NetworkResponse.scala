package fi.spectrumlabs.rates.resolver.models

import derevo.derive
import io.circe.{Decoder, HCursor}
import tofu.logging.derivation.loggable

@derive(loggable)
final case class NetworkResponse(name: String, symbol: String, price: BigDecimal)

object NetworkResponse {

  implicit val decoder: Decoder[NetworkResponse] = Decoder.instance { c: HCursor =>
    val data            = c.downField("data")
    val adaCurrencyInfo = data.downField("2010")
    val rateUds         = adaCurrencyInfo.downField("quote").downField("2781")
    for {
      name   <- adaCurrencyInfo.get[String]("name")
      symbol <- adaCurrencyInfo.get[String]("symbol")
      price  <- rateUds.get[BigDecimal]("price")
    } yield NetworkResponse(name, symbol, price)
  }
}
