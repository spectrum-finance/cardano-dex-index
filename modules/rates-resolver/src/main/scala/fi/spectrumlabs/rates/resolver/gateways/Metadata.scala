package fi.spectrumlabs.rates.resolver.gateways

import cats.syntax.option._
import cats.{Functor, Monad}
import derevo.derive
import fi.spectrumlabs.core.models.domain.AssetClass
import fi.spectrumlabs.rates.resolver.config.NetworkConfig
import fi.spectrumlabs.rates.resolver.models.{MetadataResponse, TokenList}
import org.http4s.implicits.http4sLiteralsSyntax
import sttp.client3.circe.asJson
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.Uri
import sttp.model.Uri.Segment
import tofu.Throws
import tofu.higherKind.Mid
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._

import scala.util.Left

@derive(representableK)
trait Metadata[F[_]] {
  def getTokenMeta(token: AssetClass): F[Option[MetadataResponse]]

  def get: F[TokenList]
}

object Metadata {

  def create[I[_]: Functor, F[_]: Monad: Throws](config: NetworkConfig)(implicit
    backend: SttpBackend[F, _],
    logs: Logs[I, F]
  ): I[Metadata[F]] =
    logs.forService[Network[F]].map(implicit __ => new Tracing[F] attach new Impl[F](config))

  final private class Impl[F[_]: Monad: Throws](config: NetworkConfig)(implicit backend: SttpBackend[F, _])
    extends Metadata[F] {

    def get: F[TokenList] = {
      basicRequest.get(config.metadataUrl)
        .response(asJson[TokenList])
        .send(backend)
        .map {
          _.body match {
            case Left(_) => TokenList(List.empty)
            case Right(value) => value.copy(value.tokens.filter(_.policyId.nonEmpty))
          }
        }
    }

    def getTokenMeta(token: AssetClass): F[Option[MetadataResponse]] =
      basicRequest
        .get(
          config.metadataUrl.withPathSegment(Segment(s"/cardano/metadata/${AssetClass.toMetadata(token)}", identity))
        )
        .response(asJson[MetadataResponse])
        .send(backend)
        .map {
          _.body match {
            case Left(_)      => none
            case Right(value) => value.some
          }
        }
  }

  final private class Tracing[F[_]: Monad: Logging] extends Metadata[Mid[F, *]] {

    def getTokenMeta(token: AssetClass): Mid[F, Option[MetadataResponse]] =
      for {
        _ <- trace"Going to get meta for token $token from metadata service"
        r <- _
        _ <- trace"Token meta for $token is $r"
      } yield r

    def get: Mid[F, TokenList] =
      for {
        _ <- trace"get"
        r <- _
        _ <- trace"get -> "
      } yield r
  }
}
