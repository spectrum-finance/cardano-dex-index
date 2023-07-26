package fi.spectrumlabs.markets.api.services

import cats.effect.Clock
import cats.{Apply, Functor, Monad}
import derevo.derive
import fi.spectrumlabs.db.writer.models.db.DBOrder
import fi.spectrumlabs.db.writer.repositories.OrdersRepository
import fi.spectrumlabs.markets.api.v1.endpoints.models.{HistoryApiQuery, Paging, TimeWindow}
import fi.spectrumlabs.markets.api.v1.models.{OrderHistoryResponse, PendingNeedRefundResponse, UserOrderInfo}
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

  def getUserHistoryV2(query: HistoryApiQuery, paging: Paging, window: TimeWindow): F[OrderHistoryResponse]

  def pendingNeedRefundCount(query: HistoryApiQuery): F[PendingNeedRefundResponse]
}

object HistoryService {

  def make[I[_]: Functor, F[_]: Monad: Clock](
    ordersRepository: OrdersRepository[F],
    mempoolService: MempoolService[F]
  )(implicit logs: Logs[I, F]): I[HistoryService[F]] =
    logs.forService[HistoryService[F]].map { implicit logging =>
      new HistoryServiceTracingMid[F] attach new Live[F](ordersRepository, mempoolService)
    }

  final private class Live[F[_]: Monad: Clock](ordersRepository: OrdersRepository[F], mempoolService: MempoolService[F])
    extends HistoryService[F] {

    def pendingNeedRefundCount(query: HistoryApiQuery): F[PendingNeedRefundResponse] =
      ordersRepository.pendingNeedRefundCount(query.userPkhs.distinct).map { res =>
        PendingNeedRefundResponse(res._2.getOrElse(0), res._1.getOrElse(0))
      }

    def getUserHistoryV2(query: HistoryApiQuery, paging: Paging, window: TimeWindow): F[OrderHistoryResponse] =
      mempoolService.getUserOrders(query).flatMap { exclude =>
        ordersRepository.getAnyOrder(query.userPkhs.distinct, paging.offset, paging.limit, exclude.map(_.id)).flatMap { orders =>
          ordersRepository.addressCount(query.userPkhs.distinct).flatMap { count =>
            Clock[F].realTime(SECONDS).map { curTime =>
              val res = orders.flatMap(x => UserOrderInfo.fromAnyOrderDB(x, curTime))
              OrderHistoryResponse(res, count.getOrElse(0))
            }
          }
        }
      }

    override def getUserHistory(query: HistoryApiQuery, paging: Paging, window: TimeWindow): F[OrderHistoryResponse] =
      query.userPkhs.distinct
        .flatTraverse(x =>
          ordersRepository.getUserOrdersByPkh(
            x,
            query.refundOnly.getOrElse(false),
            query.pendingOnly.getOrElse(false)
          )
        )
        .flatMap { orders =>
          Clock[F].realTime(SECONDS).map { curTime =>
            val finalOrders = orders
              .sortBy(_.creationTimestamp)(Ordering.Long.reverse)
              .flatMap(
                UserOrderInfo.fromDbOrder(
                  _,
                  curTime,
                  query.refundOnly.getOrElse(false),
                  false
                )
              )
            OrderHistoryResponse(finalOrders.distinct.take(paging.limit), finalOrders.distinct.length)
          }
        }
  }

  final private class HistoryServiceTracingMid[F[_]: Logging: Apply] extends HistoryService[Mid[F, *]] {
    override def getUserHistory(
      query: HistoryApiQuery,
      paging: Paging,
      window: TimeWindow
    ): Mid[F, OrderHistoryResponse] =
      trace"Going to get user history for pkhs: ${query.userPkhs.toString()}" *> _

    def getUserHistoryV2(query: HistoryApiQuery, paging: Paging, window: TimeWindow): Mid[F, OrderHistoryResponse] =
      trace"Going to get user history for pkhs: ${query.userPkhs.toString()}" *> _


    def pendingNeedRefundCount(query: HistoryApiQuery): Mid[F, PendingNeedRefundResponse] =
      trace"Going to get pendingNeedRefundCount for pkhs: ${query.userPkhs.toString()}" *> _

  }
}
