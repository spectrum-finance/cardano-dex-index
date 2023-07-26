package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.db.writer.classes.Key
import fi.spectrumlabs.db.writer.models.orders.TxOutRef

trait DBOrder {
  val rewardPkh: String
  val orderInputId: TxOutRef
  val creationTimestamp: Long
}

object DBOrder {
  implicit val key: Key[DBOrder] = new Key[DBOrder] {
    override def getKey(in: DBOrder): String =
      in match {
        case deposit: Deposit => Deposit.key.getKey(deposit)
        case redeem: Redeem => Redeem.key.getKey(redeem)
        case swap: Swap => Swap.key.getKey(swap)
        case _ => ""
      }

    def getExtendedKey(in: DBOrder): String =
      in match {
        case deposit: Deposit => Deposit.key.getKey(deposit)
        case redeem: Redeem => Redeem.key.getKey(redeem)
        case swap: Swap => Swap.key.getKey(swap)
        case _ => ""
      }
  }
}