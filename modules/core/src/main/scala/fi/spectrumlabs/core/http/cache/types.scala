package fi.spectrumlabs.core.http.cache

import io.estatico.newtype.macros.newtype
import scodec.Codec
import scodec.bits.ByteVector
import _root_.scodec.interop.cats._
import _root_.scodec.codecs._
import cats.Show
import cats.effect.Sync
import org.http4s.Request
import tofu.logging.Loggable
import tofu.syntax.monadic._

import java.security.MessageDigest

object types {

  @newtype
  case class RequestHash32(value: ByteVector)

  object RequestHash32 {
    implicit val show: Show[RequestHash32]         = deriving
    implicit val loggable: Loggable[RequestHash32] = Loggable.show
    implicit val codec: Codec[RequestHash32]       = fixedSizeBytes(32, bytes).xmap(RequestHash32(_), _.value)

    def apply[F[_]: Sync](request: Request[F]): F[RequestHash32] =
      request.body.compile.to(Seq).map { body =>
        RequestHash32(
          ByteVector(
            MessageDigest
              .getInstance("SHA-256")
              .digest(request.method.toString.getBytes ++ request.uri.toString.getBytes ++ body)
          )
        )
      }
  }
}
