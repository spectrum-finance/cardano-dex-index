package fi.spectrumlabs.db.writer.models.orders

import cats.Show
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.db.writer.classes.FromLedger
import fi.spectrumlabs.db.writer.models.streaming.OrderEvent
import io.circe.parser.parse
import tofu.logging.Loggable

@derive(decoder, encoder)
final case class Deposit(
  depositPoolId: PoolId,
  depositPair: (AssetEntry, AssetEntry),
  depositExFee: ExFee,
  depositRewardPkh: PublicKeyHash,
  depositRewardSPkh: Option[PublicKeyHash],
  adaCollateral: Long
)

object Deposit {
  implicit val show: Show[Deposit]         = _.toString
  implicit val loggable: Loggable[Deposit] = Loggable.show

  implicit val fromLedger: FromLedger[OrderEvent, Option[Deposit]] =
    (in: OrderEvent) => parse(in.stringJson).toOption.flatMap(_.as[Deposit].toOption)
}
