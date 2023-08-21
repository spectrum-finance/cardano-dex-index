package fi.spectrumlabs.db.writer.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import fi.spectrumlabs.core.models.domain.AssetClass
import tofu.logging.derivation.loggable

import java.math.BigInteger
import scala.util.Try

@derive(encoder, decoder, loggable)
final case class TokenInfo(policyId: String, subject: String, decimals: Int) {
  def asset: AssetClass = AssetClass(policyId, tokenName)
  def tokenName: String = {
    val str = subject.drop(policyId.length)
    Try {
      val bytes = new BigInteger(str, 16).toByteArray
      new String(bytes)
    }.toOption.getOrElse("")
  }

  def token: String = s"$policyId.$tokenName"

}

object TokenInfo {
  val Ada = TokenInfo("", "", 6)
}