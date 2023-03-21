package fi.spectrumlabs.db.writer.models.db

import cats.implicits.catsSyntaxOptionId
import cats.syntax.option.none
import fi.spectrumlabs.core.models.domain.AssetClass.syntax.AssetClassOps
import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.cardano.{Order, RedeemAction, RedeemOrder, SwapAction}
import fi.spectrumlabs.db.writer.models.orders.{ExFee, PublicKeyHash, StakePKH, StakePubKeyHash, TxOutRef}

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
  orderInputId: TxOutRef,
  userOutputId: Option[TxOutRef],
  poolInputId: Option[TxOutRef],
  poolOutputId: Option[TxOutRef],
  timestamp: Option[Long]
)

object Redeem {

  implicit val streamingSchema: ToSchema[Order, Option[Redeem]] = {
    case orderAction: RedeemOrder =>
      Redeem(
        castFromCardano(orderAction.order.action.redeemPoolId.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemPoolX.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemPoolY.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemLq.unCoin.unAssetClass).toCoin,
        Amount(0), //todo: fixMe
        Amount(0), //todo: fixMe
        Amount(orderAction.order.action.redeemLqIn),
        ExFee(orderAction.order.action.redeemExFee.unExFee),
        PublicKeyHash(orderAction.order.action.redeemRewardPkh.getPubKeyHash),
        orderAction.order.action.redeemRewardSPkh.map(spkh =>
          StakePKH(StakePubKeyHash(spkh.unStakePubKeyHash.getPubKeyHash))
        ), //todo: fixme
        castFromCardano(orderAction.fullTxOut.fullTxOutRef),
        none,
        none,
        none,
        none
      ).some
    case _ => none
  }
}