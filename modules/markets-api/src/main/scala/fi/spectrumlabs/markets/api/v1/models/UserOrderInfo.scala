package fi.spectrumlabs.markets.api.v1.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.{AssetAmount, AssetClass}
import fi.spectrumlabs.db.writer.models.db.{AnyOrderDB, DBOrder, Deposit, OrderStatus, OrderTypeDB, Redeem, Swap}
import sttp.tapir.Schema
import cats.syntax.option._
import cats.syntax.show._
import fi.spectrumlabs.core.AdaAssetClass
import fi.spectrumlabs.markets.api.v1.endpoints.models.TxData

import scala.concurrent.duration._

@derive(encoder, decoder)
sealed trait UserOrderInfo {
  val id: String
  val registerTx: TxData
}

object UserOrderInfo {

  implicit val schema: Schema[UserOrderInfo] = Schema.derived

  val FiveMin = 1.minutes.toSeconds

  val NeedRefundTime = 1.minutes.toSeconds

  def fromAnyOrderDB(order: AnyOrderDB, curTime: Long): Option[UserOrderInfo] =
    order.orderType match {
      case OrderTypeDB.Swap =>
        for {
          base  <- order.swapBase
          quote <- order.swapQuote
          needRefund = curTime - order.creationTimestamp > NeedRefundTime
          status =
            if (order.poolOutputId.isDefined) OrderStatus.Evaluated
            else if (order.redeemOutputId.isDefined) OrderStatus.Refunded
            else if (needRefund) OrderStatus.NeedRefund
            else OrderStatus.Pending
          fee = order.exFee
        } yield SwapOrderInfo(
          order.orderInputId.show,
          order.poolId.value,
          status,
          base,
          quote,
          order.actualQuote.map(_.value.toString),
          "ADA".some,
          fee.map(_.value).getOrElse(0L).some,
          order.rewardPkh,
          order.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
          TxData(order.orderInputId.txOutRefId.getTxId, order.creationTimestamp),
          for {
            id <- order.redeemOutputId.map(_.txOutRefId.getTxId)
            ts <- order.executionTimestamp
          } yield TxData(id, ts),
          for {
            id <- order.poolOutputId.map(_.txOutRefId.getTxId)
            ts <- order.executionTimestamp
          } yield TxData(id, ts)
        )
      case OrderTypeDB.Redeem =>
        for {
          lq  <- order.redeemLq
          fee <- order.exFee
          needRefund = curTime - order.creationTimestamp > NeedRefundTime
          status =
            if (order.poolOutputId.isDefined) OrderStatus.Evaluated
            else if (order.redeemOutputId.isDefined) OrderStatus.Refunded
            else if (needRefund) OrderStatus.NeedRefund
            else OrderStatus.Pending
        } yield RedeemOrderInfo(
          order.orderInputId.show,
          order.poolId.value,
          status,
          lq,
          order.redeemX,
          order.redeemY,
          "ADA",
          fee.value,
          order.rewardPkh,
          order.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
          TxData(order.orderInputId.txOutRefId.getTxId, order.creationTimestamp),
          for {
            id <- order.redeemOutputId.map(_.txOutRefId.getTxId)
            ts <- order.executionTimestamp
          } yield TxData(id, ts),
          for {
            id <- order.poolOutputId.map(_.txOutRefId.getTxId)
            ts <- order.executionTimestamp
          } yield TxData(id, ts)
        )
      case OrderTypeDB.Deposit =>
        for {
          x   <- order.depositX
          y   <- order.depositY
          fee <- order.exFee
          needRefund = curTime - order.creationTimestamp > NeedRefundTime
          status =
            if (order.poolOutputId.isDefined) OrderStatus.Evaluated
            else if (order.redeemOutputId.isDefined) OrderStatus.Refunded
            else if (needRefund) OrderStatus.NeedRefund
            else OrderStatus.Pending
        } yield DepositOrderInfo(
          order.orderInputId.show,
          order.poolId.value,
          status,
          if (x.asset == AdaAssetClass) x else y, //todo drop when tracker will be fixed
          if (y.asset == AdaAssetClass) x else y,
          if (x.asset == AdaAssetClass) x.amount.value.toString.some
          else y.amount.value.toString.some,
          if (y.asset == AdaAssetClass) x.amount.value.toString.some
          else y.amount.value.toString.some,
          order.depositLq,
          "ADA",
          fee.value,
          order.rewardPkh,
          order.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
          TxData(order.orderInputId.txOutRefId.getTxId, order.creationTimestamp),
          for {
            id <- order.redeemOutputId.map(_.txOutRefId.getTxId)
            ts <- order.executionTimestamp
          } yield TxData(id, ts),
          for {
            id <- order.poolOutputId.map(_.txOutRefId.getTxId)
            ts <- order.executionTimestamp
          } yield TxData(id, ts)
        )
    }

  //todo: check values
  def fromDbOrder(dbOrder: DBOrder, curTime: Long, refundOnly: Boolean, isMempool: Boolean): Option[UserOrderInfo] =
    dbOrder match {
      case deposit: Deposit =>
        for {
          assetX  <- AssetClass.fromString(deposit.coinX.value)
          assetY  <- AssetClass.fromString(deposit.coinY.value)
          assetLq <- AssetClass.fromString(deposit.coinLq.value)
          needRefund = curTime - deposit.creationTimestamp > FiveMin
          status =
            if (refundOnly) OrderStatus.NeedRefund
            else if (isMempool) OrderStatus.Pending
            else if (deposit.poolOutputId.isDefined) OrderStatus.Evaluated
            else if (deposit.redeemOutputId.isDefined) OrderStatus.Refunded
            else if (needRefund) OrderStatus.NeedRefund
            else OrderStatus.Pending
        } yield DepositOrderInfo(
          deposit.orderInputId.show,
          deposit.poolId.value,
          status,
          AssetAmount(assetX, deposit.amountX),
          AssetAmount(assetY, deposit.amountY),
          deposit.amountX.value.toString.some,
          deposit.amountY.value.toString.some,
          deposit.amountLq.map(x => AssetAmount(assetLq, x)),
          "ADA",
          deposit.exFee.unExFee,
          deposit.rewardPkh,
          deposit.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
          TxData(deposit.orderInputId.txOutRefId.getTxId, deposit.creationTimestamp),
          for {
            id <- deposit.redeemOutputId.map(_.txOutRefId.getTxId)
            ts <- deposit.executionTimestamp
          } yield TxData(id, ts),
          for {
            id <- deposit.poolOutputId.map(_.txOutRefId.getTxId)
            ts <- deposit.executionTimestamp
          } yield TxData(id, ts)
        )
      case redeem: Redeem =>
        for {
          assetX  <- AssetClass.fromString(redeem.coinX.value)
          assetY  <- AssetClass.fromString(redeem.coinY.value)
          assetLq <- AssetClass.fromString(redeem.coinLq.value)
          needRefund = curTime - redeem.creationTimestamp > FiveMin
          status =
            if (refundOnly) OrderStatus.NeedRefund
            else if (isMempool) OrderStatus.Pending
            else if (redeem.poolOutputId.isDefined) OrderStatus.Evaluated
            else if (redeem.redeemOutputId.isDefined) OrderStatus.Refunded
            else if (needRefund) OrderStatus.NeedRefund
            else OrderStatus.Pending
        } yield RedeemOrderInfo(
          redeem.orderInputId.show,
          redeem.poolId.value,
          status,
          AssetAmount(assetLq, redeem.amountLq),
          redeem.amountX.map(x => AssetAmount(assetX, x)),
          redeem.amountY.map(y => AssetAmount(assetY, y)),
          "ADA",
          redeem.exFee.unExFee,
          redeem.rewardPkh,
          redeem.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
          TxData(redeem.orderInputId.txOutRefId.getTxId, redeem.creationTimestamp),
          for {
            id <- redeem.redeemOutputId.map(_.txOutRefId.getTxId)
            ts <- redeem.executionTimestamp
          } yield TxData(id, ts),
          for {
            id <- redeem.poolOutputId.map(_.txOutRefId.getTxId)
            ts <- redeem.executionTimestamp
          } yield TxData(id, ts)
        )
      case swap: Swap =>
        for {
          assetX <- AssetClass.fromString(swap.base.value)
          assetY <- AssetClass.fromString(swap.quote.value)
          needRefund = curTime - swap.creationTimestamp > FiveMin
          status =
            if (refundOnly) OrderStatus.NeedRefund
            else if (isMempool) OrderStatus.Pending
            else if (swap.poolOutputId.isDefined) OrderStatus.Evaluated
            else if (swap.redeemOutputId.isDefined) OrderStatus.Refunded
            else if (needRefund) OrderStatus.NeedRefund
            else OrderStatus.Pending
        } yield SwapOrderInfo(
          swap.orderInputId.show,
          swap.poolId.value,
          status,
          AssetAmount(assetX, swap.baseAmount),
          AssetAmount(assetY, swap.minQuoteAmount),
          swap.actualQuote.map(_.value.toString),
          "ADA".some,
          swap.exFee.getOrElse(0L).some,
          swap.rewardPkh,
          swap.stakePkh.map(_.unStakePubKeyHash.getPubKeyHash),
          TxData(swap.orderInputId.txOutRefId.getTxId, swap.creationTimestamp),
          for {
            id <- swap.redeemOutputId.map(_.txOutRefId.getTxId)
            ts <- swap.executionTimestamp
          } yield TxData(id, ts),
          for {
            id <- swap.poolOutputId.map(_.txOutRefId.getTxId)
            ts <- swap.executionTimestamp
          } yield TxData(id, ts)
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
  registerTx: TxData,
  refundTx: Option[TxData],
  evaluateTx: Option[TxData]
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
  registerTx: TxData,
  refundTx: Option[TxData],
  evaluateTx: Option[TxData]
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
  registerTx: TxData,
  refundTx: Option[TxData],
  evaluateTx: Option[TxData]
) extends UserOrderInfo
