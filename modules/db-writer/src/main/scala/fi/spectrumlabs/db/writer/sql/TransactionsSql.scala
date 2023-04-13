package fi.spectrumlabs.db.writer.sql

import doobie.util.query.Query0
import fi.spectrumlabs.db.writer.models.Transaction
import doobie.{Get, Update}
import doobie.implicits._
import io.circe.Json
import io.circe.parser._
import cats.syntax.either._
import doobie.util.log.LogHandler

object TransactionsSql {

  implicit val getJson: Get[Json] = Get[String].temap(parse(_).leftMap(_.message))

  implicit val getBigInt: Get[BigInt] = Get[Long].map(BigInt.apply)

  def getTransactionByHashSQL(txHash: String): Query0[Transaction] =
    sql"""select block_hash,
         |    block_index,
         |    hash,
         |    invalid_before,
         |    invalid_hereafter,
         |    metadata,
         |    size,
         |    timestamp from transaction where hash = $txHash""".stripMargin.query
}
