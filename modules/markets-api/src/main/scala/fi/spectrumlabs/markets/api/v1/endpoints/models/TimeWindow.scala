package fi.spectrumlabs.markets.api.v1.endpoints.models

import cats.Monoid
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import cats.syntax.semigroup._
import sttp.tapir.Schema
import tofu.logging.derivation.loggable

@derive(encoder, decoder, loggable)
final case class TimeWindow(from: Option[Long], to: Option[Long])

object TimeWindow {

  val empty: TimeWindow = TimeWindow(None, None)

  implicit val monoid: Monoid[TimeWindow] =
    Monoid.instance(empty, (w0, w1) => w0.copy(w0.to |+| w1.to, w0.from |+| w1.from))

  implicit val schema: Schema[TimeWindow] = Schema.derived
}

