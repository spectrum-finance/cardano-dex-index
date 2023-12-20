package fi.spectrumlabs.markets.api.services

import cats.{Monad, MonadError}
import derevo.derive
import fi.spectrumlabs.markets.api.configs.NetworkConfig
import fi.spectrumlabs.markets.api.models.CmcResponse
import sttp.client3.circe.asJson
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.Uri.Segment
import tofu.{Catches, Throws}
import tofu.higherKind.derived.representableK
import tofu.lift.Lift
import tofu.logging.Logs
import tofu.syntax.lift._
import tofu.syntax.monadic._
import tofu.syntax.handle._
import tofu.time.Clock
import cats.syntax.option._
@derive(representableK)
trait Network[F[_]] {
  def getAdaPriceCMC: F[Option[BigDecimal]]
}

object Network {

  final val AdaCMCId: 2010 = 2010
  final val UsdCMCId: 2781 = 2781

  def make[I[_]: Monad, F[_]: MonadError[*[_], Throwable]: Catches: Clock](network: NetworkConfig)(implicit
    backend: SttpBackend[F, _],
    lift: Lift[F, I],
    logs: Logs[I, F]
  ): I[Network[F]] =
    logs.forService[Network[F]].map { implicit __ =>
      new Live[F](network)
    }

  final private class Live[F[_]: Monad: Catches](config: NetworkConfig)(implicit backend: SttpBackend[F, _])
    extends Network[F] {

    private val CmcApiKey = "X-CMC_PRO_API_KEY"

    def getAdaPriceCMC: F[Option[BigDecimal]] =
      basicRequest
        .header(CmcApiKey, config.cmcApiKey)
        .get(
          config.cmcUrl
            .withPathSegment(Segment("v2/cryptocurrency/quotes/latest", identity))
            .addParams("id" -> s"$AdaCMCId", "convert_id" -> s"$UsdCMCId")
        )
        .response(asJson[CmcResponse])
        .send(backend)
        .map(_.body.toOption)
        .map(_.map(_.price))
        .handleWith { e: Throwable =>
          println("error: " + e)
            none[BigDecimal].pure[F]
        }
  }
}
