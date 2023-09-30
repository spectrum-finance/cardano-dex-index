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
import tofu.syntax.monadic._

final class AnalyticsRoutes[F[_]: Concurrent: ContextShift: Timer: AdaptThrowableEitherT[*[_], HttpError]](implicit
  service: AnalyticsService[F],
  opts: Http4sServerOptions[F, F]
) {

  private val endpoints = PoolInfoEndpoints
  import endpoints._

  private val interpreter = Http4sServerInterpreter(opts)

  def routes =
    getPoolStateByDateR <+> getPoolListR <+> getPoolInfoR <+> getPoolsOverviewR <+> getPoolPriceChartR <+> getPlatformStatsR

  def getPoolInfoR: HttpRoutes[F] = interpreter.toRoutes(getPoolInfo) { case (id, period) =>
    service.getPoolInfo(id, period.toSeconds).orNotFound(s"PoolInfo{id=$id}")
  }

  def getPoolsOverviewR =
    interpreter.toRoutes(getPoolsOverview)(_ => service.getPoolsOverview.adaptThrowable.value)

  def getPoolPriceChartR = interpreter.toRoutes(getPoolPriceChart) { case (poolId, tw, res) =>
    service.getPoolPriceChart(poolId, tw, res).adaptThrowable.value
  }

  def getPlatformStatsR = interpreter.toRoutes(getPlatformStats) { _ =>
    service.getPlatformStats.adaptThrowable.value
  }

  def getPoolListR = interpreter.toRoutes(getPoolList) { _ =>
    service.getPoolList.adaptThrowable.value
  }

  def getPoolStateByDateR = interpreter.toRoutes(getPoolStateByDate) { case (id, date) =>
    service.getPoolStateByDate(id, date).orNotFound(s"PoolState{id=$id, date=$date}")
  }
}

object AnalyticsRoutes {

  def make[F[_]: Concurrent: ContextShift: Timer](implicit
    service: AnalyticsService[F],
    opts: Http4sServerOptions[F, F]
  ): HttpRoutes[F] =
    new AnalyticsRoutes[F]().routes
}
