package fi.spectrumlabs.db.writer.models.db

import cats.implicits.catsSyntaxOptionId
import cats.syntax.option.none
import fi.spectrumlabs.core.models.domain.AssetClass.syntax.AssetClassOps
import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.{Key, ToSchema}
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.cardano.{
  CurrencySymbol,
  Order,
  RedeemAction,
  RedeemOrder,
  SwapAction,
  TokenName
}
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
  rewardPkh: String,
  stakePkh: Option[StakePKH],
  orderInputId: TxOutRef,
  userOutputId: Option[TxOutRef],
  poolInputId: Option[TxOutRef],
  poolOutputId: Option[TxOutRef],
  redeemOutputId: Option[TxOutRef],
  creationTimestamp: Long,
  executionTimestamp: Option[Long],
  orderStatus: OrderStatus,
  refundableFee: Long
) extends DBOrder

object Redeem {

  val RedeemRedisPrefix = "Redeem"

  implicit val key: Key[Redeem] = new Key[Redeem] {
    override def getKey(in: Redeem): String = RedeemRedisPrefix ++ in.rewardPkh
    def getExtendedKey(in: Redeem)          = getKey(in) ++ in.orderInputId.show
  }

  def streamingSchema(config: CardanoConfig): ToSchema[Order, Option[Redeem]] = {
    case orderAction: RedeemOrder =>
      Redeem(
        castFromCardano(orderAction.order.action.redeemPoolId.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemPoolX.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemPoolY.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.redeemLq.unCoin.unAssetClass).toCoin,
        none,
        none,
        Amount(orderAction.order.action.redeemLqIn),
        ExFee(orderAction.order.action.redeemExFee.unExFee),
        orderAction.order.action.redeemRewardPkh.getPubKeyHash,
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
        OrderStatus.Register,
        orderAction.fullTxOut.fullTxOutValue
          .get(CurrencySymbol.Ada, TokenName.Ada)
          .map(_.value)
          .getOrElse(0L) - orderAction.order.action.redeemExFee.unExFee
      ).some
    case _ => none
  }
}
