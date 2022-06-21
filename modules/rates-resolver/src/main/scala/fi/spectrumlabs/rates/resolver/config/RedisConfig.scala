package fi.spectrumlabs.rates.resolver.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import tofu.logging.derivation.loggable

import scala.concurrent.duration.FiniteDuration

@derive(pureconfigReader, loggable)
final case class RedisConfig(password: String, host: String, port: Int, timeout: FiniteDuration)