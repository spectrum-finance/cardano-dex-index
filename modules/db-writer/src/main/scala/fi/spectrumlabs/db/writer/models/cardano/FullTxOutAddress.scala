package fi.spectrumlabs.db.writer.models.cardano

final case class FullTxOutAddress(
  addressCredential: AddressCredential,
  addressStakingCredential: Option[AddressStakingCredential]
)

final case class AddressCredential()

final case class AddressStakingCredential()
