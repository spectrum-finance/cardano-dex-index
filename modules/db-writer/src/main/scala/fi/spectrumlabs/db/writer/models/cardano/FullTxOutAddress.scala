package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}

@derive(encoder, decoder)
final case class FullTxOutAddress(
  addressCredential: AddressCredential,
  addressStakingCredential: Option[AddressStakingCredential]
)

@derive(encoder)
sealed trait AddressCredential

object AddressCredential {

  implicit val decoder: Decoder[AddressCredential] = new Decoder[AddressCredential] {

    override def apply(c: HCursor): Result[AddressCredential] =
      c.downField("tag").as[String] flatMap {
        case "ScriptCredential" =>
          c.downField("contents").as[String].map(contents => ScriptAddressCredential(contents, "ScriptCredential"))
        case "PubKeyCredential" =>
          c.downField("contents")
            .as[PubKeyHash]
            .map(contents => PubKeyAddressCredential(contents, "PubKeyCredential"))
      }
  }
}

final case class ScriptAddressCredential(contents: String, tag: String) extends AddressCredential
final case class PubKeyAddressCredential(contents: PubKeyHash, tag: String) extends AddressCredential

@derive(encoder, decoder)
final case class PubKeyHash(getPubKeyHash: String)

@derive(encoder, decoder)
final case class StakePubKeyHash(unStakePubKeyHash: PubKeyHash)

@derive(encoder, decoder)
final case class AddressStakingCredential()
