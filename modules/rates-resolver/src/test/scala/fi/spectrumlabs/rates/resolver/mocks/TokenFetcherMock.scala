package fi.spectrumlabs.rates.resolver.mocks

import cats.Applicative
import cats.syntax.applicative._
import fi.spectrumlabs.core.models.domain.AssetClass
import fi.spectrumlabs.rates.resolver.services.TokenFetcher

object TokenFetcherMock {

  def create[F[_]: Applicative](tokens: List[AssetClass]): TokenFetcher[F] = new TokenFetcher[F] {

    def fetchTokens: F[List[AssetClass]] = tokens.pure
  }
}
