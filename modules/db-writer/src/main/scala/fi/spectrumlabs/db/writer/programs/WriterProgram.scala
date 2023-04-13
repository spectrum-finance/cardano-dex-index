package fi.spectrumlabs.db.writer.programs

import fi.spectrumlabs.db.writer.config.WriterConfig
import tofu.streams.{Broadcast, Compile, Emits, ParFlatten}
import tofu.syntax.streams.compile._
import tofu.syntax.streams.emits._
import tofu.syntax.streams.parFlatten._

trait WriterProgram[F[_]] {
  def run: F[Unit]
}

object WriterProgram {

  def create[S[_]: Compile[*[_], F]: Broadcast: Emits: ParFlatten, F[_]](
    handlers: HandlersBundle[S],
    config: WriterConfig
  ): WriterProgram[F] = new Impl[S, F](handlers, config)

  final private class Impl[S[_]: Compile[*[_], F]: Broadcast: Emits: ParFlatten, F[_]](
    handlers: HandlersBundle[S],
    config: WriterConfig
  ) extends WriterProgram[F] {

    def run: F[Unit] =
      emits(handlers.handlers.toList.map(_.handle)).parFlatten(config.maxConcurrent).drain
  }
}
