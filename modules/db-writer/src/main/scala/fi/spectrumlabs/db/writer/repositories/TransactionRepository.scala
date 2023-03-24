package fi.spectrumlabs.db.writer.repositories

import cats.{Apply, Functor, Monad}
import derevo.derive
import doobie.ConnectionIO
import tofu.doobie.LiftConnectionIO
import tofu.doobie.transactor.Txr
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import cats.tagless.syntax.functorK._
import fi.spectrumlabs.db.writer.models.Transaction

@derive(representableK)
trait TransactionRepository[F[_]] {

  def getTxByHash(txHash: String): F[Option[Transaction]]
}

object TransactionRepository {

  def make[I[_]: Functor, F[_]: Monad, DB[_]: LiftConnectionIO](implicit
    txr: Txr[F, DB],
    logs: Logs[I, F]
  ): I[TransactionRepository[F]] =
    logs.forService[TransactionRepository[F]].map { implicit logging =>
      new TransactionRepositoryTracingMid[F] attach (new LiveCIO().mapK(LiftConnectionIO[DB].liftF andThen txr.trans))
    }

  final private class LiveCIO extends TransactionRepository[ConnectionIO] {

    import fi.spectrumlabs.db.writer.sql.TransactionsSql._

    def getTxByHash(txHash: String): ConnectionIO[Option[Transaction]] =
      getTransactionByHashSQL(txHash).option
  }

  final private class TransactionRepositoryTracingMid[F[_]: Apply: Logging] extends TransactionRepository[Mid[F, *]] {

    def getTxByHash(txHash: String): Mid[F, Option[Transaction]] =
      info"Going to get tx with hash $txHash" *> _
  }
}
