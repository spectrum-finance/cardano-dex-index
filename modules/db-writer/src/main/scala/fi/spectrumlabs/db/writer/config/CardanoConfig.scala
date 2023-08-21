package fi.spectrumlabs.db.writer.config

import derevo.derive
import derevo.pureconfig.pureconfigReader
import sttp.model.Uri
import tofu.logging.derivation.loggable
import fi.spectrumlabs.core.network._

import scala.concurrent.duration.FiniteDuration

@derive(loggable, pureconfigReader)
final case class CardanoConfig(
  startTimeInSeconds: Long,
  tokensUrl: Uri,
  tokensTtl: FiniteDuration
)
