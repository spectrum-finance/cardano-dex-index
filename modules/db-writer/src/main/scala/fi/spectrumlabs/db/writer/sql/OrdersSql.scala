package fi.spectrumlabs.db.writer.sql

import doobie.{LogHandler, Update}
import doobie.util.query.Query0
import fi.spectrumlabs.db.writer.models.db.{Deposit, Redeem, Swap}
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update0
import fi.spectrumlabs.db.writer.classes.OrdersInfo.{ExecutedDepositOrderInfo, ExecutedRedeemOrderInfo, ExecutedSwapOrderInfo}
import fi.spectrumlabs.db.writer.models.cardano.FullTxOutRef
import fi.spectrumlabs.db.writer.models.orders.TxOutRef

object OrdersSql {

  def getDepositOrderSQL(txOutRef: FullTxOutRef): Query0[Deposit] =
    sql"""select
          |     pool_nft,
          |     coin_x,
          |     coin_y,
          |     coin_lq,
          |     amount_x,
          |     amount_y,
          |     amount_lq,
          |     ex_fee,
          |     reward_pkh,
          |     stake_pkh,
          |     collateral_ada,
          |     order_input_id,
          |     user_output_id,
          |     pool_input_id,
          |     pool_output_id,
          |     redeem_output_Id,
          |     creation_timestamp,
          |     execution_timestamp,
          |     order_status from deposit where order_input_id = $txOutRef""".stripMargin.query

  def getUserDepositOrdersSQL(userPkh: String, refundOnly: Boolean,  pendingOnly: Boolean): Query0[Deposit] =
    sql"""select
         |     pool_nft,
         |     coin_x,
         |     coin_y,
         |     coin_lq,
         |     amount_x,
         |     amount_y,
         |     amount_lq,
         |     ex_fee,
         |     reward_pkh,
         |     stake_pkh,
         |     collateral_ada,
         |     order_input_id,
         |     user_output_id,
         |     pool_input_id,
         |     pool_output_id,
         |     redeem_output_Id,
         |     creation_timestamp,
         |     execution_timestamp,
         |     order_status from deposit where reward_pkh = $userPkh ${refundOnlyF(refundOnly)} ${pendingOnlyF(pendingOnly)}""".stripMargin.query

  def getSwapOrderSQL(txOutRef: FullTxOutRef): Query0[Swap] =
    sql"""select base,
          |  quote,
          |  pool_nft,
          |  ex_fee_per_token_num,
          |  ex_fee_per_token_den,
          |  reward_pkh,
          |  stake_pkh,
          |  base_amount,
          |  actual_quote,
          |  min_quote_amount,
          |  order_input_id,
          |  user_output_id,
          |  pool_input_id,
          |  pool_output_id,
          |  redeem_output_Id,
          |  creation_timestamp,
          |  execution_timestamp,
          |  order_status from swap where order_input_id = $txOutRef""".stripMargin
      .query[Swap]

  def getUserSwapOrdersSQL(userPkh: String, refundOnly: Boolean,  pendingOnly: Boolean): Query0[Swap] =
    sql"""select base,
         |  quote,
         |  pool_nft,
         |  ex_fee_per_token_num,
         |  ex_fee_per_token_den,
         |  reward_pkh,
         |  stake_pkh,
         |  base_amount,
         |  actual_quote,
         |  min_quote_amount,
         |  order_input_id,
         |  user_output_id,
         |  pool_input_id,
         |  pool_output_id,
         |  redeem_output_Id,
         |  creation_timestamp,
         |  execution_timestamp,
         |  order_status from swap where reward_pkh = $userPkh ${refundOnlyF(refundOnly)} ${pendingOnlyF(pendingOnly)}""".stripMargin
      .query[Swap]

  def getRedeemOrderSQL(txOutRef: FullTxOutRef): Query0[Redeem] =
    sql"""select pool_nft,
          |      coin_x,
          |      coin_y,
          |      coin_lq,
          |      amount_x,
          |      amount_y,
          |      amount_lq,
          |      ex_fee,
          |      reward_pkh,
          |      stake_pkh,
          |      order_input_id,
          |      user_output_id,
          |      pool_input_id,
          |      pool_output_id,
          |      redeem_output_Id,
          |      creation_timestamp,
          |      execution_timestamp,
          |      order_status from redeem where order_input_id = $txOutRef""".stripMargin.query

  def getUserRedeemOrdersSQL(userPkh: String, refundOnly: Boolean,  pendingOnly: Boolean): Query0[Redeem] =
    sql"""select pool_nft,
         |      coin_x,
         |      coin_y,
         |      coin_lq,
         |      amount_x,
         |      amount_y,
         |      amount_lq,
         |      ex_fee,
         |      reward_pkh,
         |      stake_pkh,
         |      order_input_id,
         |      user_output_id,
         |      pool_input_id,
         |      pool_output_id,
         |      redeem_output_Id,
         |      creation_timestamp,
         |      execution_timestamp,
         |      order_status from redeem where reward_pkh = $userPkh ${refundOnlyF(refundOnly)} ${pendingOnlyF(pendingOnly)}""".stripMargin.query

  def pendingOnlyF(pendingOnly: Boolean): Fragment =
    if (pendingOnly) {
      fr"and execution_timestamp is null and creation_timestamp + 60 > extract(epoch from now())::INTEGER"
    } else Fragment.empty

  def refundOnlyF(refundOnly: Boolean): Fragment =
    if (refundOnly) {
      fr"and execution_timestamp is null and creation_timestamp + 60 < extract(epoch from now())::INTEGER"
    } else Fragment.empty

  def updateExecutedSwapOrderSQL(swapOrderInfo: ExecutedSwapOrderInfo): Update0 =
    Update[ExecutedSwapOrderInfo](
      s"""
         |update swap
         |set actual_quote=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, execution_timestamp=?, order_status='Evaluated'
         |where order_input_id=?""".stripMargin
    ).toUpdate0(swapOrderInfo)

  def refundSwapOrderSQL(refundOutputId: TxOutRef, refundTimestamp: Long, swapInputId: TxOutRef): Update0 =
    Update[(TxOutRef, Long, TxOutRef)](
      s"""
         |update swap
         |set redeem_output_id=?, execution_timestamp=?, order_status='Refunded'
         |where order_input_id=?""".stripMargin
    ).toUpdate0((refundOutputId, refundTimestamp, swapInputId))

  def deleteExecutedSwapOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update swap
         |set actual_quote=null, user_output_id=null, pool_input_id=null, pool_output_Id=null, execution_timestamp=null, order_status='Register'
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)

  def updateExecutedDepositOrderSQL(depositOrderInfo: ExecutedDepositOrderInfo): Update0 =
    Update[ExecutedDepositOrderInfo](
      s"""
         |update deposit
         |set amount_lq=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, execution_timestamp=?, order_status='Evaluated'
         |where order_input_id=?""".stripMargin
    ).toUpdate0(depositOrderInfo)

  def refundDepositOrderSQL(refundOutputId: TxOutRef, refundTimestamp: Long, depositInputId: TxOutRef): Update0 =
    Update[(TxOutRef, Long, TxOutRef)](
      s"""
         |update deposit
         |set redeem_output_id=?, execution_timestamp=?, order_status='Refunded'
         |where order_input_id=?""".stripMargin
    ).toUpdate0((refundOutputId, refundTimestamp, depositInputId))

  def deleteExecutedDepositOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update deposit
         |set user_output_id=null, pool_input_Id=null, pool_output_Id=null, execution_timestamp=null, order_status='Register'
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)

  def updateExecutedRedeemOrderSQL(redeemOrderInfo: ExecutedRedeemOrderInfo): Update0 =
    Update[ExecutedRedeemOrderInfo](
      s"""
         |update redeem
         |set amount_x=?, amount_y=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, execution_timestamp=?, order_status='Evaluated'
         |where order_input_id=?""".stripMargin
    ).toUpdate0(redeemOrderInfo)

  def refundRedeemOrderSQL(refundOutputId: TxOutRef, refundTimestamp: Long, redeemInputId: TxOutRef): Update0 =
    Update[(TxOutRef, Long, TxOutRef)](
      s"""
         |update redeem
         |set redeem_output_id=?, execution_timestamp=?, order_status='Refunded'
         |where order_input_id=?""".stripMargin
    ).toUpdate0((refundOutputId, refundTimestamp, redeemInputId))

  def deleteExecutedRedeemOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update redeem
         |set user_output_id=null, pool_input_Id=null, pool_output_Id=null, execution_timestamp=null, order_status='Register'
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)
}
