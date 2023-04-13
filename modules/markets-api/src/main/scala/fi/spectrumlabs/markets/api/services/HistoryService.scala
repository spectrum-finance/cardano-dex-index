package fi.spectrumlabs.markets.api.services

import cats.effect.Clock
import cats.{Apply, Functor, Monad}
import derevo.derive
import fi.spectrumlabs.db.writer.models.db.DBOrder
import fi.spectrumlabs.db.writer.repositories.OrdersRepository
import fi.spectrumlabs.markets.api.v1.endpoints.models.{HistoryApiQuery, Paging, TimeWindow}
import fi.spectrumlabs.markets.api.v1.models.{OrderHistoryResponse, UserOrderInfo}
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import cats.syntax.traverse._
import tofu.logging.{Logging, Logs}
import tofu.syntax.monadic._
import tofu.syntax.logging._

import scala.concurrent.duration.SECONDS

@derive(representableK)
trait HistoryService[F[_]] {

  def getUserHistory(query: HistoryApiQuery, paging: Paging, window: TimeWindow): F[OrderHistoryResponse]
}

object HistoryService {

  def make[I[_]: Functor, F[_]: Monad: Clock](
    ordersRepository: OrdersRepository[F]
  )(implicit logs: Logs[I, F]): I[HistoryService[F]] =
    logs.forService[HistoryService[F]].map { implicit logging =>
      new HistoryServiceTracingMid[F] attach new Live[F](ordersRepository)
    }

  final private class Live[F[_]: Monad: Clock](ordersRepository: OrdersRepository[F]) extends HistoryService[F] {
    override def getUserHistory(query: HistoryApiQuery, paging: Paging, window: TimeWindow): F[OrderHistoryResponse] =
      query.userPkhs.flatTraverse(ordersRepository.getUserOrdersByPkh).flatMap { orders =>
        Clock[F].realTime(SECONDS).map { curTime =>
          val finalOrders = orders.flatMap(UserOrderInfo.fromDbOrder(_, curTime))
          OrderHistoryResponse(finalOrders.take(paging.limit), finalOrders.length)
        }
      }
  }

  final private class HistoryServiceTracingMid[F[_]: Logging: Apply] extends HistoryService[Mid[F, *]] {
    override def getUserHistory(
      query: HistoryApiQuery,
      paging: Paging,
      window: TimeWindow
    ): Mid[F, OrderHistoryResponse] =
      info"Going to get user history for pkhs: ${query.userPkhs.toString()}" *> _
  }
}
