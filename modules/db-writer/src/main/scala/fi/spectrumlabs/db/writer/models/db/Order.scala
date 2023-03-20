package fi.spectrumlabs.db.writer.models.db

import fi.spectrumlabs.db.writer.classes.ToSchema
import fi.spectrumlabs.db.writer.models.cardano.{Action, DepositAction, RedeemAction, SwapAction, Order => OrderA}
import fi.spectrumlabs.db.writer.persistence.Persist
import cats.syntax.option._

trait Order

object Order {

  //todo: refactor
  implicit def schema: ToSchema[OrderA[Action], Option[Order]] = new ToSchema[OrderA[Action], Option[Order]] {
    override def apply(in: OrderA[Action]): Option[Order] = in.order.action match {
      case swap: SwapAction =>
        Swap.streamingSchema.apply(in.copy(order = in.order.copy(action = swap)))
      case deposit: DepositAction =>
        Deposit.streamingSchema.apply(in.copy(order = in.order.copy(action = deposit)))
      case redeem: RedeemAction =>
        Redeem.streamingSchema.apply(in.copy(order = in.order.copy(action = redeem)))
//      case deposit: DepositAction => ???
//      case redeem: RedeemAction => ???
    }
  }
}
