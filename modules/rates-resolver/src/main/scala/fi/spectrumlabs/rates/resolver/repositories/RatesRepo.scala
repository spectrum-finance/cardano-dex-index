package fi.spectrumlabs.rates.resolver.repositories

import fi.spectrumlabs.core.models.rates.ResolvedRate

trait RatesRepo[F[_]] {
  def put(rate: ResolvedRate): F[Unit]
}
