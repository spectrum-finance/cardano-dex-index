package fi.spectrumlabs.repositories

import cats.Functor
import cats.syntax.functor._
import derevo.derive
import dev.profunktor.redis4cats.RedisCommands
import fi.spectrumlabs.core.redis.RedisConfig
import retry.RetryPolicies.constantDelay
import retry.implicits.retrySyntaxError
import retry.{RetryDetails, RetryPolicy, Sleep}
import tofu.MonadThrow
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._

@derive(representableK)
trait TrackerCache[F[_]] {

  def setLastTxOffset(offset: Long): F[Unit]

  def setLastBlockOffset(offset: Long): F[Unit]

  def getLastTxOffset: F[Long]

  def getLastBlockOffset: F[Long]
}

object TrackerCache {

  def create[I[_]: Functor, F[_]: MonadThrow: Sleep](config: RedisConfig)(implicit
    redis: RedisCommands[F, String, Long],
    logs: Logs[I, F]
  ): I[TrackerCache[F]] =
    logs.forService[TrackerCache[F]].map(implicit __ => new Impl[F](config))

  private final class Impl[F[_]: MonadThrow: Logging: Sleep](config: RedisConfig)(implicit
    redis: RedisCommands[F, String, Long]
  ) extends TrackerCache[F] {

    private val policy: RetryPolicy[F] = constantDelay(config.retryTimeout)

    private def onError(name: String): (Throwable, RetryDetails) => F[Unit] =
      (err, details) =>
        error"Failed to exec $name in cache. The error is: ${err.getMessage}. Retry details are: ${details.toString}."

    def setLastTxOffset(offset: Long): F[Unit] =
      redis
        .set(txKey, offset)
        .retryingOnAllErrors(policy, onError("set offset"))

    def setLastBlockOffset(offset: Long): F[Unit] =
      redis
        .set(blockKey, offset)
        .retryingOnAllErrors(policy, onError("set offset"))

    def getLastTxOffset: F[Long] =
      redis
        .get(txKey)
        .map(_.getOrElse(0L))
        .retryingOnAllErrors(policy, onError("get offset"))

    def getLastBlockOffset: F[Long] =
      redis
        .get(blockKey)
        .map(_.getOrElse(0L))
        .retryingOnAllErrors(policy, onError("get offset"))

    private val txKey: String = "tracker-offset-last"
    private val blockKey: String = "tracker-offset-last"
  }
}
