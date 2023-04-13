package fi.spectrumlabs.core.http.cache

import cats.Monad
import cats.data.{Kleisli, OptionT}
import cats.effect.Sync
import fi.spectrumlabs.core.http.cache.types.RequestHash32
import org.http4s.HttpRoutes
import tofu.syntax.monadic.unit

object CacheMiddleware {

  def make[F[_]: Monad: Sync](implicit
    caching: HttpResponseCaching[F]
  ): CachingMiddleware[F] =
    new CachingMiddleware[F](caching)

  final class CachingMiddleware[F[_]: Monad: Sync](caching: HttpResponseCaching[F]) {

    def middleware(routes: HttpRoutes[F]): HttpRoutes[F] = Kleisli { req =>
      OptionT(caching.process(req)).orElse {
        for {
          resp        <- routes(req)
          requestHash <- OptionT.liftF(RequestHash32(req))
          _ <- OptionT.liftF {
            if (resp.status.isSuccess) caching.saveResponse(requestHash, resp)
            else unit
          }
        } yield resp
      }
    }
  }
}
