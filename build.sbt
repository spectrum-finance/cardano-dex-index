import Dependencies.{Libraries, _}
import sbt.Keys.organization

lazy val commonScalacOption = List(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Ymacro-annotations"
)

lazy val commonSettings = Seq(
  scalaVersion := "2.13.8",
  organization := "fi.spectrumlabs",
  version := "0.1.0",
  scalacOptions ++= commonScalacOption,
  libraryDependencies ++= List(CompilerPlugins.betterMonadicFor, CompilerPlugins.kindProjector),
  assembly / test := {},
  assembly / assemblyMergeStrategy  := {
    case "logback.xml"                                             => MergeStrategy.first
    case "module-info.class"                                       => MergeStrategy.discard
    case other if other.contains("scala/annotation/nowarn.class")  => MergeStrategy.first
    case other if other.contains("scala/annotation/nowarn$.class") => MergeStrategy.first
    case other if other.contains("io.netty.versions")              => MergeStrategy.first
    case other                                                     => (assemblyMergeStrategy in assembly).value(other)
  }
)

lazy val dexIndex = project
  .in(file("."))
  .withId("cardano-dex-index")
  .settings(idePackagePrefix := Some("fi.spectrumlabs"))
  .settings(commonSettings)
  .settings(name := "cardano-dex-index")
  .aggregate(core, tracker, dexAggregator, dbWriter, api, explorer)

lazy val explorer = project
  .in(file("modules/explorer"))
  .withId("explorer")
  .settings(name := "explorer")
  .settings(libraryDependencies ++= List(
    Libraries.derevoCirce,
    Libraries.tofuDerivation,
    Libraries.newtype,
    Libraries.tofuLogging,
    Libraries.tofuDoobie,
    Libraries.enumeratum
  ))
  .settings(commonSettings)

lazy val core = project
  .in(file("modules/core"))
  .withId("cardano-markets-core")
  .settings(name := "cardano-markets-core")
  .settings(libraryDependencies ++= List(
    Libraries.derevoCirce,
    Libraries.derevoPureconfig,
    Libraries.tofuDerivation,
    Libraries.tofuDoobie,
    Libraries.tofuLogging,
    Libraries.tofuZio,
    Libraries.tofuStreams,
    Libraries.tofuFs2,
    Libraries.newtype,
    Libraries.enumeratum,
    Libraries.kafka,
    Libraries.circeParse,
    Libraries.scalaland
  ))
  .dependsOn(explorer)
  .settings(commonSettings)

lazy val tracker = project
  .in(file("modules/tracker"))
  .withId("cardano-markets-tracker")
  .settings(name := "cardano-markets-tracker")
  .settings(commonSettings)
  .settings(libraryDependencies ++=  List(
    Libraries.sttpCore,
    Libraries.sttpCirce,
    Libraries.sttpClientFs2,
    Libraries.sttpClientCE2,
    Libraries.redis4catsEffects,
    Libraries.derevoCats,
    Libraries.derevoCatsTagless,
    Libraries.jawnFs2,
    Libraries.enumeratumCirce,
    Libraries.mouse,
    Libraries.pureconfig,
    Libraries.catsRetry
  ))
  .dependsOn(core)
  .settings(assembly / assemblyJarName := "tracker.jar")
  .enablePlugins(JavaAppPackaging, UniversalPlugin, DockerPlugin)

lazy val dexAggregator = project
  .in(file("modules/dex-aggregator"))
  .withId("cardano-dex-aggregator")
  .settings(name := "cardano-dex-aggregator")
  .settings(commonSettings)
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging, UniversalPlugin, DockerPlugin)

lazy val dbWriter = project
  .in(file("modules/db-writer"))
  .withId("cardano-db-writer")
  .settings(name := "cardano-db-writer")
  .settings(commonSettings)
  .settings(libraryDependencies ++= List(
    Libraries.doobiePg,
    Libraries.doobieHikari,
    Libraries.doobieCore,
    Libraries.derevoPureconfig,
    Libraries.tofuZio,
    Libraries.pureconfig,
    Libraries.kafka,
    Libraries.tofuFs2,
    Libraries.mouse
  ))
  .dependsOn(core)
  .settings(assembly / assemblyJarName := "indexes-writer.jar")
  .enablePlugins(JavaAppPackaging, UniversalPlugin, DockerPlugin)

lazy val api = project
  .in(file("modules/markets-api"))
  .withId("cardano-markets-api")
  .settings(name := "cardano-markets-api")
  .settings(commonSettings)
  .dependsOn(core)
  .enablePlugins(JavaAppPackaging, UniversalPlugin, DockerPlugin)
