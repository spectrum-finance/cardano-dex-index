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
import fi.spectrumlabs.db.writer.classes.OrdersInfo.{
  ExecutedDepositOrderInfo,
  ExecutedRedeemOrderInfo,
  ExecutedSwapOrderInfo
}
import fi.spectrumlabs.db.writer.models.cardano.FullTxOutRef
import tofu.syntax.logging._

@derive(representableK)
trait OrdersRepository[F[_]] {

  def getOrder(txOutRef: FullTxOutRef): F[Option[DBOrder]]

  def getUserOrdersByPkh(userPkh: String): F[List[DBOrder]]

  def updateExecutedSwapOrder(swapOrderInfo: ExecutedSwapOrderInfo): F[Int]

  def deleteExecutedSwapOrder(txOutRef: String): F[Int]

  def updateExecutedDepositOrder(depositOrderInfo: ExecutedDepositOrderInfo): F[Int]

  def deleteExecutedDepositOrder(txOutRef: String): F[Int]

  def updateExecutedRedeemOrder(redeemOrderInfo: ExecutedRedeemOrderInfo): F[Int]

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

    override def getOrder(txOutRef: FullTxOutRef): ConnectionIO[Option[DBOrder]] = for {
      swapOpt    <- getSwapOrderSQL(txOutRef).option
      redeemOpt  <- getRedeemOrderSQL(txOutRef).option
      depositOpt <- getDepositOrderSQL(txOutRef).option
    } yield swapOpt.orElse(redeemOpt).orElse(depositOpt)

    override def updateExecutedSwapOrder(swapOrderInfo: ExecutedSwapOrderInfo): ConnectionIO[Int] =
      updateExecutedSwapOrderSQL(swapOrderInfo).run

    override def deleteExecutedSwapOrder(txOutRef: String): ConnectionIO[Int] =
      deleteExecutedSwapOrderSQL(txOutRef).run

    override def updateExecutedDepositOrder(depositOrderInfo: ExecutedDepositOrderInfo): ConnectionIO[Int] =
      updateExecutedDepositOrderSQL(depositOrderInfo).run

    override def deleteExecutedDepositOrder(txOutRef: String): ConnectionIO[Int] =
      deleteExecutedSwapOrderSQL(txOutRef).run

    override def updateExecutedRedeemOrder(redeemOrderInfo: ExecutedRedeemOrderInfo): ConnectionIO[Int] =
      updateExecutedRedeemOrderSQL(redeemOrderInfo).run

    override def deleteExecutedRedeemOrder(txOutRef: String): ConnectionIO[Int] =
      deleteExecutedRedeemOrderSQL(txOutRef).run

    override def getUserOrdersByPkh(userPkh: String): ConnectionIO[List[DBOrder]] = for {
      swapOrders <- getUserSwapOrdersSQL(userPkh).to[List]
      depositOrders <- getUserDepositOrdersSQL(userPkh).to[List]
      redeemOrders <- getUserRedeemOrdersSQL(userPkh).to[List]
    } yield (swapOrders ++ depositOrders ++ redeemOrders)
  }

  final private class OrdersRepositoryTracingMid[F[_]: Logging: Monad] extends OrdersRepository[Mid[F, *]] {

    def getOrder(txOutRef: FullTxOutRef): Mid[F, Option[DBOrder]] = for {
      _   <- info"Going to get order with ${txOutRef.toString}"
      res <- _
      _   <- info"Result of getting order with id is ${res.toString}"
    } yield res

    def updateExecutedSwapOrder(swapOrderInfo: ExecutedSwapOrderInfo): Mid[F, Int] =
      info"Going to update swap order (${swapOrderInfo.toString}) status to executed" *> _

    def deleteExecutedSwapOrder(txOutRef: String): Mid[F, Int] =
      info"Going to update executed swap order ($txOutRef) status to non-executed" *> _

    def updateExecutedDepositOrder(depositOrderInfo: ExecutedDepositOrderInfo): Mid[F, Int] =
      info"Going to update deposit order (${depositOrderInfo.toString}) status to executed" *> _

    def deleteExecutedDepositOrder(txOutRef: String): Mid[F, Int] =
      info"Going to update executed deposit order ($txOutRef) status to non-executed" *> _

    def updateExecutedRedeemOrder(redeemOrderInfo: ExecutedRedeemOrderInfo): Mid[F, Int] =
      info"Going to update redeem order (${redeemOrderInfo.toString}) status to executed" *> _

    def deleteExecutedRedeemOrder(txOutRef: String): Mid[F, Int] =
      info"Going to update executed redeem order ($txOutRef) status to non-executed" *> _

    override def getUserOrdersByPkh(userPkh: String): Mid[F, List[DBOrder]] =
      info"Going to get order for pkh $userPkh from db" *> _
  }
}
