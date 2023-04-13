package fi.spectrumlabs.db.writer.sql

import doobie.Update
import doobie.util.query.Query0
import fi.spectrumlabs.db.writer.models.db.Pool
import doobie.implicits._
import doobie.util.log.LogHandler
import doobie.util.update.Update0
import fi.spectrumlabs.db.writer.models.cardano.FullTxOutRef

object PoolSql {

  def getPoolByOutputIdSQL(outputId: FullTxOutRef): Query0[Pool] =
    sql"""select pool_id,
         |    reserves_x,
         |    reserves_y,
         |    liquidity,
         |    x,
         |    y,
         |    lq,
         |    pool_fee_num,
         |    pool_fee_den,
         |    out_collateral,
         |    output_id,
         |    timestamp from pool where output_id = $outputId""".stripMargin.query

  def updatePoolTimestampSQL(outputId: FullTxOutRef, newTimestamp: Long): Update0 = {
    implicit val logHandler = LogHandler.jdkLogHandler
    Update[(Long, FullTxOutRef)](
      "update pool set timestamp = ? where output_id = ?"
    ).toUpdate0((newTimestamp, outputId))
  }
}
