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

@derive(representableK)
trait InputsRepository[F[_]] {

  def dropInputsByTxHash(txHash: String): F[Int]
}

object InputsRepository {

  def make[F[_]: Monad, DB[_]: LiftConnectionIO](implicit
    txr: Txr[F, DB],
    logs: Logging.Make[F]
  ): InputsRepository[F] =
    logs.forService[InputsRepository[F]].map { implicit logging =>
      new InputsRepositoryTracingMid[F] attach (new LiveCIO().mapK(LiftConnectionIO[DB].liftF andThen txr.trans))
    }

  final private class LiveCIO extends InputsRepository[ConnectionIO] {

    import fi.spectrumlabs.db.writer.sql.InputsSql._

    def dropInputsByTxHash(txHash: String): ConnectionIO[Int] =
      dropInputsByTxHashSQL(txHash).run
  }

  final private class InputsRepositoryTracingMid[F[_]: Apply: Logging] extends InputsRepository[Mid[F, *]] {

    def dropInputsByTxHash(txHash: String): Mid[F, Int] =
      info"Going to drop inputs for tx with hash $txHash" *> _
  }
}
