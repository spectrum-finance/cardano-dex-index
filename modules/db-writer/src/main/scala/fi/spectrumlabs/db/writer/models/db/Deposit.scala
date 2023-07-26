package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.{Key, ToSchema}
import fi.spectrumlabs.db.writer.models.cardano.{DepositAction, DepositOrder, Order}
import fi.spectrumlabs.db.writer.models.orders.{ExFee, StakePKH, StakePubKeyHash, TxOutRef}
import cats.syntax.option._
import fi.spectrumlabs.core.models.domain.AssetClass.syntax._
import fi.spectrumlabs.db.writer.config.CardanoConfig
import cats.syntax.show._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
final case class Deposit(
  poolId: Coin,
  coinX: Coin,
  coinY: Coin,
  coinLq: Coin,
  amountX: Amount,
  amountY: Amount,
  amountLq: Option[Amount],
  exFee: ExFee,
  rewardPkh: String,
  stakePkh: Option[StakePKH],
  collateralAda: Long,
  orderInputId: TxOutRef,
  userOutputId: Option[TxOutRef],
  poolInputId: Option[TxOutRef],
  poolOutputId: Option[TxOutRef],
  redeemOutputId: Option[TxOutRef],
  creationTimestamp: Long,
  executionTimestamp: Option[Long],
  orderStatus: OrderStatus
) extends DBOrder

object Deposit {

  val DepositRedisPrefix = "Deposit"

  implicit val key: Key[Deposit] = new Key[Deposit] {
    override def getKey(in: Deposit): String = DepositRedisPrefix ++ in.rewardPkh

    def getExtendedKey(in: Deposit) = getKey(in) ++ in.orderInputId.show
  }

  def streamingSchema(config: CardanoConfig): ToSchema[Order, Option[Deposit]] = {
    case orderAction: DepositOrder
        if config.supportedPools.contains(castFromCardano(orderAction.order.poolId.unCoin.unAssetClass).toCoin.value) =>
      Deposit(
        castFromCardano(orderAction.order.poolId.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositPair.firstElem.coin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositPair.secondElem.coin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.depositLq.unAssetClass).toCoin,
        Amount(orderAction.order.action.depositPair.firstElem.value),
        Amount(orderAction.order.action.depositPair.secondElem.value),
        none, //todo: make optional in schema
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
        none,
        config.startTimeInSeconds + orderAction.slotNo,
        none,
        OrderStatus.Register
      ).some
    case _ => none
  }
}
