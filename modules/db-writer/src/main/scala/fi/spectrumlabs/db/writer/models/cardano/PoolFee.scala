package fi.spectrumlabs.db.writer.models.cardano

import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}

@derive(encoder)
final case class PoolFee(poolFeeDen: Long, poolFeeNum: Long)

object PoolFee {

  implicit val decoder: Decoder[PoolFee] = new Decoder[PoolFee] {

    override def apply(c: HCursor): Result[PoolFee] =
      for {
        poolFeeDen <- c.downField("poolFeeDen'").as[Long]
        poolFeeNum <- c.downField("poolFeeNum'").as[Long]
      } yield PoolFee(poolFeeDen, poolFeeNum)
  }
}
