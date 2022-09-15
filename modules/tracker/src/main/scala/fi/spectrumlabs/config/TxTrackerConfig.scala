package fi.spectrumlabs.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import tofu.logging.derivation.loggable
import scala.concurrent.duration.FiniteDuration

@derive(pureconfigReader, loggable)
final case class TxTrackerConfig(
  initialOffset: Long,
  limit: Int,
  batchSize: Int,
  timeout: FiniteDuration,
  throttleRate: FiniteDuration
)
