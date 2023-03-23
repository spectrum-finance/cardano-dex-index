package fi.spectrumlabs.db.writer.sql

import doobie.{LogHandler, Update}
import doobie.util.query.Query0
import fi.spectrumlabs.db.writer.models.db.{Deposit, Redeem, Swap}
import doobie.implicits._
import doobie.util.update.Update0

//todo: current version is only for testing
object OrdersSql {

  def getDepositOrderSQL(txOutRef: String): Query0[Deposit] =
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
          |     timestamp from deposit where order_input_id = $txOutRef""".stripMargin.queryWithLogHandler[Deposit](LogHandler.jdkLogHandler)

  def getSwapOrderSQL(txOutRef: String): Query0[Swap] =
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
      .queryWithLogHandler[Swap](LogHandler.jdkLogHandler)

  def getRedeemOrderSQL(txOutRef: String): Query0[Redeem] =
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
          |      timestamp from redeem where order_input_id = $txOutRef""".stripMargin.queryWithLogHandler[Redeem](LogHandler.jdkLogHandler)

  def updateExecutedSwapOrderSQL(
    actualQuote: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  ): Update0 =
    Update[(Long, String, String, String, Long, String)](
      s"""
         |update swap
         |set actual_quote=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, timestamp=?
         |where order_input_id=?""".stripMargin
    ).toUpdate0((actualQuote, userOutputId, poolInputId, poolOutputId, timestamp, orderInputId))

  def deleteExecutedSwapOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update swap
         |set actual_quote=null, user_output_id=null, pool_input_id=null, pool_output_Id=null, timestamp=null
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)

  def updateExecutedDepositOrderSQL(
    amountLq: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  ): Update0 =
    Update[(Long, String, String, String, Long, String)](
      s"""
         |update deposit
         |set amount_lq=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, timestamp=?
         |where order_input_id=?""".stripMargin
    ).toUpdate0((amountLq, userOutputId, poolInputId, poolOutputId, timestamp, orderInputId))

  def deleteExecutedDepositOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update deposit
         |set user_output_id=null, pool_input_Id=null, pool_output_Id=null, timestamp=null
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)

  def updateExecutedRedeemOrderSQL(
    amountX: Long,
    amountY: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  ): Update0 =
    Update[(Long, Long, String, String, String, Long, String)](
      s"""
         |update redeem
         |set amount_x=?, amount_y=?, user_output_id=?, pool_input_id=?, pool_output_Id=?, timestamp=?
         |where order_input_id=?""".stripMargin
    ).toUpdate0((amountX, amountY, userOutputId, poolInputId, poolOutputId, timestamp, orderInputId))

  def deleteExecutedRedeemOrderSQL(txOutRef: String): Update0 =
    Update[String](
      s"""
         |update redeem
         |set user_output_id=null, pool_input_Id=null, pool_output_Id=null, timestamp=null
         |where order_input_id=?""".stripMargin
    ).toUpdate0(txOutRef)
}
