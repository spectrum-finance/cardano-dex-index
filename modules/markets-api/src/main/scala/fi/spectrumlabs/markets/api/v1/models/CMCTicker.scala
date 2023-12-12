package fi.spectrumlabs.markets.api.v1.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class CMCTicker(
  base_id: String,
  base_name: String,
  base_symbol: String,
  quote_id: String,
  quote_name: String,
  quote_symbol: String,
  last_price: String,
  base_volume: String,
  quote_volume: String
)
