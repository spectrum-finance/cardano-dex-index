package fi.spectrumlabs.markets.api.v1.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import sttp.tapir.Schema

@derive(encoder, decoder)
final case class CoinGeckoTicker(
  ticker_id: String,
  base_currency: String,
  target_currency: String,
  last_price: BigDecimal,
  liquidity_in_usd: BigDecimal,
  base_volume: BigDecimal,
  target_volume: BigDecimal,
  pool_id: String
)

object CoinGeckoTicker {

  implicit val poolStatsSchema: Schema[CoinGeckoTicker] = Schema
    .derived[CoinGeckoTicker]
    .modify(_.ticker_id)(_.description("Identifier of a ticker with delimiter to separate base/target"))
    .modify(_.base_currency)(_.description("Symbol of a the base cryptoasset"))
    .modify(_.target_currency)(_.description("Symbol of the target cryptoasset"))
    .modify(_.last_price)(_.description("Last transacted price of base currency based on given target currency"))
    .modify(_.base_volume)(_.description("24 hour trading volume for the pair"))
    .modify(_.target_volume)(_.description("24 hour trading volume for the pair"))
    .modify(_.pool_id)(_.description("Pool pair id"))
}
