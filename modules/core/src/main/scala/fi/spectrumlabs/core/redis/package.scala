package fi.spectrumlabs.core

import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import io.lettuce.core.{ClientOptions, TimeoutOptions}
import tofu.lift.Unlift

import scala.jdk.DurationConverters.ScalaDurationOps

package object redis {

  def mkRedis[K, V, I[_]: Sync, F[_]: Concurrent: ContextShift](
    redis: RedisConfig,
    codec: RedisCodec[K, V]
  )(implicit ul: Unlift[F, I]): Resource[I, RedisCommands[F, K, V]] = {
    import dev.profunktor.redis4cats.effect.Log.Stdout._
    import redis._
    for {
      timeoutOptions <- Resource.eval(Sync[I].delay(TimeoutOptions.builder().fixedTimeout(timeout.toJava).build()))
      clientOptions  <- Resource.eval(Sync[I].delay(ClientOptions.builder().timeoutOptions(timeoutOptions).build()))
      client         <- RedisClient[F].withOptions(s"redis://$password@$host:$port", clientOptions).mapK(ul.liftF)
      redisCmd       <- Redis[F].fromClient(client, codec).mapK(ul.liftF)
    } yield redisCmd
  }
}
