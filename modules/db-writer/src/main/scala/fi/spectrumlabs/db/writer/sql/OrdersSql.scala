package fi.spectrumlabs.db.writer.sql

import doobie.util.query.Query0
import fi.spectrumlabs.db.writer.models.db.{Deposit, Redeem, Swap}
import doobie.implicits._
import doobie.util.update.Update0

//todo: current version is only for testing
object OrdersSql {

  def getDepositOrderSQL(txOutRef: String): Query0[Deposit] =
    sql"select * from deposit where order_input_id = ?".query[Deposit]

  def getSwapOrderSQL(txOutRef: String): Query0[Swap] =
    sql"select * from swap where order_input_id = ?".query[Swap]

  def getRedeemOrderSQL(txOutRef: String): Query0[Redeem] =
    sql"select * from redeem where order_input_id = ?".query[Redeem]

  def updateExecutedSwapOrderSQL(): Update0 = ???

  def deleteExecutedSwapOrderSQL(): Update0 = ???

  def updateExecutedDepositOrderSQL(): Update0 = ???

  def deleteExecutedDepositOrderSQL(): Update0 = ???

  def updateExecutedRedeemOrderSQL(): Update0 = ???

  def deleteExecutedRedeemOrderSQL(): Update0 = ???
}
