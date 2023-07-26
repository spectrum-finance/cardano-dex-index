package fi.spectrumlabs.core.streaming

import cats.effect.Sync
import fs2.kafka._
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import cats.syntax.either._

object serde {

  implicit def recordSerializer[F[_]: Sync, A: Encoder]: RecordSerializer[F, A] =
    RecordSerializer.lift(Serializer.string.contramap(_.asJson.noSpaces))

  implicit def recordDeserializer[F[_]: Sync, A: Decoder]: RecordDeserializer[F, Option[A]] =
    RecordDeserializer.lift {
      Deserializer.string.map { str =>
        parse(str).flatMap(_.as[A]).toOption
      }
    }
}
