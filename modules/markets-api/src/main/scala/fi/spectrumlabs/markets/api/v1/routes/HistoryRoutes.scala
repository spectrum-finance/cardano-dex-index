package fi.spectrumlabs.markets.api.v1.routes

import cats.effect.{Concurrent, ContextShift, Timer}
import cats.syntax.semigroupk._
import fi.spectrumlabs.core.network.AdaptThrowable.AdaptThrowableEitherT
import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.core.network.syntax._
import fi.spectrumlabs.markets.api.services.HistoryService
import fi.spectrumlabs.markets.api.v1.endpoints.HistoryEndpoints
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}

class HistoryRoutes[F[_]: Concurrent: ContextShift: Timer: AdaptThrowableEitherT[*[_], HttpError]](implicit
  service: HistoryService[F],
  opts: Http4sServerOptions[F, F]
) {

  private val endpoints = HistoryEndpoints
  import endpoints._

  private val interpreter = Http4sServerInterpreter(opts)

  def routes = orderHistoryR <+> orderHistoryV2R <+> historyPendingNeedRefundR

  def orderHistoryR: HttpRoutes[F] = interpreter.toRoutes(orderHistoryE) { case (paging, window, query) =>
    service.getUserHistory(query, paging, window).adaptThrowable.value
  }

  def orderHistoryV2R: HttpRoutes[F] = interpreter.toRoutes(orderHistoryV2E) { case (paging, window, query) =>
    service.getUserHistoryV2(query, paging, window).adaptThrowable.value
  }

  def historyPendingNeedRefundR: HttpRoutes[F] = interpreter.toRoutes(historyPendingNeedRefundE) { query =>
    service.pendingNeedRefundCount(query).adaptThrowable.value
  }


}

object HistoryRoutes {

  def make[F[_]: Concurrent: ContextShift: Timer](implicit
    service: HistoryService[F],
    opts: Http4sServerOptions[F, F]
  ): HttpRoutes[F] =
    new HistoryRoutes[F]().routes
}
