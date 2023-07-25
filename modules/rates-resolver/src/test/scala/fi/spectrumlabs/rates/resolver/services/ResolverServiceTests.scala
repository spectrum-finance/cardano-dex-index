package fi.spectrumlabs.rates.resolver.services

import cats.effect.SyncIO
import cats.~>
import fi.spectrumlabs.rates.resolver.config.ResolverConfig
import fi.spectrumlabs.rates.resolver.mocks.{PoolsMock, TokensMock}
import org.specs2.mutable.Specification
import tofu.logging.Logs

import scala.concurrent.duration.DurationInt

class ResolverServiceTests extends Specification {

  /**  pair x -> y = y / x
    *  pair y -> x = x / y
    */
  implicit val logsNoOp: Logs[SyncIO, SyncIO] = Logs.empty[SyncIO, SyncIO]
  val conf: ResolverConfig                    = ResolverConfig(10.seconds, 10)
  implicit val pools                          = PoolsMock.make[SyncIO]
  val xa: ~>[SyncIO, SyncIO]                  = λ[SyncIO ~> SyncIO](identity(_))
  implicit val tokens = TokensMock.create[SyncIO](pools.snapshots(10).unsafeRunSync().flatMap { p =>
    List(p.x.asset, p.y.asset)
  })

  "Resolver should" >> {
    "resolve tokens via ada" in {
      val resolver = ResolverService.create[SyncIO, SyncIO, SyncIO](conf, xa)

      val resolved = resolver.unsafeRunSync().resolve.unsafeRunSync()

      println(resolved)
      true shouldEqual true
    }
  }
}
