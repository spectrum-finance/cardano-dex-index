package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
case class CoinWrapper(unCoin: Coin)
