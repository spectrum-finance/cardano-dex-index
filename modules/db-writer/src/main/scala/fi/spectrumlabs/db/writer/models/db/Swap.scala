package fi.spectrumlabs.db.writer.models.db

import cats.syntax.option._
import fi.spectrumlabs.core.models.domain.AssetClass.syntax.AssetClassOps
import fi.spectrumlabs.core.models.domain.{Amount, Coin}
import fi.spectrumlabs.db.writer.classes.{Key, ToSchema}
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.cardano.{CurrencySymbol, DepositAction, Order, SwapAction, SwapOrder, TokenName}
import fi.spectrumlabs.db.writer.models.orders.{ExFee, StakePKH, StakePubKeyHash, TxOutRef}
import cats.syntax.show._
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
final case class Swap(
  base: Coin,
  quote: Coin,
  poolId: Coin,
  exFeePerTokenNum: Long,
  exFeePerTokenDen: Long,
  rewardPkh: String,
  stakePkh: Option[StakePKH],
  baseAmount: Amount,
  actualQuote: Option[Amount],
  minQuoteAmount: Amount,
  orderInputId: TxOutRef,
  userOutputId: Option[TxOutRef],
  poolInputId: Option[TxOutRef],
  poolOutputId: Option[TxOutRef],
  redeemOutputId: Option[TxOutRef],
  creationTimestamp: Long,
  executionTimestamp: Option[Long],
  orderStatus: OrderStatus,
  originalAdaAmount: Long,
  exFee: Option[Long]
) extends DBOrder

object Swap {

  val SwapRedisPrefix = "Swap"

  implicit val key: Key[Swap] = new Key[Swap] {
    override def getKey(in: Swap): String = SwapRedisPrefix ++ in.rewardPkh
    def getExtendedKey(in: Swap) = getKey(in) ++ in.orderInputId.show
  }

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
        none,
        Amount(orderAction.order.action.swapMinQuoteOut),
        castFromCardano(orderAction.fullTxOut.fullTxOutRef),
        none,
        none,
        none,
        none,
        orderAction.slotNo + config.startTimeInSeconds,
        none,
        OrderStatus.Register,
        orderAction.fullTxOut.fullTxOutValue
          .get(CurrencySymbol.Ada, TokenName.Ada)
          .map(_.value)
          .getOrElse(0L),
        none
      ).some
    case _ => none
  }
}
