package fi.spectrumlabs.markets.api.repositories.repos

import cats.{Functor, Monad}
import dev.profunktor.redis4cats.RedisCommands
import fi.spectrumlabs.core.models.domain.{AssetClass, PoolId}
import fi.spectrumlabs.core.models.rates.ResolvedRate
import cats.syntax.show._
import cats.syntax.eq._
import tofu.syntax.monadic._
import tofu.syntax.logging._
import derevo.derive
import fi.spectrumlabs.core.{AdaAssetClass, AdaDefaultPoolId}
import io.circe.parser.parse
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}

@derive(representableK)
trait RatesRepo[F[_]] {
  def get(asset: AssetClass, poolId: PoolId): F[Option[ResolvedRate]]
}

object RatesRepo {

  def create[I[_]: Functor, F[_]: Monad](implicit
    cmd: RedisCommands[F, String, String],
    logs: Logs[I, F]
  ): I[RatesRepo[F]] =
    logs.forService[RatesRepo[F]].map(implicit __ => new Tracing[F] attach new Impl[F])

  final private class Impl[F[_]: Functor](implicit cmd: RedisCommands[F, String, String]) extends RatesRepo[F] {

    def get(asset: AssetClass, poolId: PoolId): F[Option[ResolvedRate]] =
      cmd.get(mkKey(asset, poolId)).map(_.flatMap(parse(_).flatMap(_.as[ResolvedRate]).toOption))
  }

  final private class Tracing[F[_]: Monad: Logging] extends RatesRepo[Mid[F, *]] {

    def get(asset: AssetClass, poolId: PoolId): Mid[F, Option[ResolvedRate]] =
      for {
        _ <- trace"Going to get rate for $asset and $poolId. Keys is ${mkKey(asset, poolId)}"
        r <- _
        _ <- trace"Rate for $asset and $poolId is $r"
      } yield r
  }

  private def mkKey(asset: AssetClass, poolId: PoolId): String =
    if (asset === AdaAssetClass) s"${asset.show}"
    else s"${asset.show}"
}
