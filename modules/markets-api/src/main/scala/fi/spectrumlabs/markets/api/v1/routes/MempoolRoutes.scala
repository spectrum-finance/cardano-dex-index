package fi.spectrumlabs.markets.api.v1.routes

import cats.effect.{Concurrent, ContextShift, Timer}
import fi.spectrumlabs.core.network.AdaptThrowable.AdaptThrowableEitherT
import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.services.{HistoryService, MempoolService}
import fi.spectrumlabs.markets.api.v1.endpoints.HistoryEndpoints
import fi.spectrumlabs.markets.api.v1.endpoints.MempoolEndpoints.orderMempoolE
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import fi.spectrumlabs.core.network.syntax._

class MempoolRoutes[F[_]: Concurrent: ContextShift: Timer: AdaptThrowableEitherT[*[_], HttpError]](implicit
  service: MempoolService[F],
  opts: Http4sServerOptions[F, F]
) {

  private val endpoints = HistoryEndpoints
  import endpoints._

  private val interpreter = Http4sServerInterpreter(opts)

  def routes = mempoolHistoryR

  def mempoolHistoryR: HttpRoutes[F] = interpreter.toRoutes(orderMempoolE) { query =>
    service.getUserOrders(query).adaptThrowable.value
  }
}

object MempoolRoutes {

  def make[F[_]: Concurrent: ContextShift: Timer](implicit
    service: MempoolService[F],
    opts: Http4sServerOptions[F, F]
  ): HttpRoutes[F] =
    new MempoolRoutes[F]().routes
}
