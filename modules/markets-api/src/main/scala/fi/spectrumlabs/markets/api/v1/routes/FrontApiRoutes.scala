package fi.spectrumlabs.markets.api.v1.routes

import cats.effect.{Concurrent, ContextShift, Timer}
import fi.spectrumlabs.core.network.AdaptThrowable.AdaptThrowableEitherT
import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.core.network.syntax._
import fi.spectrumlabs.markets.api.services.AnalyticsService
import fi.spectrumlabs.markets.api.v1.endpoints.FrontApiEndpoints
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import tofu.syntax.monadic._

class FrontApiRoutes[F[_]: Concurrent: ContextShift: Timer: AdaptThrowableEitherT[*[_], HttpError]](implicit
  service: AnalyticsService[F],
  opts: Http4sServerOptions[F, F]
) {

  private val endpoints = FrontApiEndpoints
  import endpoints._

  private val interpreter = Http4sServerInterpreter(opts)

  def routes =
    frontApiPoolsR

  def frontApiPoolsR: HttpRoutes[F] =
    interpreter.toRoutes(poolsApiE)(_ => service.getLatestPoolsStates.map(_.map(_.toFront)).adaptThrowable.value)

}

object FrontApiRoutes {

  def make[F[_]: Concurrent: ContextShift: Timer](implicit
    service: AnalyticsService[F],
    opts: Http4sServerOptions[F, F]
  ): HttpRoutes[F] =
    new FrontApiRoutes[F]().routes
}
