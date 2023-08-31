package fi.spectrum.db.writer

import cats.effect.{ContextShift, IO, Timer}
import doobie.util.ExecutionContexts
import tofu.logging.Logging
import tofu.logging.Logging.Make

trait CatsInstances {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContexts.synchronous)

  implicit val ioTimer: Timer[IO] = IO.timer(ExecutionContexts.synchronous)

  implicit val logs: Make[IO] = Logging.Make.plain[IO]
}

