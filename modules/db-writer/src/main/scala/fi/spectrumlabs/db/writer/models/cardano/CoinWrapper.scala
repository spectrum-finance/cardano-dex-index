package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

//todo: only for test. Remove it
@derive(encoder, decoder)
case class CoinWrapper(unCoin: Coin)
