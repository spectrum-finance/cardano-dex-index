package fi.spectrumlabs.rates.resolver.repositories

import cats.tagless.syntax.functorK._
import cats.{Functor, Monad}
import doobie.ConnectionIO
import fi.spectrumlabs.core.models.domain.Pool
import fi.spectrumlabs.rates.resolver.repositories.sql.PoolsSql
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.higherKind.{Mid, RepresentableK}
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._

trait Pools[F[_]] {
  def snapshots(minLiquidityValue: Long): F[List[Pool]]
}

object Pools {

  implicit def representableK: RepresentableK[Pools] =
    tofu.higherKind.derived.genRepresentableK

  def create[I[_]: Functor, D[_]: Monad: LiftConnectionIO](implicit
    elh: EmbeddableLogHandler[D],
    logs: Logs[I, D]
  ): I[Pools[D]] =
    logs.forService[Pools[D]].map { implicit __ =>
      elh
        .embed(implicit lh => new Tracing[D] attach new Live(new PoolsSql()).mapK(LiftConnectionIO[D].liftF))
    }

  final private class Live(sql: PoolsSql) extends Pools[ConnectionIO] {

    def snapshots(minLiquidityValue: Long): ConnectionIO[List[Pool]] =
      sql.snapshots(minLiquidityValue).to[List].map(_.flatMap(Pool.fromDBRatesResolver))
  }

  final private class Tracing[F[_]: Monad: Logging] extends Pools[Mid[F, *]] {
    def snapshots(minLiquidityValue: Long): Mid[F, List[Pool]] =
      for {
        _ <- trace"snapshots($minLiquidityValue)"
        r <- _
        _ <- trace"snapshots($minLiquidityValue) -> $r"
      } yield r
  }
}
