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
  redeemer: Persist[Redeemer, F],
  order: Persist[Order, F],
  pool: Persist[Pool, F]
)

object PersistBundle {

  def create[D[_]: FlatMap: LiftConnectionIO, F[_]: Monad](implicit
                                                           elh: EmbeddableLogHandler[D],
                                                           txr: Txr[F, D]
  ): PersistBundle[F] = {
    val depositP = Persist.create[Deposit, D, F](depositSchema)
    val swapP = Persist.create[Swap, D, F](swapSchema)
    val redeemP = Persist.create[Redeem, D, F](redeemSchema)
    val orderPersist = new Persist[Order, F] {
      override def persist(inputs: NonEmptyList[Order]): F[Int] =
        inputs.traverse {
          case deposit: Deposit => depositP.persist(NonEmptyList.one(deposit))
          case redeem: Redeem => redeemP.persist(NonEmptyList.one(redeem))
          case swap: Swap => swapP.persist(NonEmptyList.one(swap))
        } >> 0.pure
    }
    PersistBundle(
      Persist.create[Input, D, F](input),
      Persist.create[Output, D, F](output),
      Persist.create[Transaction, D, F](transaction),
      Persist.create[Redeemer, D, F](redeemer),
      orderPersist,
      Persist.create[Pool, D, F](pool)
    )
  }
}
