package fi.spectrumlabs.db.writer.models.db

import cats.implicits.catsSyntaxOptionId
import cats.syntax.option.none
import fi.spectrumlabs.core.models.domain.AssetClass.syntax.AssetClassOps
import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.{Key, ToSchema}
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.cardano.{Order, RedeemAction, RedeemOrder, SwapAction}
import fi.spectrumlabs.db.writer.models.orders.{ExFee, PublicKeyHash, StakePKH, StakePubKeyHash, TxOutRef}
import cats.syntax.show._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
final case class Redeem(
  poolId: Coin,
  coinX: Coin,
  coinY: Coin,
  coinLq: Coin,
  amountX: Option[Amount],
  amountY: Option[Amount],
  amountLq: Amount,
  exFee: ExFee,
  rewardPkh: PublicKeyHash,
  stakePkh: Option[StakePKH],
  orderInputId: TxOutRef,
  userOutputId: Option[TxOutRef],
  poolInputId: Option[TxOutRef],
  poolOutputId: Option[TxOutRef],
  redeemOutputId: Option[TxOutRef],
  creationTimestamp: Long,
  executionTimestamp: Option[Long],
  orderStatus: OrderStatus
) extends DBOrder

object Redeem {

  val RedeemRedisPrefix = "Redeem"

  implicit val key: Key[Redeem] = new Key[Redeem] {
    override def getKey(in: Redeem): String = RedeemRedisPrefix ++ in.rewardPkh.getPubKeyHash
  }

  def streamingSchema(config: CardanoConfig): ToSchema[Order, Option[Redeem]] = {
    case orderAction: RedeemOrder
        if config.supportedPools.contains(
          castFromCardano(orderAction.order.action.redeemPoolId.unCoin.unAssetClass).toCoin.value
        ) =>
      Redeem(
        castFromCardano(orderAction.order.action.redeemPoolId.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemPoolX.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemPoolY.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemLq.unCoin.unAssetClass).toCoin,
        none, //todo: make optional in schema
        none, //todo: make optional in schema
        Amount(orderAction.order.action.redeemLqIn),
        ExFee(orderAction.order.action.redeemExFee.unExFee),
        PublicKeyHash(orderAction.order.action.redeemRewardPkh.getPubKeyHash),
        orderAction.order.action.redeemRewardSPkh.map(spkh =>
          StakePKH(StakePubKeyHash(spkh.unStakePubKeyHash.getPubKeyHash))
        ),
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
