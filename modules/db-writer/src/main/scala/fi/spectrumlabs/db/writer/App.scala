package fi.spectrumlabs.db.writer

import cats.effect.{Blocker, Concurrent, ContextShift, ExitCode, Resource, Sync}
import dev.profunktor.redis4cats.connection.RedisClient
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import fi.spectrumlabs.core.network.makeBackend
import fi.spectrumlabs.core.pg.PostgresTransactor
import fi.spectrumlabs.core.pg.doobieLogging.makeEmbeddableHandler
import fi.spectrumlabs.core.redis.RedisConfig
import fi.spectrumlabs.core.streaming.Consumer.Aux
import fi.spectrumlabs.core.streaming.config.{ConsumerConfig, KafkaConfig}
import fi.spectrumlabs.core.streaming.serde._
import fi.spectrumlabs.core.streaming.{Consumer, MakeKafkaConsumer}
import fi.spectrumlabs.db.writer.Handlers._
import fi.spectrumlabs.db.writer.config._
import fi.spectrumlabs.db.writer.models.cardano.{Confirmed, Order, PoolEvent}
import fi.spectrumlabs.db.writer.models.streaming.TxEvent
import fi.spectrumlabs.db.writer.persistence.PersistBundle
import fi.spectrumlabs.db.writer.programs.{HandlersBundle, WriterProgram}
import fi.spectrumlabs.db.writer.redis.codecs.bytesCodec
import fi.spectrumlabs.db.writer.repositories._
import fi.spectrumlabs.db.writer.services.Tokens
import fs2.kafka.RecordDeserializer
import io.lettuce.core.{ClientOptions, TimeoutOptions}
import monix.eval.{Task, TaskApp}
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import sttp.client3.SttpBackend
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.fs2Instances._
import tofu.logging.Logging
import tofu.logging.derivation.loggable.generate

import scala.jdk.DurationConverters.ScalaDurationOps

object App extends TaskApp {

  type Stream[+A] = fs2.Stream[Task, A]

  def run(args: List[String]): Task[ExitCode] =
    init(args.headOption).use(_ => Task.never as ExitCode.Success)

  def init(configPathOpt: Option[String]): Resource[Task, Unit] =
    for {
      blocker <- Blocker[Task]
      configs <- Resource.eval(ConfigBundle.load[Task](configPathOpt, blocker))
      trans   <- PostgresTransactor.make[Task]("db-writer-pool", configs.pg)
      implicit0(xa: Txr.Continuational[Task])     = Txr.continuational[Task](trans)
      implicit0(logsF: Logging.Make[Task])        = Logging.Make.plain[Task]
      implicit0(elh: EmbeddableLogHandler[xa.DB]) = makeEmbeddableHandler[Task, xa.DB]("db-writer-logging")
      implicit0(txConsumer: Consumer[String, Option[TxEvent], Stream, Task]) = makeConsumer[String, Option[TxEvent]](
        configs.txConsumer,
        configs.kafka
      )
      implicit0(executedOpsConsumer: Consumer[String, Option[Order], Stream, Task]) =
        makeConsumer[
          String,
          Option[Order]
        ](configs.executedOpsConsumer, configs.kafka)
      mempoolOpsConsumer =
        makeConsumer[
          String,
          Option[Order]
        ](configs.mempoolOpsConsumer, configs.kafka)
      implicit0(poolsConsumer: Consumer[String, Option[Confirmed[PoolEvent]], Stream, Task]) =
        makeConsumer[
          String,
          Option[Confirmed[PoolEvent]]
        ](configs.poolsConsumer, configs.kafka)
      ordersRepo  = OrdersRepository.make[Task, xa.DB]
      inputsRepo  = InputsRepository.make[Task, xa.DB]
      outputsRepo = OutputsRepository.make[Task, xa.DB]
      poolsRepo   = PoolsRepository.make[Task, xa.DB]
      implicit0(redis: RedisCommands[Task, Array[Byte], Array[Byte]]) <-
        mkRedis[Array[Byte], Array[Byte], Task](configs.redisMempool)
      implicit0(persistBundle: PersistBundle[Task]) = PersistBundle.create[xa.DB, Task](configs.mempoolTtl)
      implicit0(sttp: SttpBackend[Task, Any]) <- makeBackend[Task](blocker)
      tokens                                  <- Resource.eval(Tokens.create[Task](configs.cardanoConfig))
      txHandler = makeTxHandler(
        configs.writer,
        configs.cardanoConfig,
        ordersRepo,
        inputsRepo,
        outputsRepo,
        poolsRepo
      )
      mempoolOpsHandler  = makeMempoolOrdersHandler(configs.writer, configs.cardanoConfig, mempoolOpsConsumer, tokens)
      executedOpsHandler = makeOrdersHandler(configs.writer, configs.cardanoConfig, configs.mempoolTtl, tokens)
      poolsHandler       = makePoolsHandler(configs.writer, configs.cardanoConfig, tokens)
      bundle             = HandlersBundle.make[Stream](txHandler, List(poolsHandler, executedOpsHandler, mempoolOpsHandler))
      program            = WriterProgram.create[Stream, Task](bundle, configs.writer)
      r <- Resource.eval(program.run)
    } yield r

  private def makeConsumer[K: RecordDeserializer[Task, *], V: RecordDeserializer[Task, *]](
    conf: ConsumerConfig,
    kafka: KafkaConfig
  ): Aux[K, V, (TopicPartition, OffsetAndMetadata), Stream, Task] = {
    implicit val maker = MakeKafkaConsumer.make[Task, Task, K, V](kafka)
    Consumer.make[Stream, Task, K, V](conf)
  }

  def mkRedis[K, V, F[_]: Concurrent: ContextShift](config: RedisConfig)(implicit
    codec: RedisCodec[K, V]
  ): Resource[F, RedisCommands[F, K, V]] = {
    import dev.profunktor.redis4cats.effect.Log.Stdout._
    for {
      timeoutOptions <- Resource.eval(
        Sync[F].delay(TimeoutOptions.builder().fixedTimeout(config.timeout.toJava).build())
      )
      clientOptions <- Resource.eval(Sync[F].delay(ClientOptions.builder().timeoutOptions(timeoutOptions).build()))
      client        <- RedisClient[F].withOptions(s"redis://${config.password}@${config.host}:${config.port}", clientOptions)
      redisCmd      <- Redis[F].fromClient(client, codec)
    } yield redisCmd
  }
}
