package fi.spectrumlabs.db.writer.repositories

import cats.{Apply, Functor, Monad}
import doobie.ConnectionIO
import fi.spectrumlabs.db.writer.models.db.Pool
import fi.spectrumlabs.db.writer.repositories.OutputsRepository.{LiveCIO, OrdersRepositoryTracingMid}
import tofu.doobie.LiftConnectionIO
import tofu.doobie.transactor.Txr
import tofu.higherKind.Mid
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import cats.tagless.syntax.functorK._
import derevo.derive
import fi.spectrumlabs.db.writer.models.cardano.FullTxOutRef
import tofu.higherKind.derived.representableK

@derive(representableK)
trait PoolsRepository[F[_]] {

  def getPoolByOutputId(id: FullTxOutRef): F[Option[Pool]]

  def updatePoolTimestamp(outputId: FullTxOutRef, newTimestamp: Long): F[Int]
}

object PoolsRepository {

  def make[F[_]: Monad, DB[_]: LiftConnectionIO](implicit
    txr: Txr[F, DB],
    logs: Logging.Make[F]
  ): PoolsRepository[F] = logs.forService[PoolsRepository[F]].map { implicit logging =>
    new PoolsRepositoryTracingMid[F] attach (new LiveCIO().mapK(LiftConnectionIO[DB].liftF andThen txr.trans))
  }

  final private class LiveCIO extends PoolsRepository[ConnectionIO] {

    import fi.spectrumlabs.db.writer.sql.PoolSql._

    override def getPoolByOutputId(id: FullTxOutRef): ConnectionIO[Option[Pool]] =
      getPoolByOutputIdSQL(id).option

    override def updatePoolTimestamp(outputId: FullTxOutRef, newTimestamp: Long): ConnectionIO[Int] =
      updatePoolTimestampSQL(outputId, newTimestamp).run
  }

  final private class PoolsRepositoryTracingMid[F[_]: Monad: Logging] extends PoolsRepository[Mid[F, *]] {

    override def getPoolByOutputId(id: FullTxOutRef): Mid[F, Option[Pool]] = for {
      _   <- info"Going to get pool by output id: ${id.toString}"
      res <- _
      _   <- info"Pool by output id: ${res.toString}"
    } yield res

    override def updatePoolTimestamp(outputId: FullTxOutRef, newTimestamp: Long): Mid[F, Int] = for {
      _   <- info"Going to update timestamp for pool with outputId ${outputId.toString} to $newTimestamp"
      res <- _
      _   <- info"Result of update timestamp for pool with outputId ${outputId.toString} to $newTimestamp is $res"
    } yield res
  }
}
