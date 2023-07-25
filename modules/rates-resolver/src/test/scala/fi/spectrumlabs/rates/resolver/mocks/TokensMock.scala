package fi.spectrumlabs.rates.resolver.mocks

import cats.Applicative
import fi.spectrumlabs.core.models.domain.AssetClass
import fi.spectrumlabs.rates.resolver.gateways.Tokens
import fi.spectrumlabs.rates.resolver.models.TokenInfo

import scala.util.Random

object TokensMock {
  def create[F[_]: Applicative](tokens: List[AssetClass]): Tokens[F] = new Tokens[F] {
    def get: F[List[TokenInfo]] = Applicative[F].pure {
      tokens.map { ac =>
        TokenInfo(ac.currencySymbol, ac.currencySymbol + ac.tokenName, Random.nextInt(10))
      }
    }
  }
}
