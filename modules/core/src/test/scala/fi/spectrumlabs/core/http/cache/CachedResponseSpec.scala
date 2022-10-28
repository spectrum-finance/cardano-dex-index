package fi.spectrumlabs.core.http.cache

import fi.spectrumlabs.core.http.cache.CachedResponse.{
  cachedResponseCodec,
  headersCodec,
  httpDateCodec,
  httpVersionCodec,
  methodCodec,
  statusCodec,
  uriCodec
}
import org.http4s.Header.Raw
import org.http4s._
import org.scalacheck.Gen
import org.typelevel.ci._
import scodec.CodecSuite
import scodec.bits.ByteVector

class CachedResponseSpec extends CodecSuite {

  def genStatus =
    for {
      code   <- Gen.choose(100, 500)
      reason <- Gen.listOfN(20, Gen.alphaChar).map(_.mkString)
    } yield Status(code, reason, true)

  def genHttpVersion =
    for {
      major <- Gen.choose(1, 9)
      minor <- Gen.choose(1, 9)
    } yield HttpVersion(major, minor)

  def genHeader =
    for {
      name  <- Gen.listOfN(20, Gen.alphaChar).map(_.mkString)
      value <- Gen.listOfN(20, Gen.alphaChar).map(_.mkString)
    } yield Raw(CIString(name), value)

  def genHeaders = Gen.listOfN(10, genHeader).map(Headers(_))

  def genHttpDate = Gen.choose(1000000L, 10000000000L).map(HttpDate.unsafeFromEpochSecond)

  val testUri = "http://localhost:8080/home"

  def genCachedResponse =
    for {
      status      <- genStatus
      httpVersion <- genHttpVersion
      headers     <- genHeaders
      body        <- Gen.alphaStr.map(_.getBytes).map(ByteVector(_))
    } yield CachedResponse(status, httpVersion, headers, body)

  genStatus.sample.foreach(status => roundtrip[Status](statusCodec, status))
  genHttpVersion.sample.foreach(hv => roundtrip[HttpVersion](httpVersionCodec, hv))
  genHeaders.sample.foreach(headers => roundtrip[Headers](headersCodec, headers))
  genHttpDate.sample.foreach(dt => roundtrip[HttpDate](httpDateCodec, dt))
  roundtrip[Method](methodCodec, Method.GET)
  roundtrip[Method](methodCodec, Method.POST)
  roundtrip[Uri](uriCodec, Uri.unsafeFromString(testUri))
  genCachedResponse.sample.foreach(cr => roundtrip[CachedResponse](cachedResponseCodec, cr))

}
