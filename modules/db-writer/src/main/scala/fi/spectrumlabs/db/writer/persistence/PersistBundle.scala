package fi.spectrumlabs.db.writer.persistence

import cats.data.NonEmptyList
import cats.{Applicative, FlatMap, Monad}
import fi.spectrumlabs.db.writer.models._
import fi.spectrumlabs.db.writer.models.cardano.{Confirmed, PoolEvent}
import fi.spectrumlabs.db.writer.models.db._
import fi.spectrumlabs.db.writer.schema.Schema._
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import fi.spectrumlabs.db.writer.schema._
import tofu.syntax.monadic._

final case class PersistBundle[F[_]](
  input: Persist[Input, F],
  output: Persist[Output, F],
  transaction: Persist[Transaction, F],
  deposit: Persist[Deposit, F],
  swap: Persist[Swap, F],
  redeem: Persist[Redeem, F],
  pool: Persist[Pool, F]
)

object PersistBundle {

  def create[D[_]: FlatMap: LiftConnectionIO, F[_]: Monad](implicit
    elh: EmbeddableLogHandler[D],
    txr: Txr[F, D]
  ): PersistBundle[F] =
    PersistBundle(
      Persist.create[Input, D, F](input),
      Persist.create[Output, D, F](output),
      Persist.create[Transaction, D, F](transaction),
      Persist.create[Deposit, D, F](depositSchema),
      Persist.create[Swap, D, F](swapSchema),
      Persist.create[Redeem, D, F](redeemSchema),
      Persist.create[Pool, D, F](pool)
    )
}
