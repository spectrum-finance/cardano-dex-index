package fi.spectrumlabs.markets.api.v1.routes

import cats.effect.{Concurrent, ContextShift, Timer}
import fi.spectrumlabs.core.network.AdaptThrowable.AdaptThrowableEitherT
import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.services.{AnalyticsService, HistoryService}
import fi.spectrumlabs.markets.api.v1.endpoints.{HistoryEndpoints, PoolInfoEndpoints}
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import fi.spectrumlabs.core.network.syntax._

class HistoryRoutes[F[_]: Concurrent: ContextShift: Timer: AdaptThrowableEitherT[*[_], HttpError]](implicit
  service: HistoryService[F],
  opts: Http4sServerOptions[F, F]
) {

  private val endpoints = HistoryEndpoints
  import endpoints._

  private val interpreter = Http4sServerInterpreter(opts)

  def routes = orderHistoryR

  def orderHistoryR: HttpRoutes[F] = interpreter.toRoutes(orderHistoryE) { case (paging, window, query) =>
    service.getUserHistory(query, paging, window).adaptThrowable.value
  }
}

object HistoryRoutes {

  def make[F[_]: Concurrent: ContextShift: Timer](implicit
    service: HistoryService[F],
    opts: Http4sServerOptions[F, F]
  ): HttpRoutes[F] =
    new HistoryRoutes[F]().routes
}
