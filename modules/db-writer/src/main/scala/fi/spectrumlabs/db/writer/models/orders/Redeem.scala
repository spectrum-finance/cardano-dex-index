package fi.spectrumlabs.db.writer.models.orders

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.db.writer.classes.FromLedger
import fi.spectrumlabs.db.writer.models.streaming.OrderEvent
import io.circe.parser.parse
import tofu.logging.derivation.loggable

@derive(decoder, encoder, loggable)
final case class Redeem(
  redeemPoolId: PoolId,
  redeemLqIn: Amount,
  redeemLq: Coin,
  redeemExFee: ExFee,
  redeemRewardPkh: PublicKeyHash,
  redeemRewardSPkh: Option[PublicKeyHash]
)

object Redeem {

  implicit val fromLedger: FromLedger[OrderEvent, Option[Redeem]] =
    (in: OrderEvent) => parse(in.stringJson).toOption.flatMap(_.as[Redeem].toOption)
}