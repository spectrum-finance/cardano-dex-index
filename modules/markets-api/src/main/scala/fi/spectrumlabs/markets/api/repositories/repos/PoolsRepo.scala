package fi.spectrumlabs.markets.api.repositories.repos

import cats.{FlatMap, Functor}
import derevo.derive
import doobie.ConnectionIO
import fi.spectrumlabs.core.models.db.Pool
import fi.spectrumlabs.core.models.domain.{PoolId, Pool => DomainPool}
import fi.spectrumlabs.markets.api.models.{PoolOverview, PoolVolume}
import fi.spectrumlabs.markets.api.repositories.sql.PoolsSql
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import cats.tagless.syntax.functorK._
import fi.spectrumlabs.markets.api.models.db.PoolDb

import scala.concurrent.duration.FiniteDuration

@derive(representableK)
trait PoolsRepo[D[_]] {
  def getPools: D[List[PoolDb]]

  def getPoolById(poolId: PoolId, minLiquidityValue: Long): D[Option[Pool]]

  def getPoolVolume(pool: DomainPool, period: FiniteDuration): D[Option[PoolVolume]]
}

object PoolsRepo {

  def create[I[_]: Functor, D[_]: FlatMap: LiftConnectionIO, F[_]](implicit
    elh: EmbeddableLogHandler[D],
    logs: Logs[I, D],
    txr: Txr[F, D]
  ): I[PoolsRepo[F]] =
    logs.forService[PoolsRepo[F]].map { implicit __ =>
      elh
        .embed(implicit lh => new Tracing[D] attach new Impl(new PoolsSql()).mapK(LiftConnectionIO[D].liftF))
        .mapK(txr.trans)
    }

  final private class Impl(sql: PoolsSql) extends PoolsRepo[ConnectionIO] {

    def getPools: ConnectionIO[List[PoolDb]] =
      sql.getPools.to[List]

    def getPoolById(poolId: PoolId, minLiquidityValue: Long): ConnectionIO[Option[Pool]] =
      sql.getPool(poolId, minLiquidityValue).option

    def getPoolVolume(pool: DomainPool, period: FiniteDuration): ConnectionIO[Option[PoolVolume]] =
      sql.getPoolVolume(pool, period).option
  }

  final private class Tracing[F[_]: FlatMap: Logging] extends PoolsRepo[Mid[F, *]] {

    def getPools: Mid[F, List[PoolDb]] =
      for {
        _ <- trace"Going to get all pools"
        r <- _
        _ <- trace"Pools from db are $r"
      } yield r

    def getPoolById(poolId: PoolId, minLiquidityValue: Long): Mid[F, Option[Pool]] =
      for {
        _ <- trace"Going to get pool with id $poolId and min lq value $minLiquidityValue"
        r <- _
        _ <- trace"Pool from db is $r"
      } yield r

    def getPoolVolume(pool: DomainPool, period: FiniteDuration): Mid[F, Option[PoolVolume]] =
      for {
        _ <- trace"Going to get pool volume for $pool with period $period"
        r <- _
        _ <- trace"Pool value is $r"
      } yield r
  }
}
