package fi.spectrumlabs.db.writer.schema

import doobie.{ConnectionIO, Update}
import doobie.util.Write
import doobie.util.log.LogHandler
import doobie.util.update.Update0
import cats.syntax.traverse._
import fi.spectrumlabs.db.writer.models.ExecutedInput

final class ExecutedOrdersSchema {

  val tablesNames = List("deposit", "redeem", "swap")

  val fields: List[String] = List(
    "order_input_id",
    "user_output_id",
    "pool_input_Id",
    "pool_output_Id",
    "timestamp"
  )

  def updateExecuted(executedInput: ExecutedInput)(implicit lh: LogHandler): ConnectionIO[List[Int]] =
    tablesNames.traverse { tableName =>
      Update[(String, String, Long, String)](
        s"""
           |update $tableName
           |set user_output_id=?, pool_input_Id='unknown', pool_output_Id=?, execution_timestamp=?
           |where order_input_id=?""".stripMargin
      ).run(
        (
          executedInput.txHash.value,
          executedInput.txHash.value,
          executedInput.slot,
          executedInput.outRef.value ++ "#" ++ executedInput.outIndex.toString
        )
      )
    }
}
