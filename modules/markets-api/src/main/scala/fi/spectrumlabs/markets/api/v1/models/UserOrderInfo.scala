package fi.spectrumlabs.markets.api.v1.models

import cats.Show
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{AssetAmount, AssetClass}
import fi.spectrumlabs.db.writer.models.db.{DBOrder, Deposit, OrderStatus, Redeem, Swap}
import sttp.tapir.Schema
import cats.syntax.option._
import fi.spectrumlabs.db.writer.models.orders.TxOutRef
import cats.syntax.show._

@derive(encoder, decoder)
sealed trait UserOrderInfo

object UserOrderInfo {

  implicit val schema: Schema[UserOrderInfo] = Schema.derived

  //todo: check values
  def fromDbOrder(dbOrder: DBOrder): Option[UserOrderInfo] = dbOrder match {
    case deposit: Deposit =>
      for {
        assetX  <- AssetClass.fromString(deposit.coinX.value)
        assetY  <- AssetClass.fromString(deposit.coinY.value)
        assetLq <- AssetClass.fromString(deposit.coinLq.value)
      } yield DepositOrderInfo(
        deposit.orderInputId.show,
        deposit.poolId.value,
        if (deposit.poolOutputId.isDefined) OrderStatus.Evaluated else OrderStatus.Register,
        AssetAmount(assetX, deposit.amountX),
        AssetAmount(assetY, deposit.amountY),
        deposit.amountX.value.toString.some,
        deposit.amountY.value.toString.some,
        AssetAmount(assetLq, deposit.amountLq).some,
        "ADA",
        deposit.exFee.unExFee,
        deposit.rewardPkh,
        deposit.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
        deposit.orderInputId.show,
        none,
        deposit.poolOutputId.map(_.txOutRefId.getTxId)
      )
    case redeem: Redeem =>
      for {
        assetX  <- AssetClass.fromString(redeem.coinX.value)
        assetY  <- AssetClass.fromString(redeem.coinY.value)
        assetLq <- AssetClass.fromString(redeem.coinLq.value)
      } yield RedeemOrderInfo(
        redeem.orderInputId.show,
        redeem.poolId.value,
        if (redeem.poolOutputId.isDefined) OrderStatus.Evaluated else OrderStatus.Register,
        AssetAmount(assetLq, redeem.amountLq),
        AssetAmount(assetX, redeem.amountX).some,
        AssetAmount(assetY, redeem.amountY).some,
        "ADA",
        redeem.exFee.unExFee,
        redeem.rewardPkh.getPubKeyHash,
        redeem.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
        redeem.orderInputId.show,
        none,
        redeem.poolOutputId.map(_.txOutRefId.getTxId)
      )
    case swap: Swap =>
      for {
        assetX <- AssetClass.fromString(swap.base.value)
        assetY <- AssetClass.fromString(swap.quote.value)
      } yield SwapOrderInfo(
        swap.orderInputId.show,
        swap.poolId.value,
        if (swap.poolOutputId.isDefined) OrderStatus.Evaluated else OrderStatus.Register,
        AssetAmount(assetX, swap.baseAmount),
        AssetAmount(assetY, swap.minQuoteAmount),
        swap.actualQuote.value.toString.some,
        "ADA".some,
        0L.some, //todo: replace with normal value
        swap.rewardPkh,
        swap.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
        swap.orderInputId.show,
        none,
        swap.poolOutputId.map(_.txOutRefId.getTxId)
      )
    case _ => none
  }
}

final case class DepositOrderInfo(
  id: String,
  poolId: String,
  status: OrderStatus,
  inputX: AssetAmount,
  inputY: AssetAmount,
  actualX: Option[String],
  actualY: Option[String],
  outputLp: Option[AssetAmount],
  feeType: String,
  feeAmount: Long,
  userPkh: String,
  userSkh: Option[String],
  registerTx: String,
  refundTx: Option[String],
  evaluateTx: Option[String]
) extends UserOrderInfo

final case class SwapOrderInfo(
  id: String,
  poolId: String,
  status: OrderStatus,
  base: AssetAmount,
  minQuote: AssetAmount,
  quote: Option[String],
  feeType: Option[String],
  feeAmount: Option[Long],
  userPkh: String,
  userSkh: Option[String],
  registerTx: String,
  refundTx: Option[String],
  evaluateTx: Option[String]
) extends UserOrderInfo

final case class RedeemOrderInfo(
  id: String,
  poolId: String,
  status: OrderStatus,
  lp: AssetAmount,
  outX: Option[AssetAmount],
  outY: Option[AssetAmount],
  feeType: String,
  feeAmount: Long,
  userPkh: String,
  userSkh: Option[String],
  registerTx: String,
  refundTx: Option[String],
  evaluateTx: Option[String]
) extends UserOrderInfo
