package fi.spectrumlabs.db.writer.persistence

import cats.data.NonEmptyList
import cats.{Applicative, FlatMap, Monad}
import fi.spectrumlabs.core.cache.Cache.Plain
import fi.spectrumlabs.db.writer.models._
import fi.spectrumlabs.db.writer.models.cardano.{Confirmed, PoolEvent}
import fi.spectrumlabs.db.writer.models.db._
import fi.spectrumlabs.db.writer.schema.Schema._
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import fi.spectrumlabs.db.writer.schema._
import tofu.logging.Logging
import tofu.syntax.monadic._

import scala.concurrent.duration.FiniteDuration

final case class PersistBundle[F[_]](
  input: Persist[Input, F],
  output: Persist[Output, F],
  transaction: Persist[Transaction, F],
  deposit: Persist[Deposit, F],
  depositRedis: Persist[Deposit, F],
  swap: Persist[Swap, F],
  swapRedis: Persist[Swap, F],
  redeem: Persist[Redeem, F],
  redeemRedis: Persist[Redeem, F],
  pool: Persist[Pool, F]
)

object PersistBundle {

  def create[D[_]: FlatMap: LiftConnectionIO, F[_]: Monad](mempoolTtl: FiniteDuration)(implicit
    elh: EmbeddableLogHandler[D],
    redis: Plain[F],
    txr: Txr[F, D],
    logging: Logging.Make[F]
  ): PersistBundle[F] = logging.forService[PersistBundle[F]].map { implicit __ =>
    PersistBundle(
      Persist.create[Input, D, F](input),
      Persist.create[Output, D, F](output),
      Persist.create[Transaction, D, F](transaction),
      Persist.create[Deposit, D, F](depositSchema),
      Persist.createRedis[Deposit, F](mempoolTtl),
      Persist.create[Swap, D, F](swapSchema),
      Persist.createRedis[Swap, F](mempoolTtl),
      Persist.create[Redeem, D, F](redeemSchema),
      Persist.createRedis[Redeem, F](mempoolTtl),
      Persist.create[Pool, D, F](pool)
    )
  }
}
