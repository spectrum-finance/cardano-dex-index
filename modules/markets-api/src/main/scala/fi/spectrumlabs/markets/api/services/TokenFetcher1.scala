package fi.spectrumlabs.markets.api.services

import cats.syntax.option._
import cats.{Functor, Monad}
import derevo.derive
import fi.spectrumlabs.core.models.domain.AssetClass
import fi.spectrumlabs.markets.api.configs.{TokenFetcherConfig1}
import fi.spectrumlabs.rates.resolver.gateways.Network
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
trait TokenFetcher1[F[_]] {
  def get: F[TokenList]
}

object TokenFetcher1 {

  def create[I[_]: Functor, F[_]: Monad: Throws](config: TokenFetcherConfig1)(implicit
    backend: SttpBackend[F, _],
    logs: Logs[I, F]
  ): I[TokenFetcher1[F]] =
    logs.forService[Network[F]].map(implicit __ => new Tracing[F] attach new Impl[F](config))

  final private class Impl[F[_]: Monad: Throws](config: TokenFetcherConfig1)(implicit backend: SttpBackend[F, _])
    extends TokenFetcher1[F] {

    def get: F[TokenList] =
      basicRequest
        .get(config.uri)
        .response(asJson[TokenList])
        .send(backend)
        .map {
          _.body match {
            case Left(_)      => TokenList(List.empty)
            case Right(value) => value.copy(value.tokens.filter(_.policyId.nonEmpty))
          }
        }
  }

  final private class Tracing[F[_]: Monad: Logging] extends TokenFetcher1[Mid[F, *]] {

    def get: Mid[F, TokenList] =
      for {
        _ <- trace"get"
        r <- _
        _ <- trace"get -> "
      } yield r
  }
}
