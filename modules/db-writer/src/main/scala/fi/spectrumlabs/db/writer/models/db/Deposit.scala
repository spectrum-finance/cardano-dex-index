package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.cardano.{DepositAction, Order}
import fi.spectrumlabs.db.writer.models.db.{Order => OrderDB}
import fi.spectrumlabs.db.writer.models.orders.{ExFee, StakePKH, TxOutRef}
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
  orderInputId: TxOutRef
) extends OrderDB

object Deposit {

  implicit val streamingSchema: ToSchema[Order[DepositAction], Option[Deposit]] = {
    case orderAction: Order[DepositAction] =>
      Deposit(
        castFromCardano(orderAction.order.poolId.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositPair.firstElem.coin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositPair.secondElem.coin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositLq.unCoin.unAssetClass).toCoin,
        Amount(orderAction.order.action.depositPair.firstElem.value),
        Amount(orderAction.order.action.depositPair.secondElem.value),
        Amount(0), //todo: fixme
        ExFee(orderAction.order.action.depositExFee.unExFee),
        orderAction.order.action.depositRewardPkh.getPubKeyHash,
        none, //todo: fixme
        orderAction.order.action.adaCollateral,
        castFromCardano(orderAction.fullTxOut.fullTxOutRef)
      ).some
  }
}
