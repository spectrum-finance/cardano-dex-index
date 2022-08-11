package fi.spectrumlabs.rates.resolver.services

import cats.{Functor, Monad}
import derevo.derive
import fi.spectrumlabs.core.models.domain._
import fi.spectrumlabs.rates.resolver.repositories.PoolsRepo
import tofu.syntax.monadic._
import tofu.syntax.logging._
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}

@derive(representableK)
trait PoolsService[F[_]] {
  def getAllLatest(minLiquidityValue: Long): F[List[Pool]]
}

object PoolsService {

  def create[I[_]: Functor, F[_]: Monad](implicit pools: PoolsRepo[F], logs: Logs[I, F]): I[PoolsService[F]] =
    logs.forService[PoolsService[F]].map(implicit __ => new Tracing[F] attach new Impl[F])

  final private class Impl[F[_]: Functor](implicit pools: PoolsRepo[F]) extends PoolsService[F] {

    def getAllLatest(minLiquidityValue: Long): F[List[Pool]] =
      pools
        .getAllLatest(minLiquidityValue)
        .map(_.map(Pool.fromDb))
  }

  final class Tracing[F[_]: Monad: Logging] extends PoolsService[Mid[F, *]] {

    def getAllLatest(minLiquidityValue: Long): Mid[F, List[Pool]] =
      for {
        _ <- trace"Going to get all pools from db with limit lq $minLiquidityValue"
        r <- _
        _ <- trace"Pools from db are: $r"
      } yield r
  }
}
