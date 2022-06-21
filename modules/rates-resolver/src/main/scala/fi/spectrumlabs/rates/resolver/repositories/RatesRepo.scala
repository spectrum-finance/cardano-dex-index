package fi.spectrumlabs.rates.resolver.repositories

import cats.syntax.show._
import dev.profunktor.redis4cats.RedisCommands
import fi.spectrumlabs.core.models.rates.ResolvedRate
import io.circe.syntax._

trait RatesRepo[F[_]] {
  def put(rate: ResolvedRate): F[Unit]
}

object RatesRepo {

  def create[F[_]](implicit cmd: RedisCommands[F, String, String]): RatesRepo[F] =
    new Impl[F]

  final private class Impl[F[_]](implicit cmd: RedisCommands[F, String, String]) extends RatesRepo[F] {

    def put(rate: ResolvedRate): F[Unit] =
      cmd.set(rate.asset.show, rate.asJson.noSpaces)
  }
}
