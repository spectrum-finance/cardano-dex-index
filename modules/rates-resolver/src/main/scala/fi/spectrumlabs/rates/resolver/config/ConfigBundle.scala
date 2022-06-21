package fi.spectrumlabs.rates.resolver.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import tofu.WithContext
import tofu.logging.derivation.loggable
import tofu.optics.macros.ClassyOptics

@ClassyOptics
@derive(loggable, pureconfigReader)
final case class ConfigBundle(
  resolverConfig: ResolverConfig,
  redisConfig: RedisConfig,
  pgConfig: PgConfig
)

object ConfigBundle extends WithContext.Companion[ConfigBundle] with ConfigBundleCompanion[ConfigBundle]
