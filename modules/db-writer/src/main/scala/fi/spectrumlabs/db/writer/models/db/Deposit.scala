package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.cardano.{DepositAction, DepositOrder, Order}
import fi.spectrumlabs.db.writer.models.orders.{ExFee, StakePKH, StakePubKeyHash, TxOutRef}
import cats.syntax.option._
import fi.spectrumlabs.core.models.domain.AssetClass.syntax._

final case class Deposit(
  poolId: Coin,
  coinX: Coin,
  coinY: Coin,
  coinLq: Coin,
  amountX: Amount,
  amountY: Amount,
  amountLq: Amount,
  exFee: ExFee,
  rewardPkh: String,
  stakePkh: Option[StakePKH],
  collateralAda: Long,
  orderInputId: TxOutRef,
  userOutputId: Option[TxOutRef],
  poolInputId: Option[TxOutRef],
  poolOutputId: Option[TxOutRef],
  timestamp: Option[Long]
) extends DBOrder

object Deposit {

  implicit val streamingSchema: ToSchema[Order, Option[Deposit]] = {
    case orderAction: DepositOrder =>
      Deposit(
        castFromCardano(orderAction.order.poolId.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositPair.firstElem.coin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositPair.secondElem.coin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositLq.unAssetClass).toCoin,
        Amount(orderAction.order.action.depositPair.firstElem.value),
        Amount(orderAction.order.action.depositPair.secondElem.value),
        Amount(0), //todo: fixme
        ExFee(orderAction.order.action.depositExFee.unExFee),
        orderAction.order.action.depositRewardPkh.getPubKeyHash,
        orderAction.order.action.depositRewardSPkh.map(spkh =>
          StakePKH(StakePubKeyHash(spkh.unStakePubKeyHash.getPubKeyHash))
        ),
        orderAction.order.action.adaCollateral,
        castFromCardano(orderAction.fullTxOut.fullTxOutRef),
        none,
        none,
        none,
        none
      ).some
    case _ => none
  }
}
