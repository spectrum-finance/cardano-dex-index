package fi.spectrumlabs.markets.api.repositories.repos

import cats.{FlatMap, Functor}
import derevo.derive
import doobie.ConnectionIO
import fi.spectrumlabs.core.models.db.Pool
import fi.spectrumlabs.core.models.domain.{PoolFee, PoolId, Pool => DomainPool}
import fi.spectrumlabs.markets.api.models.{PoolFeesSnapshot, PoolVolume, PoolVolumeDb, PoolVolumeDbNew}
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
import fi.spectrumlabs.core.models.domain
import fi.spectrumlabs.markets.api.models.db.{AvgAssetAmounts, PoolDb, PoolDbNew, PoolFeeSnapshot}
import fi.spectrumlabs.markets.api.v1.endpoints.models.TimeWindow

import scala.concurrent.duration.FiniteDuration

@derive(representableK)
trait PoolsRepo[D[_]] {
  def getPools: D[List[PoolDbNew]]

  def getPoolsOld: D[List[PoolDb]]

  def getPoolById(poolId: PoolId, minLiquidityValue: Long): D[Option[Pool]]

  def getPoolVolume(pool: DomainPool, from: Long): D[Option[PoolVolume]]

  def getPoolVolumes(period: TimeWindow): D[List[PoolVolumeDbNew]]

  def getAvgPoolSnapshot(id: PoolId, tw: TimeWindow, resolution: Long): D[List[AvgAssetAmounts]]

  def getFirstPoolSwapTime(id: PoolId): D[Option[Long]]

  def fees(pool: domain.Pool, window: TimeWindow, poolFee: PoolFee): D[Option[PoolFeeSnapshot]]

  def getPoolList: D[List[PoolId]]

  def getPoolStateByDate(poolId: PoolId, date: Long): D[Option[Pool]]
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

  final class Impl(sql: PoolsSql) extends PoolsRepo[ConnectionIO] {

    def getPoolList: ConnectionIO[List[PoolId]] =
      sql.getPoolList.to[List]

    def getPoolStateByDate(poolId: PoolId, date: Long): ConnectionIO[Option[Pool]] =
      sql.getPoolStateByDate(poolId, date).option

    def getPools: ConnectionIO[List[PoolDbNew]] =
      sql.getPools.to[List]

    def getPoolsOld: ConnectionIO[List[PoolDb]] =
      sql.getPoolsOld.to[List]

    def getPoolById(poolId: PoolId, minLiquidityValue: Long): ConnectionIO[Option[Pool]] =
      sql.getPool(poolId, minLiquidityValue).option

    def getPoolVolume(pool: DomainPool, from: Long): ConnectionIO[Option[PoolVolume]] =
      sql.getPoolVolume(pool, from).option

    def getPoolVolumes(period: TimeWindow): ConnectionIO[List[PoolVolumeDbNew]] =
      sql.getPoolVolumes(period).to[List]

    def getAvgPoolSnapshot(id: PoolId, tw: TimeWindow, resolution: Long): ConnectionIO[List[AvgAssetAmounts]] =
      sql.getAvgPoolSnapshot(id, tw, resolution).to[List]

    def getFirstPoolSwapTime(id: PoolId): ConnectionIO[Option[Long]] =
      sql.getFirstPoolSwapTime(id).option

    def fees(pool: domain.Pool, window: TimeWindow, poolFee: PoolFee): ConnectionIO[Option[PoolFeeSnapshot]] =
      sql.getPoolFees(pool, window, poolFee).option
  }

  final private class Tracing[F[_]: FlatMap: Logging] extends PoolsRepo[Mid[F, *]] {

    def getPoolStateByDate(poolId: PoolId, date: Long):  Mid[F, Option[Pool]] =
      for {
        _ <- trace"Going to get pool state by $date"
        r <- _
        _ <- trace"Pool state from db is $r"
      } yield r

    def getPools: Mid[F, List[PoolDbNew]] =
      for {
        _ <- trace"Going to get all pools"
        r <- _
        _ <- trace"Pools from db are $r"
      } yield r

    def getPoolList: Mid[F, List[PoolId]] =
      for {
        _ <- trace"Going to get all pool ids"
        r <- _
        _ <- trace"Pool ids from db are $r"
      } yield r

    def getPoolById(poolId: PoolId, minLiquidityValue: Long): Mid[F, Option[Pool]] =
      for {
        _ <- trace"Going to get pool with id $poolId and min lq value $minLiquidityValue"
        r <- _
        _ <- trace"Pool from db is $r"
      } yield r

    def getPoolVolumes(period: TimeWindow): Mid[F, List[PoolVolumeDbNew]] =
      for {
        _ <- trace"Going to get total pool volumes for period $period"
        r <- _
        _ <- trace"Total pool volumes are $r"
      } yield r

    def getPoolVolume(pool: DomainPool, from: Long): Mid[F, Option[PoolVolume]] =
      for {
        _ <- trace"Going to get pool volume for $pool from $from"
        r <- _
        _ <- trace"Pool value is $r"
      } yield r

    def getAvgPoolSnapshot(id: PoolId, tw: TimeWindow, resolution: Long): Mid[F, List[AvgAssetAmounts]] =
      for {
        _ <- trace"Going to get avg pool snapshot for $id with period $resolution within $tw"
        r <- _
        _ <- trace"Pool value is $r"
      } yield r

    def getFirstPoolSwapTime(id: PoolId): Mid[F, Option[Long]] =
      for {
        _ <- trace"getFirstPoolSwapTime(id: $id)"
        r <- _
        _ <- trace"getFirstPoolSwapTime(id: $id) -> $r"
      } yield r

    def fees(pool: domain.Pool, window: TimeWindow, poolFee: PoolFee) =
      for {
        _ <- trace"fees(id: ${pool.id})"
        r <- _
        _ <- trace"fees(id: ${pool.id}) -> $r"
      } yield r

    def getPoolsOld: Mid[F, List[PoolDb]] =
      for {
        _ <- trace"getPoolsOld()"
        r <- _
        _ <- trace"getPoolsOld() -> $r"
      } yield r
  }
}
