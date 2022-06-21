package fi.spectrumlabs.core

import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, Resource}
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import sttp.model.Uri
import tofu.WithRun
import tofu.logging.Loggable
import tofu.syntax.unlift.UnliftEffectOps
import cats.syntax.either._

package object network {

  def makeBackend[Ctx, I[_]: ConcurrentEffect, F[_]: Concurrent: ContextShift](
    ctx: Ctx,
    blocker: Blocker
  )(implicit wr: WithRun[F, I, Ctx]): Resource[I, SttpBackend[F, Fs2Streams[F]]] =
    Resource
      .eval(wr.concurrentEffect)
      .flatMap(implicit ce => AsyncHttpClientFs2Backend.resource[F](blocker))
      .mapK(wr.runContextK(ctx))

  implicit val uriConfigReader: ConfigReader[Uri] =
    ConfigReader.fromString(s => Uri.parse(s).leftMap(r => CannotConvert(s, "Uri", r)))

  implicit val uriLoggable: Loggable[Uri] = Loggable.stringValue.contramap(_.toString())
}
