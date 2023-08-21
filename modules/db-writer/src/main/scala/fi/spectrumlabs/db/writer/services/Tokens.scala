package fi.spectrumlabs.db.writer.services

import cats.Monad
import cats.effect.Sync
import cats.effect.concurrent.Ref
import fi.spectrumlabs.db.writer.config.CardanoConfig
import fi.spectrumlabs.db.writer.models.TokenInfo.Ada
import fi.spectrumlabs.db.writer.models.{TokenInfo, TokenList}
import sttp.client3.circe.asJson
import sttp.client3.{SttpBackend, basicRequest}
import tofu.Throws
import tofu.higherKind.{Mid, RepresentableK}
import tofu.logging.{Logging, Logs}
import tofu.syntax.logging._
import tofu.syntax.monadic._
import tofu.time.Clock

import java.util.concurrent.TimeUnit

trait Tokens[F[_]] {
  def get: F[List[TokenInfo]]
}

object Tokens {

  implicit def representableK: RepresentableK[Tokens] =
    tofu.higherKind.derived.genRepresentableK

  def create[F[_]: Sync: Throws: Clock](config: CardanoConfig)(implicit
    backend: SttpBackend[F, _],
    logging: Logging.Make[F]
  ): F[Tokens[F]] =
    for {
      cache <- Ref.of[F, (Long, List[TokenInfo])](0L -> List.empty)
      implicit0(logs: Logging[F]) = logging.forService[Tokens[F]]
    } yield new Tracing[F] attach new Impl[F](config, cache)

  final private class Impl[F[_]: Monad: Throws: Clock](config: CardanoConfig, cache: Ref[F, (Long, List[TokenInfo])])(
    implicit backend: SttpBackend[F, _]
  ) extends Tokens[F] {

    def get: F[List[TokenInfo]] = Clock[F].realTime(TimeUnit.SECONDS).flatMap { now =>
      cache.get.flatMap { case (ts, tokens) =>
        if (ts + config.tokensTtl.toSeconds < now)
          basicRequest
            .get(config.tokensUrl)
            .response(asJson[TokenList])
            .send(backend)
            .map {
              _.body match {
                case Left(s)      => TokenList(List.empty)
                case Right(value) => value.copy(value.tokens.filter(_.policyId.nonEmpty))
              }
            }
            .flatMap { info =>
              cache.set(now -> (Ada :: info.tokens)).as((Ada :: info.tokens))
            }
        else (Ada :: tokens).pure
      }
    }
  }

  final private class Tracing[F[_]: Monad: Logging] extends Tokens[Mid[F, *]] {

    def get: Mid[F, List[TokenInfo]] =
      for {
        _ <- trace"get"
        r <- _
        _ <- trace"get -> $r"
      } yield r
  }
}
