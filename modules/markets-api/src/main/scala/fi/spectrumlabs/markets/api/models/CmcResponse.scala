package fi.spectrumlabs.markets.api.models

import derevo.derive
import fi.spectrumlabs.rates.resolver._
import io.circe.{Decoder, HCursor}
import tofu.logging.derivation.loggable

@derive(loggable)
final case class CmcResponse(name: String, symbol: String, price: BigDecimal)

object CmcResponse {

  implicit val decoder: Decoder[CmcResponse] = Decoder.instance { c: HCursor =>
    val data            = c.downField("data")
    val adaCurrencyInfo = data.downField(s"$AdaCMCId")
    val rateUds         = adaCurrencyInfo.downField("quote").downField(s"$UsdCMCId")
    for {
      name   <- adaCurrencyInfo.get[String]("name")
      symbol <- adaCurrencyInfo.get[String]("symbol")
      price  <- rateUds.get[BigDecimal]("price")
    } yield CmcResponse(name, symbol, price)
  }
}
