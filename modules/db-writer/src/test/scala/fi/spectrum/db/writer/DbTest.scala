package fi.spectrum.db.writer

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import doobie.util.transactor.Transactor
import org.testcontainers.utility.DockerImageName
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll, TestSuite}
import org.flywaydb.core.Flyway
import org.scalactic.source.Position
import doobie.implicits._
import doobie.util.log.LogHandler

trait DbTest extends CatsInstances with BeforeAndAfter with BeforeAndAfterAll {
  self: TestSuite =>

  implicit val lh: LogHandler = LogHandler.nop

  implicit lazy val xa: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      container.driverClassName,
      container.jdbcUrl,
      container.username,
      container.password
    )

  private lazy val container: PostgreSQLContainer =
    PostgreSQLContainer(DockerImageName.parse("postgres:11-alpine"), databaseName = "analytics", username = "postgres")

  private lazy val flyway = new Flyway()

  override def beforeAll(): Unit = {
    container.container.start()
    flyway.setSqlMigrationSeparator("__")
    flyway.setLocations("classpath:db")
    flyway.setDataSource(container.jdbcUrl, container.username, container.password)
    flyway.migrate()
  }

  override def afterAll(): Unit =
    container.container.stop()

  override def after(fun: => Any)(implicit pos: Position): Unit =
    truncateAll()

  private def truncateAll(): Unit =
    sql"""
         |truncate swap;
         |""".stripMargin.update.run.transact(xa).unsafeRunSync()
}