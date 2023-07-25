package fi.spectrumlabs.rates.resolver.mocks

import cats.Applicative
import fi.spectrumlabs.core.{AdaAssetClass, AdaDecimal}
import fi.spectrumlabs.core.models.rates.ResolvedRate
import fi.spectrumlabs.rates.resolver.gateways.Network
import cats.syntax.applicative._

object NetworkMock {

  def create[F[_]: Applicative](rate: BigDecimal): Network[F] = new Network[F] {

    def getAdaPrice: F[ResolvedRate] =
      ResolvedRate(AdaAssetClass, rate, AdaDecimal).pure
  }
}
