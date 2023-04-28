package fi.spectrumlabs.db.writer

import cats.effect.{Blocker, Resource}
import fi.spectrumlabs.core.EnvApp
import fi.spectrumlabs.core.streaming.Consumer.Aux
import fi.spectrumlabs.core.streaming.config.{ConsumerConfig, KafkaConfig}
import fi.spectrumlabs.core.streaming.serde._
import fi.spectrumlabs.core.streaming.{Consumer, MakeKafkaConsumer}
import fi.spectrumlabs.db.writer.Handlers._
import fi.spectrumlabs.db.writer.config._
import fi.spectrumlabs.db.writer.classes.Handle
import fi.spectrumlabs.db.writer.config.{ConfigBundle, _}
import fi.spectrumlabs.db.writer.models._
import fi.spectrumlabs.db.writer.models.db.Pool
import fi.spectrumlabs.db.writer.models.streaming.TxEvent
import fi.spectrumlabs.db.writer.persistence.PersistBundle
import fi.spectrumlabs.db.writer.programs.{HandlersBundle, WriterProgram}
import fs2.kafka.RecordDeserializer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.fs2Instances._
import tofu.lift.{IsoK, Unlift}
import tofu.logging.Logs
import tofu.logging.derivation.loggable.generate
import zio.interop.catz._
import zio.{ExitCode, URIO, ZIO}
import fi.spectrumlabs.core.pg.doobieLogging
import fi.spectrumlabs.core.pg.PostgresTransactor
import fi.spectrumlabs.db.writer.models.cardano.{Action, Confirmed, Order, PoolEvent}
import fi.spectrumlabs.db.writer.repositories.{
  InputsRepository,
  OrdersRepository,
  OutputsRepository,
  PoolsRepository,
  TransactionRepository
}

object App extends EnvApp[AppContext] {

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    init(args.headOption).use(_ => ZIO.never).orDie

  def init(configPathOpt: Option[String]): Resource[InitF, Unit] =
    for {
      blocker <- Blocker[InitF]
      configs <- Resource.eval(ConfigBundle.load[InitF](configPathOpt, blocker))
      ctx                                = AppContext.init(configs)
      implicit0(ul: Unlift[RunF, InitF]) = Unlift.byIso(IsoK.byFunK(wr.runContextK(ctx))(wr.liftF))
      trans <- PostgresTransactor.make[InitF]("db-writer-pool", configs.pg)
      implicit0(xa: Txr.Continuational[RunF]) = Txr.continuational[RunF](trans.mapK(wr.liftF))
      implicit0(elh: EmbeddableLogHandler[xa.DB]) <- Resource.eval(
        doobieLogging.makeEmbeddableHandler[InitF, RunF, xa.DB](
          "db-writer-logging"
        )
      )
      implicit0(logsDb: Logs[InitF, xa.DB]) = Logs.sync[InitF, xa.DB]
      implicit0(txConsumer: Consumer[String, Option[TxEvent], StreamF, RunF]) = makeConsumer[String, Option[TxEvent]](
        configs.txConsumer,
        configs.kafka
      )
      implicit0(executedOpsConsumer: Consumer[String, Option[Order], StreamF, RunF]) =
        makeConsumer[
          String,
          Option[Order]
        ](configs.executedOpsConsumer, configs.kafka)
      implicit0(poolsConsumer: Consumer[String, Option[Confirmed[PoolEvent]], StreamF, RunF]) =
        makeConsumer[
          String,
          Option[Confirmed[PoolEvent]]
        ](configs.poolsConsumer, configs.kafka)
      implicit0(persistBundle: PersistBundle[RunF]) = PersistBundle.create[xa.DB, RunF]
      ordersRepo   <- Resource.eval(OrdersRepository.make[InitF, RunF, xa.DB])
      inputsRepo   <- Resource.eval(InputsRepository.make[InitF, RunF, xa.DB])
      outputsRepo  <- Resource.eval(OutputsRepository.make[InitF, RunF, xa.DB])
      poolsRepo    <- Resource.eval(PoolsRepository.make[InitF, RunF, xa.DB])
      txRepository <- Resource.eval(TransactionRepository.make[InitF, RunF, xa.DB])
      txHandler <- makeTxHandler(
        configs.writer,
        configs.cardanoConfig,
        ordersRepo,
        inputsRepo,
        outputsRepo,
        poolsRepo,
        txRepository
      )
      executedOpsHandler <- makeOrdersHandler(configs.writer, configs.cardanoConfig)
      poolsHandler       <- makePoolsHandler(configs.writer, configs.cardanoConfig)
      bundle  = HandlersBundle.make[StreamF](txHandler, List(poolsHandler, executedOpsHandler))
      program = WriterProgram.create[StreamF, RunF](bundle, configs.writer)
      r <- Resource.eval(program.run).mapK(ul.liftF)
    } yield r

  private def makeConsumer[K: RecordDeserializer[RunF, *], V: RecordDeserializer[RunF, *]](
    conf: ConsumerConfig,
    kafka: KafkaConfig
  ): Aux[K, V, (TopicPartition, OffsetAndMetadata), StreamF, RunF] = {
    implicit val maker = MakeKafkaConsumer.make[InitF, RunF, K, V](kafka)
    Consumer.make[StreamF, RunF, K, V](conf)
  }
}
