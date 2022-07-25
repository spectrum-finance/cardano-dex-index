package fi.spectrumlabs.rates.resolver.services

import cats.Monad
import derevo.circe.magnolia.decoder
import derevo.derive
import fi.spectrumlabs.core.network.syntax.ResponseOps
import fi.spectrumlabs.rates.resolver.config.TokenFetcherConfig
import sttp.client3.circe.asJson
import sttp.client3.{SttpBackend, UriContext, basicRequest}
import tofu.Throws
import tofu.higherKind.derived.representableK
import tofu.syntax.embed._
import tofu.syntax.monadic._

@derive(representableK)
trait TokenFetcher[F[_]] {
  def fetchTokens: F[List[String]]
}

object TokenFetcher {

  val network = "cardano"

  @derive(decoder)
  final case class Token(network: String, address: String, decimals: Int, name: String, ticker: String)
  @derive(decoder)
  final case class TokenResponse(tokens: List[Token])

  def make[F[_]: Monad: Throws: TokenFetcherConfig.Has](implicit
    backend: SttpBackend[F, Any]
  ): TokenFetcher[F] =
    TokenFetcherConfig.access.map(conf => new ValidTokensFetcher[F](conf): TokenFetcher[F]).embed

  final class ValidTokensFetcher[F[_]: Monad: Throws](
    conf: TokenFetcherConfig
  )(implicit
    backend: SttpBackend[F, Any]
  ) extends TokenFetcher[F] {

    def fetchTokens: F[List[String]] =
      basicRequest
        .get(uri"${conf.url}")
        .response(asJson[TokenResponse])
        .send(backend)
        .absorbError
        .map(_.tokens.filter(_.network == network).map(_.address))
  }
}
