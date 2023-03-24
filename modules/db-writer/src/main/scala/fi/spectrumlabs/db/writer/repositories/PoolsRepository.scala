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
import tofu.higherKind.derived.representableK

@derive(representableK)
trait PoolsRepository[F[_]] {

  def getPoolByOutputId(id: String): F[Option[Pool]]

  def updatePoolTimestamp(outputId: String, newTimestamp: Long): F[Int]
}

object PoolsRepository {

  def make[I[_]: Functor, F[_]: Monad, DB[_]: LiftConnectionIO](implicit
    txr: Txr[F, DB],
    logs: Logs[I, F]
  ): I[PoolsRepository[F]] = logs.forService[PoolsRepository[F]].map { implicit logging =>
    new PoolsRepositoryTracingMid[F] attach (new LiveCIO().mapK(LiftConnectionIO[DB].liftF andThen txr.trans))
  }

  final private class LiveCIO extends PoolsRepository[ConnectionIO] {

    import fi.spectrumlabs.db.writer.sql.PoolSql._

    override def getPoolByOutputId(id: String): ConnectionIO[Option[Pool]] =
      getPoolByOutputIdSQL(id).option

    override def updatePoolTimestamp(outputId: String, newTimestamp: Long): ConnectionIO[Int] =
      updatePoolTimestampSQL(outputId, newTimestamp).run
  }

  final private class PoolsRepositoryTracingMid[F[_]: Monad: Logging] extends PoolsRepository[Mid[F, *]] {

    override def getPoolByOutputId(id: String): Mid[F, Option[Pool]] = for {
      _ <- info"Going to get pool by output id: $id"
      res <- _
      _ <- info"Pool by output id: ${res.toString}"
    } yield res

    override def updatePoolTimestamp(outputId: String, newTimestamp: Long): Mid[F, Int] = for {
      _ <- info"Going to update timestamp for pool with outputId $outputId to $newTimestamp"
      res <- _
      _ <- info"Result of update timestamp for pool with outputId $outputId to $newTimestamp is $res"
    } yield res
  }
}
