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
  .aggregate(core, dbWriter, api, ratesResolver)

lazy val core = project
  .in(file("modules/core"))
  .withId("cardano-markets-core")
  .settings(name := "cardano-markets-core")
  .settings(libraryDependencies ++= List(
    Libraries.derevoPureconfig,
    Libraries.derevoCirce,
    Libraries.tofuDoobie,
    Libraries.tofuZio,
    Libraries.tofuStreams,
    Libraries.tofuLogging,
    Libraries.tofuDerivation,
    Libraries.tofuFs2,
    Libraries.derevoCatsTagless,
    Libraries.tofuOpticsInterop,
    Libraries.kafka,
    Libraries.circeParse,
    Libraries.scalaland,
    Libraries.sttpCore,
    Libraries.sttpCirce,
    Libraries.sttpClientFs2,
    Libraries.sttpClientCE2,
    Libraries.tapirCirce,
    Libraries.tapirHttp4s,
    Libraries.redis4catsEffects,
    Libraries.doobiePg,
    Libraries.doobieHikari,
    Libraries.doobieCore,
    Libraries.pureconfig,
    Libraries.newtype
  ) ++ Libraries.Scodec)
  .settings(commonSettings)

lazy val dbWriter = project
  .in(file("modules/db-writer"))
  .withId("cardano-db-writer")
  .settings(name := "cardano-db-writer")
  .settings(commonSettings)
  .settings(libraryDependencies ++= List(
    Libraries.tofuZio,
    Libraries.kafka,
    Libraries.tofuFs2,
    Libraries.mouse,
    Libraries.enumeratum,
    Libraries.enumeratumCirce
  ))
  .dependsOn(core)
  .settings(assembly / assemblyJarName := "indexes-writer.jar")
  .enablePlugins(JavaAppPackaging, UniversalPlugin, DockerPlugin)

lazy val api = project
  .in(file("modules/markets-api"))
  .withId("cardano-markets-api")
  .settings(name := "cardano-markets-api")
  .settings(commonSettings)
  .settings(libraryDependencies ++= List(
    Libraries.doobiePg,
    Libraries.doobieHikari,
    Libraries.doobieCore,
    Libraries.tofuZio,
    Libraries.pureconfig,
    Libraries.tofuFs2,
    Libraries.mouse,
    Libraries.tapirRedoc,
    Libraries.tapirDocs,
    Libraries.tapirOpenApi,
    Libraries.http4sServer,
    Libraries.flyway,
    Libraries.scalaCheck
  ) ++ Libraries.testing)
  .dependsOn(core, ratesResolver, dbWriter)
  .settings(assembly / assemblyJarName := "markets-api.jar")
  .enablePlugins(JavaAppPackaging, UniversalPlugin, DockerPlugin)

lazy val ratesResolver = project
  .in(file("modules/rates-resolver"))
  .withId("cardano-rates-resolver")
  .settings(name := "cardano-rates-resolver")
  .settings(commonSettings)
  .settings(libraryDependencies ++= List(
    Libraries.doobiePg,
    Libraries.doobieHikari,
    Libraries.doobieCore,
    Libraries.tofuZio,
    Libraries.tofuFs2,
    Libraries.mouse,
    Libraries.specs2Core,
    Libraries.scalaCheck
  ))
  .dependsOn(core)
  .settings(assembly / assemblyJarName := "rates-resolver.jar")
  .enablePlugins(JavaAppPackaging, UniversalPlugin, DockerPlugin)