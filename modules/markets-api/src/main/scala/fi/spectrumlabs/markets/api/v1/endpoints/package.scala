package fi.spectrumlabs.markets.api.v1

import fi.spectrumlabs.core.network.models.HttpError
import sttp.model.StatusCode
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{emptyOutputAs, endpoint, oneOf, oneOfDefaultMapping, oneOfMapping, Endpoint, EndpointInput}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import cats.syntax.option._
import io.circe.generic.auto._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}

package object endpoints {
  val V1Prefix: EndpointInput[Unit] = "v1"

  val baseEndpoint: Endpoint[Unit, HttpError, Unit, Any] =
    endpoint
      .in(V1Prefix)
      .errorOut(
        oneOf[HttpError](
          oneOfMapping(StatusCode.NotFound, jsonBody[HttpError.NotFound].description("not found")),
          oneOfMapping(StatusCode.NoContent, emptyOutputAs(HttpError.NoContent)),
          oneOfDefaultMapping(jsonBody[HttpError.Unknown].description("unknown"))
        )
      )

  implicit val codec: Codec.PlainCodec[FiniteDuration] = Codec.string
    .mapDecode(fromString)(_.toString)

  private def fromString(s: String): DecodeResult[FiniteDuration] =
    Option(FiniteDuration(Duration(s).toSeconds, SECONDS)) match {
      case Some(value) => DecodeResult.Value(value)
      case None        => DecodeResult.Mismatch("Expected correct ts string", s)
    }

  def after: EndpointInput[FiniteDuration] =
    query[Long]("after")
      .validate(Validator.min(0))
      .validate(Validator.max(Long.MaxValue))
      .map { input =>
        FiniteDuration(Duration(input, TimeUnit.SECONDS).toSeconds, SECONDS)
      }(_.toSeconds)
      .description("Unix Timestamp after which statistic is accumulated.")
      .example(FiniteDuration(Duration(1658379570, TimeUnit.SECONDS).toSeconds, SECONDS))
}
