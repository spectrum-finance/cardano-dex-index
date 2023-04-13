package fi.spectrumlabs.db.writer.sql

import doobie.Update
import doobie.util.update.Update0
import doobie.implicits._

object InputsSql {

  def dropInputsByTxHashSQL(txHash: String): Update0 =
    Update[String]("drop * from inputs where tx_hash = ?").toUpdate0(txHash)
}
