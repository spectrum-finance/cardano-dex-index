package fi.spectrumlabs.db.writer.schema

import doobie.Update
import doobie.util.Write
import doobie.util.log.LogHandler

trait Schema[T] {

  val tableName: String

  val fields: List[String]

  final def insert(implicit lh: LogHandler, w: Write[T]): Update[T] =
    Update[T](s"insert into $tableName ($fieldsString) values ($holdersString)")

  final def insertNoConflict(implicit lh: LogHandler, w: Write[T]): Update[T] =
    Update[T](s"insert into $tableName ($fieldsString) values ($holdersString) on conflict do nothing")

  private def fieldsString: String =
    fields.mkString(", ")

  private def holdersString: String =
    fields.map(_ => "?").mkString(", ")
}

object Schema {
  implicit val input: InputSchema                        = new InputSchema
  implicit val output: OutputSchema                      = new OutputSchema
  implicit val transaction: TransactionSchema            = new TransactionSchema
  implicit val depositSchema: DepositSchema              = new DepositSchema
  implicit val swapSchema: SwapSchema                    = new SwapSchema
  implicit val redeemSchema: RedeemSchema                = new RedeemSchema
  implicit val pool: PoolSchema                          = new PoolSchema
  implicit val executedInputSchema: ExecutedOrdersSchema = new ExecutedOrdersSchema
}
