package fi.spectrumlabs.rates.resolver.models

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class TokenList(tokens: List[TokenInfo])
