package fi.spectrumlabs.rates.resolver.repositories

import cats.tagless.syntax.functorK._
import cats.{FlatMap, Functor}
import doobie.ConnectionIO
import fi.spectrumlabs.core.models.db.{Pool, PoolResolver}
import fi.spectrumlabs.rates.resolver.repositories.sql.PoolsSql
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.logging.Logs
import cats.syntax.functor._
import derevo.derive
import tofu.doobie.transactor.Txr
import tofu.higherKind.derived.representableK

@derive(representableK)
trait PoolsRepo[F[_]] {
  def getAllLatest(minLiquidityValue: Long): F[List[PoolResolver]]
}

object PoolsRepo {

  def create[I[_]: Functor, D[_]: FlatMap: LiftConnectionIO, F[_]](implicit
    elh: EmbeddableLogHandler[D],
    logs: Logs[I, D],
    txr: Txr[F, D]
  ): I[PoolsRepo[F]] =
    logs.forService[PoolsRepo[F]].map { implicit l =>
      elh.embed(implicit lh => new Impl(new PoolsSql()).mapK(LiftConnectionIO[D].liftF)).mapK(txr.trans)
    }

  final private class Impl(sql: PoolsSql) extends PoolsRepo[ConnectionIO] {

    def getAllLatest(minLiquidityValue: Long): ConnectionIO[List[PoolResolver]] =
      sql.getAllLatest(minLiquidityValue).to[List]
  }
}
