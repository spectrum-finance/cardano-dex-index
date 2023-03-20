package fi.spectrumlabs.db.writer.models.cardano

import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, HCursor}
import cats.syntax.either._

final case class Order[A <: Action](fullTxOut: FullTxOut, order: OrderAction[A])

object Order {

  implicit def commonDecoder: Decoder[Order[Action]] = new Decoder[Order[Action]] {

    override def apply(c: HCursor): Result[Order[Action]] =
      c.as[Order[SwapAction]].orElse(c.as[Order[DepositAction]]).orElse(c.as[Order[RedeemAction]])
  }

  implicit def decoder[A <: Action](implicit actionDecoder: Decoder[A]): Decoder[Order[A]] = new Decoder[Order[A]] {

    override def apply(c: HCursor): Result[Order[A]] =
      c.values.toRight(DecodingFailure("Order should contains fields", List.empty)).flatMap { orderFields =>
        for {
          fullTxOut <- orderFields.head.as[FullTxOut]
          value <- if (orderFields.size == 2) orderFields.last.as[OrderAction[A]]
                   else DecodingFailure("Deposit pair doesn't contain 2 elems", List.empty).asLeft
        } yield Order(fullTxOut, value)
      }
  }
}
