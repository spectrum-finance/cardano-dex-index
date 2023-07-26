package fi.spectrumlabs.db.writer.models.db

import cats.Show
import cats.syntax.either._
import doobie.{Get, Put}
import enumeratum.{Enum, EnumEntry}
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema
import tofu.logging.Loggable

sealed abstract class OrderTypeDB(override val entryName: String) extends EnumEntry

object OrderTypeDB extends Enum[OrderTypeDB] {
  def values: IndexedSeq[OrderTypeDB] = findValues

  case object Swap extends OrderTypeDB("swap")
  case object Redeem extends OrderTypeDB("redeem")
  case object Deposit extends OrderTypeDB("deposit")

  implicit val encoder: Encoder[OrderTypeDB] = Encoder[String].contramap(_.entryName)

  implicit val decoder: Decoder[OrderTypeDB] =
    Decoder[String].emap(s => withNameInsensitiveEither(s).leftMap(_.getMessage()))

  implicit val put: Put[OrderTypeDB] = Put[String].contramap(_.entryName)
  implicit val get: Get[OrderTypeDB] = Get[String].temap(s => withNameInsensitiveEither(s).leftMap(_.getMessage()))

  implicit val show: Show[OrderTypeDB] = _.entryName

  implicit val loggable: Loggable[OrderTypeDB] = Loggable.show

  implicit val schema: Schema[OrderTypeDB] = Schema.derived
}
