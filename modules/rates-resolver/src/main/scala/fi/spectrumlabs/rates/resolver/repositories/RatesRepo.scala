package fi.spectrumlabs.rates.resolver.repositories

import cats.{Functor, Monad}
import derevo.derive
import dev.profunktor.redis4cats.RedisCommands
import fi.spectrumlabs.core.models.rates.ResolvedRate
import io.circe.syntax._
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._

@derive(representableK)
trait RatesRepo[F[_]] {
  def put(rate: ResolvedRate): F[Unit]
}

object RatesRepo {

  def create[I[_]: Functor, F[_]: Monad](implicit
    cmd: RedisCommands[F, String, String],
    logs: Logs[I, F]
  ): I[RatesRepo[F]] =
    logs.forService[RatesRepo[F]].map(implicit __ => new Tracing[F] attach new Impl[F])

  final private class Impl[F[_]](implicit cmd: RedisCommands[F, String, String]) extends RatesRepo[F] {

    def put(rate: ResolvedRate): F[Unit] =
      cmd.set(rate.cacheKey, rate.asJson.noSpaces)
  }

  final class Tracing[F[_]: Monad: Logging] extends RatesRepo[Mid[F, *]] {

    def put(rate: ResolvedRate): Mid[F, Unit] =
      for {
        _ <- info"Going to put new resolved rate $rate into storage. Key is ${rate.cacheKey}."
        _ <- _
        _ <- info"Rate $rate put successfully."
      } yield ()
  }
}
