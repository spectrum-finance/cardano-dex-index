package fi.spectrumlabs.db.writer.persistence

import cats.data.NonEmptyList
import cats.tagless.syntax.functorK._
import cats.{Applicative, FlatMap, Monad}
import doobie.ConnectionIO
import derevo.tagless.applyK
import dev.profunktor.redis4cats.RedisCommands
import doobie.util.Write
import doobie.util.log.LogHandler
import fi.spectrumlabs.db.writer.models.ExecutedInput
import fi.spectrumlabs.db.writer.schema.{ExecutedOrdersSchema, Schema}
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.higherKind.RepresentableK
import io.circe.parser._
import cats.syntax.traverse._
import fi.spectrumlabs.core.cache.Cache.Plain
import fi.spectrumlabs.db.writer.classes.Key
import io.circe.{Decoder, Encoder}
import tofu.logging.{Logging, Logs}
import tofu.syntax.monadic._
import tofu.syntax.logging._

import scala.concurrent.duration.FiniteDuration

/** Takes batch of T elements and persists them into indexes storage.
  */
trait Persist[T, F[_]] {
  def persist(inputs: NonEmptyList[T]): F[Int]
}

object Persist {

  implicit def repK[T]: RepresentableK[Persist[T, *[_]]] = {
    type Repr[F[_]] = Persist[T, F]
    tofu.higherKind.derived.genRepresentableK[Repr]
  }

  def create[T: Write, D[_]: FlatMap: LiftConnectionIO, F[_]: Applicative](schema: Schema[T])(implicit
    elh: EmbeddableLogHandler[D],
    txr: Txr[F, D]
  ): Persist[T, F] =
    elh.embed(implicit __ => new Impl[T](schema).mapK(LiftConnectionIO[D].liftF)).mapK(txr.trans)

  def createRedis[T: Encoder: Key: Decoder, F[_]: Monad: Logging](mempoolTtl: FiniteDuration)(implicit
    redis: Plain[F]
  ): Persist[T, F] =
    new ImplRedis[T, F](mempoolTtl)

  final private class ImplRedis[T: Encoder: Decoder: Key, F[_]: Monad: Logging](mempoolTtl: FiniteDuration)(implicit
    redis: Plain[F]
  ) extends Persist[T, F] {

    def persist(inputs: NonEmptyList[T]): F[Int] =
      inputs
        .traverse { toInsert =>
          val key = implicitly[Key[T]].getKey(toInsert)
          redis.get(key.getBytes).flatMap {
            case Some(previousOrdersRaw) =>
              val previousOrders = parse(new String(previousOrdersRaw)).flatMap(Decoder[List[T]].decodeJson(_))
              info"Got mempool for $key ${previousOrders.toString}" >>
              (previousOrders match {
                case Left(_) => ().pure[F]
                case Right(value) =>
                  val filtered = value.filter { x =>
                    implicitly[Key[T]].getExtendedKey(x) != implicitly[Key[T]].getExtendedKey(toInsert)
                  }
                  redis.setEx(
                    implicitly[Key[T]].getKey(toInsert).getBytes,
                    Encoder[List[T]].apply(filtered :+ toInsert).toString().getBytes(),
                    mempoolTtl
                  )
              })
            case None =>
              info"Got empty mempool for $key" >>
                redis.setEx(
                  implicitly[Key[T]].getKey(toInsert).getBytes,
                  Encoder[List[T]].apply(List(toInsert)).toString().getBytes(),
                  mempoolTtl
                )
          }
        }
        .map(_.length)
  }

  final private class Impl[T: Write](schema: Schema[T])(implicit
    lh: LogHandler
  ) extends Persist[T, ConnectionIO] {

    def persist(inputs: NonEmptyList[T]): ConnectionIO[Int] =
      schema.insertNoConflict.updateMany(inputs)
  }

  def createForExecuted[D[_]: FlatMap: LiftConnectionIO, F[_]: Applicative](schema: ExecutedOrdersSchema)(implicit
    elh: EmbeddableLogHandler[D],
    txr: Txr[F, D]
  ) = elh.embed(implicit __ => new Executed(schema).mapK(LiftConnectionIO[D].liftF)).mapK(txr.trans)

  final private class Executed(executedOrdersSchema: ExecutedOrdersSchema)(implicit
    lh: LogHandler
  ) extends Persist[ExecutedInput, ConnectionIO] {

    override def persist(inputs: NonEmptyList[ExecutedInput]): ConnectionIO[Int] =
      inputs.traverse(executedOrdersSchema.updateExecuted).map(_.toList.flatten.sum)
  }
}
