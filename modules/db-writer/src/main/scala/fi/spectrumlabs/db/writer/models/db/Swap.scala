package fi.spectrumlabs.db.writer.models.db

import cats.syntax.option._
import fi.spectrumlabs.core.models.domain.AssetClass.syntax.AssetClassOps
import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.cardano.{DepositAction, Order, SwapAction, SwapOrder}
import fi.spectrumlabs.db.writer.models.orders.{ExFee, StakePKH, StakePubKeyHash, TxOutRef}

final case class Swap(
  base: Coin,
  quote: Coin,
  poolId: Coin,
  exFeePerTokenNum: Long,
  exFeePerTokenDen: Long,
  rewardPkh: String,
  stakePkh: Option[StakePKH],
  baseAmount: Amount,
  actualQuote: Amount,
  minQuoteAmount: Amount,
  orderInputId: TxOutRef,
  userOutputId: Option[TxOutRef],
  poolInputId: Option[TxOutRef],
  poolOutputId: Option[TxOutRef],
  redeemOutputId: Option[TxOutRef],
  creationTimestamp: Long,
  executionTimestamp: Option[Long],
  orderStatus: OrderStatus
) extends DBOrder

object Swap {

  def streamingSchema(config: CardanoConfig): ToSchema[Order, Option[Swap]] = {
    case orderAction: SwapOrder
        if config.supportedPools.contains(
          castFromCardano(orderAction.order.action.swapPoolId.unCoin.unAssetClass).toCoin.value
        ) =>
      Swap(
        castFromCardano(orderAction.order.action.swapBase.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.swapQuote.unCoin.unAssetClass).toCoin,
        castFromCardano(orderAction.order.action.swapPoolId.unCoin.unAssetClass).toCoin,
        orderAction.order.action.swapExFee.exFeePerTokenNum,
        orderAction.order.action.swapExFee.exFeePerTokenDen,
        orderAction.order.action.swapRewardPkh.getPubKeyHash,
        orderAction.order.action.swapRewardSPkh.map(spkh =>
          StakePKH(StakePubKeyHash(spkh.unStakePubKeyHash.getPubKeyHash))
        ),
        Amount(orderAction.order.action.swapBaseIn),
        Amount(-1), //todo: make optional in schema
        Amount(orderAction.order.action.swapMinQuoteOut),
        castFromCardano(orderAction.fullTxOut.fullTxOutRef),
        none,
        none,
        none,
        none,
        orderAction.slotNo + config.startTimeInSeconds,
        none,
        OrderStatus.Register
      ).some
    case _ => none
  }
}
