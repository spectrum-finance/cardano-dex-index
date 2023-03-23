package fi.spectrumlabs.db.writer.repositories

import cats.{Functor, Monad}
import derevo.derive
import doobie.ConnectionIO
import fi.spectrumlabs.db.writer.models.db.DBOrder
import tofu.doobie.LiftConnectionIO
import tofu.doobie.transactor.Txr
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.monadic._
import cats.tagless.syntax.functorK._
import tofu.syntax.logging._

@derive(representableK)
trait OrdersRepository[F[_]] {

  def getOrder(txOutRef: String): F[Option[DBOrder]]

  def updateExecutedSwapOrder(
    actualQuote: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  ): F[Int]

  def deleteExecutedSwapOrder(txOutRef: String): F[Int]

  def updateExecutedDepositOrder(
    amountLq: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  ): F[Int]

  def deleteExecutedDepositOrder(txOutRef: String): F[Int]

  def updateExecutedRedeemOrder(
    amountX: Long,
    amountY: Long,
    userOutputId: String,
    poolInputId: String,
    poolOutputId: String,
    timestamp: Long,
    orderInputId: String
  ): F[Int]

  def deleteExecutedRedeemOrder(txOutRef: String): F[Int]
}

object OrdersRepository {

  def make[I[_]: Functor, F[_]: Monad, DB[_]: LiftConnectionIO](implicit
    txr: Txr[F, DB],
    logs: Logs[I, F]
  ): I[OrdersRepository[F]] =
    logs.forService[OrdersRepository[F]].map { implicit logging =>
      new OrdersRepositoryTracingMid[F] attach new LiveCIO().mapK(LiftConnectionIO[DB].liftF andThen txr.trans)
    }

  final private class LiveCIO extends OrdersRepository[ConnectionIO] {

    import fi.spectrumlabs.db.writer.sql.OrdersSql._

    override def getOrder(txOutRef: String): ConnectionIO[Option[DBOrder]] = for {
      swapOpt    <- getSwapOrderSQL(txOutRef).option
      redeemOpt  <- getRedeemOrderSQL(txOutRef).option
      depositOpt <- getDepositOrderSQL(txOutRef).option
    } yield swapOpt.orElse(redeemOpt).orElse(depositOpt)

    override def updateExecutedSwapOrder(
      actualQuote: Long,
      userOutputId: String,
      poolInputId: String,
      poolOutputId: String,
      timestamp: Long,
      orderInputId: String
    ): ConnectionIO[Int] =
      updateExecutedSwapOrderSQL(actualQuote, userOutputId, poolInputId, poolOutputId, timestamp, orderInputId).run

    override def deleteExecutedSwapOrder(txOutRef: String): ConnectionIO[Int] =
      deleteExecutedSwapOrderSQL(txOutRef).run

    override def updateExecutedDepositOrder(
      amountLq: Long,
      userOutputId: String,
      poolInputId: String,
      poolOutputId: String,
      timestamp: Long,
      orderInputId: String
    ): ConnectionIO[Int] =
      updateExecutedDepositOrderSQL(amountLq, userOutputId, poolInputId, poolOutputId, timestamp, orderInputId).run

    override def deleteExecutedDepositOrder(txOutRef: String): ConnectionIO[Int] =
      deleteExecutedSwapOrderSQL(txOutRef).run

    override def updateExecutedRedeemOrder(
      amountX: Long,
      amountY: Long,
      userOutputId: String,
      poolInputId: String,
      poolOutputId: String,
      timestamp: Long,
      orderInputId: String
    ): ConnectionIO[Int] =
      updateExecutedRedeemOrderSQL(amountX, amountY, userOutputId, poolInputId, poolOutputId, timestamp, orderInputId).run

    override def deleteExecutedRedeemOrder(txOutRef: String): ConnectionIO[Int] =
      deleteExecutedRedeemOrderSQL(txOutRef).run
  }

  final private class OrdersRepositoryTracingMid[F[_]: Logging: Monad] extends OrdersRepository[Mid[F, *]] {

    def getOrder(txOutRef: String): Mid[F, Option[DBOrder]] = for {
      _   <- info"Going to get order with $txOutRef"
      res <- _
      _   <- info"Result of getting order with id is ${res.toString}"
    } yield res

    def updateExecutedSwapOrder(
      actualQuote: Long,
      userOutputId: String,
      poolInputId: String,
      poolOutputId: String,
      timestamp: Long,
      orderInputId: String
    ): Mid[F, Int] =
      info"Going to update swap order (${orderInputId}) status to executed" *> _

    def deleteExecutedSwapOrder(txOutRef: String): Mid[F, Int] =
      info"Going to update executed swap order (${txOutRef}) status to non-executed" *> _

    def updateExecutedDepositOrder(
      amountLq: Long,
      userOutputId: String,
      poolInputId: String,
      poolOutputId: String,
      timestamp: Long,
      orderInputId: String
    ): Mid[F, Int] =
      info"Going to update deposit order (${orderInputId}) status to executed" *> _

    def deleteExecutedDepositOrder(txOutRef: String): Mid[F, Int] =
      info"Going to update executed deposit order (${txOutRef}) status to non-executed" *> _

    def updateExecutedRedeemOrder(
      amountX: Long,
      amountY: Long,
      userOutputId: String,
      poolInputId: String,
      poolOutputId: String,
      timestamp: Long,
      orderInputId: String
    ): Mid[F, Int] =
      info"Going to update redeem order (${orderInputId}) status to executed" *> _

    def deleteExecutedRedeemOrder(txOutRef: String): Mid[F, Int] =
      info"Going to update executed redeem order (${txOutRef}) status to non-executed" *> _
  }
}
