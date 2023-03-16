package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive

@derive(encoder, decoder)
final case class FullTxOutAddress(
  addressCredential: AddressCredential,
  addressStakingCredential: Option[AddressStakingCredential]
)

@derive(encoder, decoder)
final case class AddressCredential()

@derive(encoder, decoder)
final case class AddressStakingCredential()
