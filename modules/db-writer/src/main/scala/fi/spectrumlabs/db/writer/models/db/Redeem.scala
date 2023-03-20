package fi.spectrumlabs.db.writer.models.db

import cats.implicits.catsSyntaxOptionId
import cats.syntax.option.none
import fi.spectrumlabs.core.models.domain.AssetClass.syntax.AssetClassOps
import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.cardano.{Order, RedeemAction, SwapAction}
import fi.spectrumlabs.db.writer.models.orders.{ExFee, PublicKeyHash, StakePKH, TxOutRef}
import fi.spectrumlabs.db.writer.models.db.{Order => OrderDB}

final case class Redeem(
  poolId: Coin,
  coinX: Coin,
  coinY: Coin,
  coinLq: Coin,
  amountX: Amount,
  amountY: Amount,
  amountLq: Amount,
  exFee: ExFee,
  rewardPkh: PublicKeyHash,
  stakePkh: Option[StakePKH],
  orderInputId: TxOutRef
) extends OrderDB

object Redeem {

  implicit val streamingSchema: ToSchema[Order[RedeemAction], Option[Redeem]] = {
    case orderAction: Order[RedeemAction] =>
      Redeem(
        castFromCardano(orderAction.order.action.redeemPoolId.unCoin.unAssetClass).toCoin,
        Coin("test1"), //todo: fixMe
        Coin("test2"), //todo: fixMe
        castFromCardano(orderAction.order.action.redeemPoolId.unCoin.unAssetClass).toCoin,
        Amount(0), //todo: fixMe
        Amount(0), //todo: fixMe
        Amount(orderAction.order.action.redeemLqIn),
        ExFee(orderAction.order.action.redeemExFee.unExFee),
        PublicKeyHash(orderAction.order.action.redeemRewardPkh.getPubKeyHash),
        none, //todo: fixme
        castFromCardano(orderAction.fullTxOut.fullTxOutRef)
      ).some
  }
}
