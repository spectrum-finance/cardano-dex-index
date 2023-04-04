package fi.spectrumlabs.markets.api.v1

import cats.{~>, Functor, Monad}
import cats.data.{Kleisli, OptionT}
import cats.effect.{Concurrent, ConcurrentEffect, ContextShift, Resource, Timer}
import fi.spectrumlabs.markets.api.configs.HttpConfig
import fi.spectrumlabs.markets.api.services.{AnalyticsService, HistoryService}
import fi.spectrumlabs.markets.api.v1.routes.{AnalyticsRoutes, HistoryRoutes, OpenApiRoutes}
import org.http4s.{Http, HttpApp, HttpRoutes}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.{Router, Server}
import org.http4s.server.middleware.CORS
import sttp.tapir.server.http4s.Http4sServerOptions
import tofu.higherKind.Embed
import tofu.lift.{IsoK, Unlift}
import tofu.syntax.monadic._
import cats.syntax.semigroupk._
import fi.spectrumlabs.core.http.cache.CacheMiddleware.CachingMiddleware

import scala.concurrent.ExecutionContext

object HttpServer {

  def imapHttpApp[F[_], G[_]: Functor](app: HttpApp[F])(fk: F ~> G)(gK: G ~> F): HttpApp[G] =
    Kleisli(req => app.mapK(fk).run(req.mapK(gK)).map(_.mapK(fk)))

  def translateHttpApp[F[_], G[_]: Functor](app: HttpApp[F])(implicit FG: IsoK[F, G]): HttpApp[G] =
    imapHttpApp(app)(FG.tof)(FG.fromF)

  def imapRoutes[F[_], G[_]: Functor](routes: HttpRoutes[F])(fk: F ~> G)(gK: G ~> F): HttpRoutes[G] =
    Kleisli(greq => routes.run(greq.mapK(gK)).mapK(fk).map(_.mapK(fk)))

  def translateRoutes[F[_], G[_]: Functor](routes: HttpRoutes[F])(implicit FG: IsoK[F, G]): HttpRoutes[G] =
    imapRoutes(routes)(FG.tof)(FG.fromF)

  def unliftRoutes[F[_], G[_]: Monad](routes: HttpRoutes[F])(implicit FG: Unlift[F, G]): HttpRoutes[G] =
    Embed.of[Http[*[_], G], OptionT[G, *]](OptionT.liftF(FG.subIso.map(implicit iso => translateRoutes[F, G](routes))))

  def make[
    I[_]: ConcurrentEffect: ContextShift: Timer,
    F[_]: Concurrent: ContextShift: Timer: Unlift[*[_], I]
  ](conf: HttpConfig, ec: ExecutionContext)(implicit
    analyticsService: AnalyticsService[F],
    opts: Http4sServerOptions[F, F],
    cache: CachingMiddleware[F],
    historyService: HistoryService[F]
  ): Resource[I, Server] = {
    val analyticsR = AnalyticsRoutes.make[F]
    val openApiR   = OpenApiRoutes.make[F]
    val historyR   = HistoryRoutes.make[F]
    val routes     = unliftRoutes[F, I](cache.middleware(analyticsR <+> openApiR <+> historyR))
    val corsRoutes = CORS.policy.withAllowOriginAll(routes)
    val api        = Router("/" -> corsRoutes).orNotFound
    BlazeServerBuilder[I](ec).bindHttp(conf.port, conf.host).withHttpApp(api).resource
  }
}
