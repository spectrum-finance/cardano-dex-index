package fi.spectrumlabs.rates.resolver.config

import scala.concurrent.duration.FiniteDuration

final case class ResolverConfig(throttleRate: FiniteDuration, minLiquidityValue: Long)
