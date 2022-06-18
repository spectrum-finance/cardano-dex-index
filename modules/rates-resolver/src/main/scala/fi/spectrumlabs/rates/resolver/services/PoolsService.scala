package fi.spectrumlabs.rates.resolver.services

import fi.spectrumlabs.core.models.domain.Pool

trait PoolsService[F[_]] {
  def getAllLatest: F[List[Pool]]
}
