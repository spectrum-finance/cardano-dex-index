package fi.spectrumlabs.core.http.cache

import derevo.derive
import derevo.pureconfig.pureconfigReader
import tofu.logging.derivation.loggable

import scala.concurrent.duration.FiniteDuration

@derive(pureconfigReader, loggable)
final case class HttpCacheConfig(batchSize: Int, batchCommitTimeout: FiniteDuration)
