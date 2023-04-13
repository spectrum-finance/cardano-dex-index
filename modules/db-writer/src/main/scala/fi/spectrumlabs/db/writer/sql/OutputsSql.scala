package fi.spectrumlabs.db.writer.sql

import doobie.{Get, Update}
import doobie.implicits._
import doobie.util.query.Query0
import doobie.util.update.Update0
import fi.spectrumlabs.db.writer.models.Output
import io.circe.Json
import io.circe.parser._
import cats.syntax.either._
import doobie.util.log.LogHandler

object OutputsSql {

  implicit val getJson: Get[Json] = Get[String].temap(parse(_).leftMap(_.message))

  def dropOutputsByTxHashSQL(txHash: String): Update0 =
    Update[String]("drop * from output where tx_hash = ?").toUpdate0(txHash)

  def getOutputsByTxHashSQL(txHash: String): Query0[Output] =
    sql"""select
         |      tx_hash,
         |      tx_index,
         |      ref,
         |      block_hash,
         |      index,
         |      addr,
         |      raw_addr,
         |      payment_cred,
         |      value,
         |      data_hash,
         |      data,
         |      data_bin,
         |      spent_by_tx_hash from output where tx_hash = $txHash """.stripMargin.query
}
