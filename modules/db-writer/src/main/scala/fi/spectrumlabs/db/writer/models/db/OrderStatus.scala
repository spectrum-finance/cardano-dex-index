package fi.spectrumlabs.db.writer.models.db

import cats.Show
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import doobie.util.{Get, Put}
import enumeratum.{CirceEnum, Enum, EnumEntry}
import sttp.tapir.{Schema, Validator}
import tofu.logging.Loggable
import cats.syntax.either._

import scala.collection.immutable

sealed trait OrderStatus extends EnumEntry

@derive(encoder, decoder)
object OrderStatus extends Enum[OrderStatus] with CirceEnum[OrderStatus] {

  case object Pending extends OrderStatus
  case object Register extends OrderStatus
  case object Evaluated extends OrderStatus
  case object Refunded extends OrderStatus
  case object NeedRefund extends OrderStatus

  val values: immutable.IndexedSeq[OrderStatus] = findValues

  implicit val show: Show[OrderStatus]         = _.entryName
  implicit val loggable: Loggable[OrderStatus] = Loggable.stringValue.contramap(_.entryName)

  implicit val schema: Schema[OrderStatus] =
    Schema.string.validate(Validator.enumeration(OrderStatus.values.toList, v => Option(v)))

  implicit val get: Get[OrderStatus] =
    Get[String].temap(s => withNameEither(s).leftMap(_ => s"No such OrderStatus [$s]"))

  implicit val put: Put[OrderStatus] =
    Put[String].contramap[OrderStatus](_.entryName)
}
