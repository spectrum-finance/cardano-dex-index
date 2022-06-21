package fi.spectrumlabs.rates.resolver.gateways

import cats.{Functor, Monad}
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.core.network.syntax._
import fi.spectrumlabs.rates.resolver.config.NetworkConfig
import fi.spectrumlabs.rates.resolver.models.NetworkResponse
import fi.spectrumlabs.rates.resolver.{AdaAssetClass, AdaCMCId, UsdCMCId}
import sttp.client3.circe._
import sttp.client3.{basicRequest, SttpBackend}
import sttp.model.Uri.Segment
import tofu.Throws
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._

trait Network[F[_]] {
  def getAdaPrice: F[ResolvedRate]
}

object Network {

  final val CmcApiKey = "X-CMC_PRO_API_KEY"

  def create[I[_]: Functor, F[_]: Monad: Throws](config: NetworkConfig)(
    implicit
    backend: SttpBackend[F, _],
    logs: Logs[I, F]
  ): I[Network[F]] =
    logs.forService[Network[F]].map(implicit __ => new Impl[F](config))

  final private class Impl[F[_]: Monad: Throws: Logging](config: NetworkConfig)(implicit backend: SttpBackend[F, _])
    extends Network[F] {

    def getAdaPrice: F[ResolvedRate] =
      info"Going to get next request to cmc for ada price. Req url is: ${config.cmcUrl.toString()}" >>
      basicRequest
        .header(CmcApiKey, config.cmcApiKey)
        .get(
          config.cmcUrl
            .withPathSegment(Segment("v2/cryptocurrency/quotes/latest", identity))
            .addParams("id" -> s"$AdaCMCId", "convert_id" -> s"$UsdCMCId")
        )
        .response(asJson[NetworkResponse])
        .send(backend)
        .absorbError
        .flatTap(price => info"Ada price from network is: $price")
        .map(r => ResolvedRate(AdaAssetClass, r.price))
  }
}
