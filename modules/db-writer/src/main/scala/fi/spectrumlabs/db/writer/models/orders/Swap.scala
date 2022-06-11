package fi.spectrumlabs.db.writer.models.orders

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.db.writer.classes.FromLedger
import fi.spectrumlabs.db.writer.models.streaming.OrderEvent
import io.circe.parser.parse
import tofu.logging.derivation.loggable

@derive(decoder, encoder, loggable)
final case class Swap(
  swapPoolId: PoolId,
  swapBaseIn: Amount,
  swapMinQuoteOut: Amount,
  swapBase: Coin,
  swapQuote: Coin,
  swapExFee: ExFeePerToken,
  swapRewardPkh: PublicKeyHash,
  swapRewardSPkh: Option[PublicKeyHash]
)

object Swap {

  implicit val fromLedger: FromLedger[OrderEvent, Option[Swap]] =
    (in: OrderEvent) => parse(in.stringJson).toOption.flatMap(_.as[Swap].toOption)
}
