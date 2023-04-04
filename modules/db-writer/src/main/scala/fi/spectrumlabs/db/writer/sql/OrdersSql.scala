package fi.spectrumlabs.db.writer.sql

import doobie.{LogHandler, Update}
import doobie.util.query.Query0
import fi.spectrumlabs.db.writer.models.db.{Deposit, Redeem, Swap}
import doobie.implicits._
import doobie.util.update.Update0
import fi.spectrumlabs.db.writer.classes.OrdersInfo.{
  ExecutedDepositOrderInfo,
  ExecutedRedeemOrderInfo,
  ExecutedSwapOrderInfo
}
import fi.spectrumlabs.db.writer.models.cardano.FullTxOutRef

//todo: current version is only for testing
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
          |     timestamp from deposit where order_input_id = $txOutRef""".stripMargin.query

  def getUserDepositOrdersSQL(userPkh: String): Query0[Deposit] =
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
         |     timestamp from deposit where reward_pkh = $userPkh""".stripMargin.query

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
          |  timestamp from swap where order_input_id = $txOutRef""".stripMargin
      .query[Swap]

  def getUserSwapOrdersSQL(userPkh: String): Query0[Swap] =
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
         |  timestamp from swap where reward_pkh = $userPkh""".stripMargin
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
          |      timestamp from redeem where order_input_id = $txOutRef""".stripMargin.query

  def getUserRedeemOrdersSQL(userPkh: String): Query0[Redeem] =
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
         |      timestamp from redeem where reward_pkh = $userPkh""".stripMargin.query

  def updateExecutedSwapOrderSQL(swapOrderInfo: ExecutedSwapOrderInfo): Update0 =
    Update[ExecutedSwapOrderInfo](
      s"""
         |update swap
         |set actual_quote=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, timestamp=?
         |where order_input_id=?""".stripMargin
    ).toUpdate0(swapOrderInfo)

  def deleteExecutedSwapOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update swap
         |set actual_quote=null, user_output_id=null, pool_input_id=null, pool_output_Id=null, timestamp=null
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)

  def updateExecutedDepositOrderSQL(depositOrderInfo: ExecutedDepositOrderInfo): Update0 =
    Update[ExecutedDepositOrderInfo](
      s"""
         |update deposit
         |set amount_lq=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, timestamp=?
         |where order_input_id=?""".stripMargin
    ).toUpdate0(depositOrderInfo)

  def deleteExecutedDepositOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update deposit
         |set user_output_id=null, pool_input_Id=null, pool_output_Id=null, timestamp=null
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)

  def updateExecutedRedeemOrderSQL(redeemOrderInfo: ExecutedRedeemOrderInfo): Update0 =
    Update[ExecutedRedeemOrderInfo](
      s"""
         |update redeem
         |set amount_x=?, amount_y=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, timestamp=?
         |where order_input_id=?""".stripMargin
    ).toUpdate0(redeemOrderInfo)

  def deleteExecutedRedeemOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update redeem
         |set user_output_id=null, pool_input_Id=null, pool_output_Id=null, timestamp=null
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)
}
