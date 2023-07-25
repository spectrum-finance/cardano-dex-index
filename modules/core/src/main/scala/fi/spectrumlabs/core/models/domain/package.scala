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
    def dropPenny(decimal: Int): BigDecimal =
      if (decimal == 0) BigDecimal(value) else BigDecimal(value) / BigDecimal(10).pow(decimal)

    def withDecimal(decimal: Int): BigDecimal =
      if (decimal == 0) BigDecimal(value) else BigDecimal(value) / BigDecimal(10).pow(decimal)
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

  @newtype final case class Apr(value: Double)

  object Apr {
    implicit val encoder: Encoder[Apr]     = deriving
    implicit val decoder: Decoder[Apr]     = deriving
    implicit val loggable: Loggable[Apr]   = deriving
    implicit val get: Get[Apr]             = deriving
    implicit val put: Put[Apr]             = deriving
    implicit val show: Show[Apr]           = deriving
    implicit val schema: Schema[Apr]       = deriving
    implicit val validator: Validator[Apr] = schema.validator
  }
}
