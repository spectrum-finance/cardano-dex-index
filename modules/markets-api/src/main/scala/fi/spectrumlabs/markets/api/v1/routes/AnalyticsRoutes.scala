package fi.spectrumlabs.markets.api.v1.routes

import cats.effect.{Concurrent, ContextShift, Timer}
import fi.spectrumlabs.core.network.AdaptThrowable.AdaptThrowableEitherT
import fi.spectrumlabs.markets.api.services.AnalyticsService
import org.http4s.HttpRoutes
import fi.spectrumlabs.core.network.syntax._
import fi.spectrumlabs.markets.api.v1.endpoints.PoolInfoEndpoints
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import fi.spectrumlabs.core.network.models._
import cats.syntax.semigroupk._

final class AnalyticsRoutes[F[_]: Concurrent: ContextShift: Timer: AdaptThrowableEitherT[*[_], HttpError]](implicit
  service: AnalyticsService[F],
  opts: Http4sServerOptions[F, F]
) {

  private val endpoints = PoolInfoEndpoints
  import endpoints._

  private val interpreter = Http4sServerInterpreter(opts)

  def routes = getPoolInfoR <+> getPoolsOverviewR

  def getPoolInfoR = interpreter.toRoutes(getPoolInfo) { case (id, period) =>
    service.getPoolInfo(id, period).orNotFound(s"PoolInfo{id=$id}")
  }

  def getPoolsOverviewR = interpreter.toRoutes(getPoolsOverview)(service.getPoolsOverview(_).adaptThrowable.value)
}

object AnalyticsRoutes {

  def make[F[_]: Concurrent: ContextShift: Timer](implicit
    service: AnalyticsService[F],
    opts: Http4sServerOptions[F, F]
  ): HttpRoutes[F] =
    new AnalyticsRoutes[F]().routes
}
