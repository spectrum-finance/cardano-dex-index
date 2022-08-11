package fi.spectrumlabs.markets.api.v1.routes

import cats.effect.{Concurrent, ContextShift, Timer}
import cats.syntax.applicative._
import cats.syntax.either._
import cats.syntax.option._
import cats.syntax.semigroupk._
import fi.spectrumlabs.core.network.models.HttpError
import fi.spectrumlabs.markets.api.v1.endpoints.{OpenApiEndpoints, PoolInfoEndpoints}
import org.http4s.HttpRoutes
import sttp.tapir.apispec.Tag
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.redoc.http4s.RedocHttp4s
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}

final class OpenApiRoutes[F[_]: Concurrent: ContextShift: Timer](implicit
  opts: Http4sServerOptions[F, F]
) {

  private val endpoints = OpenApiEndpoints
  import endpoints._

  private val interpreter = Http4sServerInterpreter(opts)

  val routes: HttpRoutes[F] = openApiSpecR <+> redocApiSpecR

  private def allEndpoints = PoolInfoEndpoints.endpoints

  private def tags =
    Tag(PoolInfoEndpoints.pathPrefix, "Cardano analytics Statistics".some) ::
    Nil

  private val docsAsYaml =
    OpenAPIDocsInterpreter()
      .toOpenAPI(allEndpoints, "ErgoDEX API v1", "1.0")
      .tags(tags)
      .toYaml

  private def openApiSpecR: HttpRoutes[F] =
    interpreter.toRoutes(apiSpecDef) { _ =>
      docsAsYaml
        .asRight[HttpError]
        .pure[F]
    }

  private def redocApiSpecR: HttpRoutes[F] =
    new RedocHttp4s(
      "Redoc",
      docsAsYaml,
      "openapi",
      contextPath = "v1" :: "docs" :: Nil
    ).routes
}

object OpenApiRoutes {

  def make[F[_]: Concurrent: ContextShift: Timer](implicit
    opts: Http4sServerOptions[F, F]
  ): HttpRoutes[F] =
    new OpenApiRoutes[F].routes
}
