package fi.spectrumlabs.core.models.rates

import fi.spectrumlabs.core.models.domain.AssetClass

final case class ResolvedRate(asset: AssetClass, rate: BigDecimal)