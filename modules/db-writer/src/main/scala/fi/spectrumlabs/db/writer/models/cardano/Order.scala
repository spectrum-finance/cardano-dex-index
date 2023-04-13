package fi.spectrumlabs.db.writer.models.cardano

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}
import cats.syntax.either._
import derevo.circe.magnolia.decoder
import derevo.derive

sealed trait Order

final case class SwapOrder(fullTxOut: FullTxOut, order: OrderAction[SwapAction], slotNo: Long) extends Order
object SwapOrder {

  implicit def decoder: Decoder[SwapOrder] = new Decoder[SwapOrder] {

    override def apply(c: HCursor): Result[SwapOrder] = {
      c.downField("slotNo").as[Long].flatMap { slotNo =>
        c.downField("event").values.toRight(DecodingFailure("Order should contains fields", List.empty)).flatMap { orderFields =>
          for {
            fullTxOut <- orderFields.head.as[FullTxOut]
            value <- if (orderFields.size == 2) orderFields.last.as[OrderAction[SwapAction]]
            else DecodingFailure("Deposit pair doesn't contain 2 elems", List.empty).asLeft
          } yield SwapOrder(fullTxOut, value, slotNo)
        }
      }
    }
  }
}

final case class DepositOrder(fullTxOut: FullTxOut, order: OrderAction[DepositAction], slotNo: Long) extends Order

object DepositOrder {

  implicit def decoder: Decoder[DepositOrder] = new Decoder[DepositOrder] {

    override def apply(c: HCursor): Result[DepositOrder] =
      c.downField("slotNo").as[Long].flatMap { slotNo =>
        c.downField("event").values.toRight(DecodingFailure("Order should contains fields", List.empty)).flatMap { orderFields =>
          for {
            fullTxOut <- orderFields.head.as[FullTxOut]
            value <- if (orderFields.size == 2) orderFields.last.as[OrderAction[DepositAction]]
            else DecodingFailure("Deposit pair doesn't contain 2 elems", List.empty).asLeft
          } yield DepositOrder(fullTxOut, value, slotNo)
        }
      }
  }
}

final case class RedeemOrder(fullTxOut: FullTxOut, order: OrderAction[RedeemAction], slotNo: Long) extends Order

object RedeemOrder {

  implicit def decoder: Decoder[RedeemOrder] = new Decoder[RedeemOrder] {

    override def apply(c: HCursor): Result[RedeemOrder] = {
      c.downField("slotNo").as[Long].flatMap { slotNo =>
        c.downField("event").values.toRight(DecodingFailure("Order should contains fields", List.empty)).flatMap { orderFields =>
          for {
            fullTxOut <- orderFields.head.as[FullTxOut]
            value <- if (orderFields.size == 2) orderFields.last.as[OrderAction[RedeemAction]]
            else DecodingFailure("Deposit pair doesn't contain 2 elems", List.empty).asLeft
          } yield RedeemOrder(fullTxOut, value, slotNo)
        }
      }
    }
  }
}

object Order {

  implicit def commonDecoder: Decoder[Order] = new Decoder[Order] {

    override def apply(c: HCursor): Result[Order] =
      (c.as[SwapOrder]).orElse(c.as[DepositOrder]).orElse(c.as[RedeemOrder])
  }
}
