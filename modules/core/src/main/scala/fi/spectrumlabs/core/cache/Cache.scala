package fi.spectrumlabs.core.cache

import cats.data.OptionT
import cats.syntax.either._
import cats.syntax.show._
import cats.{Functor, Monad, Show}
import derevo.derive
import derevo.tagless.applyK
import dev.profunktor.redis4cats.RedisCommands
import fi.spectrumlabs.core.cache.errors._
import scodec.Codec
import scodec.bits.BitVector
import tofu.BracketThrow
import tofu.higherKind.Mid
import tofu.logging.{Loggable, Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import tofu.syntax.raise._

@derive(applyK)
trait Cache[F[_]] {

  def set[K: Codec: Loggable, V: Codec: Loggable](key: K, value: V): F[Unit]

  def get[K: Codec: Loggable, V: Codec: Loggable](key: K): F[Option[V]]

  def flushAll: F[Unit]
}

object Cache {

  type Plain[F[_]] = RedisCommands[F, Array[Byte], Array[Byte]]

  def make[I[_]: Functor, F[_]: Monad: BracketThrow](implicit
    redis: Plain[F],
    logs: Logs[I, F]
  ): I[Cache[F]] =
    logs.forService[Cache[F]].map { implicit l =>
      new CacheTracing[F] attach new Redis[F]
    }

  final class Redis[
    F[_]: Monad: BinaryEncodingFailed.Raise: BinaryDecodingFailed.Raise: BracketThrow
  ](implicit redis: Plain[F])
    extends Cache[F] {

    implicit def showFromLoggable[T](implicit l: Loggable[T]): Show[T] = l.showInstance

    def set[K: Codec: Loggable, V: Codec: Loggable](key: K, value: V): F[Unit] =
      for {
        k <- Codec[K]
          .encode(key)
          .toEither
          .leftMap(err => BinaryEncodingFailed(key.show, err.messageWithContext))
          .toRaise
        v <- Codec[V]
          .encode(value)
          .toEither
          .leftMap(err => BinaryEncodingFailed(key.show, err.messageWithContext))
          .toRaise
        _ <- redis.set(k.toByteArray, v.toByteArray)
      } yield ()

    def get[K: Codec: Loggable, V: Codec: Loggable](key: K): F[Option[V]] =
      (for {
        k <- OptionT.liftF(
          Codec[K]
            .encode(key)
            .toEither
            .leftMap(err => BinaryEncodingFailed(key.show, err.messageWithContext))
            .toRaise
        )
        raw <- OptionT(redis.get(k.toByteArray))
        value <- OptionT.liftF(
          Codec[V]
            .decode(BitVector(raw))
            .toEither
            .map(_.value)
            .leftMap(err => BinaryDecodingFailed(key.show, err.messageWithContext))
            .toRaise
        )
      } yield value).value

    def flushAll: F[Unit] =
      redis.flushAll
  }

  final class CacheTracing[F[_]: Monad: Logging] extends Cache[Mid[F, *]] {

    def set[K: Codec: Loggable, V: Codec: Loggable](key: K, value: V): Mid[F, Unit] =
      _ <* trace"set(key=$key, value=$value) -> ()"

    def get[K: Codec: Loggable, V: Codec: Loggable](key: K): Mid[F, Option[V]] =
      _ >>= (r => trace"get(key=$key) -> $r" as r)

    def flushAll: Mid[F, Unit] =
      _ <* trace"flushAll -> ()"
  }
}
