package fi.spectrumlabs.core.cache

import cats.Parallel
import cats.effect.{Concurrent, ContextShift, Resource, Timer}
import dev.profunktor.redis4cats.transactions.RedisTransaction
import fi.spectrumlabs.core.cache.Redis.logInstance
import fi.spectrumlabs.core.redis.RedisConfig
import tofu.logging.{Logging, Logs}

trait MakeRedisTransaction[F[_]] {

  def make: Resource[F, Redis.PlainTx[F]]
}

object MakeRedisTransaction {

  def make[F[_]: Parallel: Concurrent: ContextShift: Timer](config: RedisConfig)(implicit
    logs: Logs[F, F]
  ): MakeRedisTransaction[F] = new PlainCEInstance[F](config)

  final class PlainCEInstance[
    F[_]: Parallel: Concurrent: ContextShift: Timer
  ](config: RedisConfig)(implicit logs: Logs[F, F])
    extends MakeRedisTransaction[F] {

    def make: Resource[F, Redis.PlainTx[F]] =
      for {
        implicit0(log: Logging[F]) <- Resource.eval(logs.byName("redis-tx"))
        redis                      <- Redis.make[F, F](config)
      } yield RedisTransaction(redis)
  }
}
