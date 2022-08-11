package fi.spectrumlabs.core.models

import cats.{Eq, Show}
import doobie.util.{Get, Put}
import fi.spectrumlabs.core.models.domain.{Amount, PoolId}
import io.circe.{Decoder, Encoder}
import io.estatico.newtype.macros.newtype
import scodec.bits.ByteVector
import sttp.tapir.{Codec, Schema, Validator}
import tofu.logging.Loggable
import scodec.bits.ByteVector

import java.nio.charset.Charset

package object domain {
  implicit val charset: Charset = Charset.defaultCharset()

  @newtype final case class PoolId(value: String)

  object PoolId {
    implicit val loggable: Loggable[PoolId]           = deriving
    implicit val get: Get[PoolId]                     = deriving
    implicit val put: Put[PoolId]                     = deriving
    implicit val eq: Eq[PoolId]                       = deriving
    implicit val encoder: Encoder[PoolId]             = deriving
    implicit val decoder: Decoder[PoolId]             = deriving
    implicit val schema: Schema[PoolId]               = deriving
    implicit val validator: Validator[PoolId]         = schema.validator
    implicit def plainCodec: Codec.PlainCodec[PoolId] = deriving
  }

  @newtype final case class Amount(value: Long) {
    def dropPenny(decimal: Int): BigDecimal = BigDecimal(s"$value".take(decimal))
  }

  object Amount {
    implicit val encoder: Encoder[Amount]     = deriving
    implicit val decoder: Decoder[Amount]     = deriving
    implicit val loggable: Loggable[Amount]   = deriving
    implicit val get: Get[Amount]             = deriving
    implicit val put: Put[Amount]             = deriving
    implicit val show: Show[Amount]           = deriving
    implicit val schema: Schema[Amount]       = deriving
    implicit val validator: Validator[Amount] = schema.validator
  }

  @newtype final case class Coin(value: String)

  object Coin {
    implicit val encoder: Encoder[Coin]   = deriving
    implicit val decoder: Decoder[Coin]   = deriving
    implicit val loggable: Loggable[Coin] = deriving
    implicit val get: Get[Coin]           = deriving
    implicit val put: Put[Coin]           = deriving
    implicit val show: Show[Coin]         = deriving
  }

  @newtype final case class Fee(value: BigDecimal)

  object Fee {
    implicit val encoder: Encoder[Fee]   = deriving
    implicit val decoder: Decoder[Fee]   = deriving
    implicit val loggable: Loggable[Fee] = deriving
    implicit val get: Get[Fee]           = deriving
    implicit val put: Put[Fee]           = deriving
    implicit val show: Show[Fee]         = deriving
  }
}
