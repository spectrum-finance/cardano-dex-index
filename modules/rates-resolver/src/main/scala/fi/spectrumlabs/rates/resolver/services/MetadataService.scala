package fi.spectrumlabs.rates.resolver.services

import cats.syntax.eq._
import cats.syntax.parallel._
import cats.{Functor, Monad, Parallel}
import fi.spectrumlabs.core.AdaAssetClass
import fi.spectrumlabs.core.models.domain.AssetClass
import fi.spectrumlabs.rates.resolver.gateways.Metadata
import fi.spectrumlabs.rates.resolver.models.{Metadata => Meta}
import fi.spectrumlabs.rates.resolver.AdaMetadata
import tofu.logging.Logs
import tofu.syntax.monadic._
import tofu.syntax.foption._

trait MetadataService[F[_]] {
  def getTokensMeta(tokens: List[AssetClass]): F[List[Meta]]
}

object MetadataService {

  def create[I[_]: Functor, F[_]: Monad: Parallel](implicit
    meta: Metadata[F],
    logs: Logs[I, F]
  ): I[MetadataService[F]] =
    logs.forService[MetadataService[F]].map(implicit __ => new Impl[F])

  final private class Impl[F[_]: Monad: Parallel](implicit meta: Metadata[F]) extends MetadataService[F] {

    def getTokensMeta(tokens: List[AssetClass]): F[List[Meta]] = {
      meta.get.map { list =>
        AdaMetadata :: list.tokens.filter { info =>
          val asset = AssetClass(info.policyId, info.tokenName)
          tokens.contains(asset)
        }.map { info =>
          Meta(info.decimals, AssetClass(info.policyId, info.tokenName))
        }
      }
    }
  }

}
