package fi.spectrumlabs.rates.resolver.gateways

import cats.{Functor, Monad}
import fi.spectrumlabs.core.models.domain.{AssetAmount, AssetClass}
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.rates.resolver.models.NetworkResponse
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.{basicRequest, SttpBackend, UriContext}
import sttp.model.Uri.Segment
import sttp.client3._
import sttp.client3.circe._
import tofu.syntax.monadic._
import tofu.syntax.logging._
import tofu.Throws
import tofu.logging.{Logging, Logs}
import fi.spectrumlabs.core.network.syntax._

trait NetworkClient[F[_]] {
  def getPrice(asset: AssetClass): F[ResolvedRate]
}

object NetworkClient {
  final val AdaId = 2010
  final val UdsId = 2781

  def create[I[_]: Functor, F[_]: Monad: Throws](
    implicit
    backend: SttpBackend[F, _],
    logs: Logs[I, F]
  ): I[NetworkClient[F]] =
    logs.forService[NetworkClient[F]].map(implicit __ => new Impl[F])

  final private class Impl[F[_]: Monad: Throws: Logging](implicit backend: SttpBackend[F, _]) extends NetworkClient[F] {

    def getPrice(asset: AssetClass): F[ResolvedRate] =
      basicRequest
        .header("X-CMC_PRO_API_KEY", "64a82f18-44d2-401f-80c8-d0f15d4ed1e9")
        .get(
          uri"https://pro-api.coinmarketcap.com/"
            .withPathSegment(Segment("v2/cryptocurrency/quotes/latest", identity))
            .addParams("id" -> s"$AdaId", "convert_id" -> s"$UdsId")
        )
        .response(asJson[NetworkResponse])
        .send(backend)
        .absorbError
        .flatTap(r => info"Rates response is: $r")
        .map { r =>
          ResolvedRate(
            asset,
            r.price
          )
        }

  }

}
