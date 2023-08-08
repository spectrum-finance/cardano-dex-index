package fi.spectrumlabs.db.writer.repositories

import cats.data.NonEmptyList
import cats.{Functor, Monad}
import derevo.derive
import doobie.ConnectionIO
import fi.spectrumlabs.db.writer.models.db.{AnyOrderDB, DBOrder}
import tofu.doobie.LiftConnectionIO
import tofu.doobie.transactor.Txr
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.monadic._
import cats.syntax.option._
import cats.tagless.syntax.functorK._
import fi.spectrumlabs.db.writer.classes.ExecutedOrderInfo.{
  ExecutedDepositOrderInfo,
  ExecutedRedeemOrderInfo,
  ExecutedSwapOrderInfo
}
import fi.spectrumlabs.db.writer.models.cardano.FullTxOutRef
import fi.spectrumlabs.db.writer.models.orders.TxOutRef
import tofu.syntax.logging._

@derive(representableK)
trait OrdersRepository[F[_]] {

  def getOrder(txOutRef: FullTxOutRef): F[Option[DBOrder]]

  def getUserOrdersByPkh(userPkh: String, refundOnly: Boolean, pendingOnly: Boolean): F[List[DBOrder]]

  def updateExecutedSwapOrder(swapOrderInfo: ExecutedSwapOrderInfo): F[Int]

  def refundSwapOrder(orderTxOutRef: TxOutRef, refundTxOutRef: TxOutRef, timestamp: Long): F[Int]

  def deleteExecutedSwapOrder(txOutRef: String): F[Int]

  def updateExecutedDepositOrder(depositOrderInfo: ExecutedDepositOrderInfo): F[Int]

  def refundDepositOrder(orderTxOutRef: TxOutRef, refundTxOutRef: TxOutRef, timestamp: Long): F[Int]

  def deleteExecutedDepositOrder(txOutRef: String): F[Int]

  def updateExecutedRedeemOrder(redeemOrderInfo: ExecutedRedeemOrderInfo): F[Int]

  def refundRedeemOrder(orderTxOutRef: TxOutRef, refundTxOutRef: TxOutRef, timestamp: Long): F[Int]

  def deleteExecutedRedeemOrder(txOutRef: String): F[Int]

  def getAnyOrder(pkh: List[String], offset: Int, limit: Int, exclude: List[String]): F[List[AnyOrderDB]]

  def addressCount(pkh: List[String]): F[Option[Long]]

  def pendingNeedRefundCount(pkh: List[String]): F[(Option[Long], Option[Long])]
}

object OrdersRepository {

  def make[I[_]: Functor, F[_]: Monad, DB[_]: LiftConnectionIO](implicit
    txr: Txr[F, DB],
    logs: Logs[I, F]
  ): I[OrdersRepository[F]] =
    logs.forService[OrdersRepository[F]].map { implicit logging =>
      new OrdersRepositoryTracingMid[F] attach new LiveCIO().mapK(LiftConnectionIO[DB].liftF andThen txr.trans)
    }

  def make[F[_]: Monad, DB[_]: LiftConnectionIO](implicit
    txr: Txr[F, DB],
    logs: Logging.Make[F]
  ): OrdersRepository[F] =
    logs.forService[OrdersRepository[F]].map { implicit logging =>
      new OrdersRepositoryTracingMid[F] attach new LiveCIO().mapK(LiftConnectionIO[DB].liftF andThen txr.trans)
    }

  final private class LiveCIO extends OrdersRepository[ConnectionIO] {

    import fi.spectrumlabs.db.writer.sql.OrdersSql._

    def getAnyOrder(pkh: List[String], offset: Int, limit: Int, exclude: List[String]): ConnectionIO[List[AnyOrderDB]] =
      NonEmptyList.fromList(pkh) match {
        case Some(value) => getAnyOrderDB(value, offset, limit, exclude).to[List]
        case None        => List.empty[AnyOrderDB].pure[ConnectionIO]
      }

    def addressCount(pkh: List[String]): ConnectionIO[Option[Long]] =
      NonEmptyList.fromList(pkh) match {
        case Some(value) => addressCountDB(value).option
        case None        => none[Long].pure[ConnectionIO]
      }

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

    override def getUserOrdersByPkh(
      userPkh: String,
      refundOnly: Boolean,
      pendingOnly: Boolean
    ): ConnectionIO[List[DBOrder]] = for {
      swapOrders    <- getUserSwapOrdersSQL(userPkh, refundOnly, pendingOnly).to[List]
      depositOrders <- getUserDepositOrdersSQL(userPkh, refundOnly, pendingOnly).to[List]
      redeemOrders  <- getUserRedeemOrdersSQL(userPkh, refundOnly, pendingOnly).to[List]
    } yield (swapOrders ++ depositOrders ++ redeemOrders)

    override def refundSwapOrder(
      orderTxOutRef: TxOutRef,
      refundTxOutRef: TxOutRef,
      timestamp: Long
    ): ConnectionIO[Int] =
      refundSwapOrderSQL(refundTxOutRef, timestamp, orderTxOutRef).run

    override def refundDepositOrder(
      orderTxOutRef: TxOutRef,
      refundTxOutRef: TxOutRef,
      timestamp: Long
    ): ConnectionIO[Int] =
      refundDepositOrderSQL(refundTxOutRef, timestamp, orderTxOutRef).run

    override def refundRedeemOrder(
      orderTxOutRef: TxOutRef,
      refundTxOutRef: TxOutRef,
      timestamp: Long
    ): ConnectionIO[Int] =
      refundRedeemOrderSQL(refundTxOutRef, timestamp, orderTxOutRef).run

    def pendingNeedRefundCount(pkh: List[String]): ConnectionIO[(Option[Long], Option[Long])] =
      NonEmptyList.fromList(pkh) match {
        case Some(value) =>
          for {
            register   <- registerAddressCount(value).option
            needRefund <- needRefundAddressCount(value).option
          } yield (register, needRefund)
        case None => (none[Long], none[Long]).pure[ConnectionIO]
      }

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

    override def getUserOrdersByPkh(userPkh: String, refundOnly: Boolean, pendingOnly: Boolean): Mid[F, List[DBOrder]] =
      info"Going to get order for pkh $userPkh from db. Refund only: $refundOnly, $pendingOnly" *> _

    override def refundSwapOrder(
      orderTxOutRef: TxOutRef,
      refundTxOutRef: TxOutRef,
      timestamp: Long
    ): Mid[F, Int] =
      info"Going to set swap order $orderTxOutRef from db to refund status" *> _

    override def refundDepositOrder(
      orderTxOutRef: TxOutRef,
      refundTxOutRef: TxOutRef,
      timestamp: Long
    ): Mid[F, Int] =
      info"Going to set deposit order $orderTxOutRef from db to refund status" *> _

    override def refundRedeemOrder(
      orderTxOutRef: TxOutRef,
      refundTxOutRef: TxOutRef,
      timestamp: Long
    ): Mid[F, Int] =
      info"Going to set redeem order $orderTxOutRef from db to refund status" *> _

    def getAnyOrder(pkh: List[String], offset: Int, limit: Int, exclude: List[String]): Mid[F, List[AnyOrderDB]] =
      info"Going to get orders for $pkh offset: $offset limit $limit" *> _

    def addressCount(pkh: List[String]): Mid[F, Option[Long]] =
      info"Going to get orders count for $pkh" *> _

    def pendingNeedRefundCount(pkh: List[String]): Mid[F, (Option[Long], Option[Long])] =
      for {
        _   <- trace"pendingNeedRefundCount($pkh) "
        res <- _
        _   <- trace"pendingNeedRefundCount($pkh) -> ${res.toString()}"
      } yield res
  }
}
