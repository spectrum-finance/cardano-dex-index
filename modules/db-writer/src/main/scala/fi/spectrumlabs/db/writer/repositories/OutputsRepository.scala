package fi.spectrumlabs.db.writer.repositories

import cats.{Apply, Functor, Monad}
import doobie.ConnectionIO
import fi.spectrumlabs.db.writer.models.Output
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
trait OutputsRepository[F[_]] {

  def dropOutputsByTxHash(txHash: String): F[Int]

  def getOutputsByTxHash(txHash: String): F[List[Output]]
}

object OutputsRepository {

  def make[I[_]: Functor, F[_]: Monad, DB[_]: LiftConnectionIO](implicit
    txr: Txr[F, DB],
    logs: Logs[I, F]
  ): I[OutputsRepository[F]] =
    logs.forService[OrdersRepository[F]].map { implicit logging =>
      new OrdersRepositoryTracingMid[F] attach (new LiveCIO().mapK(LiftConnectionIO[DB].liftF andThen txr.trans))
    }

  final private class LiveCIO extends OutputsRepository[ConnectionIO] {

    import fi.spectrumlabs.db.writer.sql.OutputsSql._

    def dropOutputsByTxHash(txHash: String): ConnectionIO[Int] =
      dropOutputsByTxHashSQL(txHash).run

    def getOutputsByTxHash(txHash: String): ConnectionIO[List[Output]] =
      getOutputsByTxHashSQL(txHash).to[List]
  }

  final private class OrdersRepositoryTracingMid[F[_]: Logging: Apply] extends OutputsRepository[Mid[F, *]] {

    def dropOutputsByTxHash(txHash: String): Mid[F, Int] =
      info"Going to drop outputs for tx with hash: $txHash" *> _

    def getOutputsByTxHash(txHash: String): Mid[F, List[Output]] =
      info"Going to get outputs of tx with hash: $txHash" *> _
  }
}
