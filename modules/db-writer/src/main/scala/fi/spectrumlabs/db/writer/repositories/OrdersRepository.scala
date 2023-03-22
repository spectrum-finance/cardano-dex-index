package fi.spectrumlabs.db.writer.repositories

import doobie.ConnectionIO
import fi.spectrumlabs.db.writer.models.db.DBOrder
import tofu.doobie.LiftConnectionIO
import tofu.doobie.transactor.Txr

trait OrdersRepository[F[_]] {

  def getOrder(txOutRef: String): F[Option[DBOrder]]

  def updateExecutedSwapOrder(): F[Unit]

  def deleteExecutedSwapOrder(): F[Unit]

  def updateExecutedDepositOrder(): F[Unit]

  def deleteExecutedDepositOrder(): F[Unit]

  def updateExecutedRedeemOrder(): F[Unit]

  def deleteExecutedRedeemOrder(): F[Unit]
}

object OrdersRepository {

  def make[I[_], F[_], DB[_]: LiftConnectionIO](implicit txr: Txr[F, DB]): I[OrdersRepository[F]] = ???

  final private class LiveCIO() extends OrdersRepository[ConnectionIO] {

    override def getOrder(txOutRef: String): ConnectionIO[Option[DBOrder]] = ???

    override def updateExecutedSwapOrder(): ConnectionIO[Unit] = ???

    override def deleteExecutedSwapOrder(): ConnectionIO[Unit] = ???

    override def updateExecutedDepositOrder(): ConnectionIO[Unit] = ???

    override def deleteExecutedDepositOrder(): ConnectionIO[Unit] = ???

    override def updateExecutedRedeemOrder(): ConnectionIO[Unit] = ???

    override def deleteExecutedRedeemOrder(): ConnectionIO[Unit] = ???
  }
}
