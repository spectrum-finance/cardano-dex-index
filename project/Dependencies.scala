import sbt._

object Dependencies {

  object V {
    val tofu            = "0.10.6"
    val derevo          = "0.12.5"
    val catsEffect      = "2.5.3"
    val doobie          = "0.13.4"
    val pureconfig      = "0.14.1"
    val tapir           = "0.18.3"
    val newtype         = "0.4.3"
    val mouse           = "0.26.2"
    val enumeratum      = "1.7.0"
    val enumeratumCirce = "1.7.0"
    val sttpVersion     = "3.3.11"
    val redis           = "0.14.0"
    val jawnFs2Version  = "1.0.0"
    val fs2KafkaVersion = "1.4.1"
    val circeVersion    = "0.14.1"
    val scalaland       = "0.6.1"
    val http4s          = "0.22.11"
    val catsRetry       = "2.1.1"
    val specs2          = "4.16.0"
    val scalaCheck      = "1.16.0"
    val scodecCoreVersion = "1.11.7"
    val scodecBitsVersion = "1.1.21"
    val scodecCatsVersion = "1.1.0"

    val betterMonadicFor = "0.3.1"
    val kindProjector    = "0.13.2"
  }

  object Libraries {
    val tofuCore          = "tf.tofu" %% "tofu-core"           % V.tofu
    val tofuConcurrent    = "tf.tofu" %% "tofu-concurrent"     % V.tofu
    val tofuOptics        = "tf.tofu" %% "tofu-optics-macro"   % V.tofu
    val tofuOpticsInterop = "tf.tofu" %% "tofu-optics-interop" % V.tofu
    val tofuDerivation    = "tf.tofu" %% "tofu-derivation"     % V.tofu
    val tofuLogging       = "tf.tofu" %% "tofu-logging"        % V.tofu
    val tofuDoobie        = "tf.tofu" %% "tofu-doobie"         % V.tofu
    val tofuStreams       = "tf.tofu" %% "tofu-streams"        % V.tofu
    val tofuFs2           = "tf.tofu" %% "tofu-fs2-interop"    % V.tofu
    val tofuZio           = "tf.tofu" %% "tofu-zio-interop"    % V.tofu

    val circeParse = "io.circe" %% "circe-parser" % V.circeVersion

    val derevoCats        = "tf.tofu" %% "derevo-cats"              % V.derevo
    val derevoCatsTagless = "tf.tofu" %% "derevo-cats-tagless"      % V.derevo
    val derevoCirce       = "tf.tofu" %% "derevo-circe-magnolia"    % V.derevo
    val derevoPureconfig  = "tf.tofu" %% "derevo-pureconfig-legacy" % V.derevo

    val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect

    val catsRetry = "com.github.cb372" %% "cats-retry" % V.catsRetry

    val doobieCore   = "org.tpolecat" %% "doobie-core"     % V.doobie
    val doobiePg     = "org.tpolecat" %% "doobie-postgres" % V.doobie
    val doobieHikari = "org.tpolecat" %% "doobie-hikari"   % V.doobie

    val mouse      = "org.typelevel"         %% "mouse"                  % V.mouse
    val scalaland  = "io.scalaland"          %% "chimney"                % V.scalaland
    val pureconfig = "com.github.pureconfig" %% "pureconfig-cats-effect" % V.pureconfig

    val sttpCore      = "com.softwaremill.sttp.client3" %% "core"                               % V.sttpVersion
    val sttpCirce     = "com.softwaremill.sttp.client3" %% "circe"                              % V.sttpVersion
    val sttpClientFs2 = "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2-ce2"  % V.sttpVersion
    val sttpClientCE2 = "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats-ce2" % V.sttpVersion

    val tapirCore    = "com.softwaremill.sttp.tapir" %% "tapir-core"               % V.tapir
    val tapirCirce   = "com.softwaremill.sttp.tapir" %% "tapir-json-circe"         % V.tapir
    val tapirHttp4s  = "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"      % V.tapir
    val tapirDocs    = "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % V.tapir
    val tapirOpenApi = "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % V.tapir
    val tapirRedoc   = "com.softwaremill.sttp.tapir" %% "tapir-redoc-http4s"       % V.tapir

    val http4sServer = "org.http4s" %% "http4s-blaze-server" % V.http4s

    val newtype = "io.estatico" %% "newtype" % V.newtype

    val enumeratum      = "com.beachape" %% "enumeratum"       % V.enumeratum
    val enumeratumCirce = "com.beachape" %% "enumeratum-circe" % V.enumeratumCirce

    val redis4catsEffects = "dev.profunktor"  %% "redis4cats-effects" % V.redis
    val jawnFs2           = "org.http4s"      %% "jawn-fs2"           % V.jawnFs2Version
    val kafka             = "com.github.fd4s" %% "fs2-kafka"          % V.fs2KafkaVersion

    val specs2Core = "org.specs2"     %% "specs2-core" % V.specs2     % Test
    val scalaCheck = "org.scalacheck" %% "scalacheck"  % V.scalaCheck % Test

    val Scodec = List(
      "org.scodec" %% "scodec-core" % V.scodecCoreVersion,
      "org.scodec" %% "scodec-bits" % V.scodecBitsVersion,
      "org.scodec" %% "scodec-cats" % V.scodecCatsVersion
    )
  }

  object CompilerPlugins {

    val betterMonadicFor = compilerPlugin(
      "com.olegpy" %% "better-monadic-for" % V.betterMonadicFor
    )

    val kindProjector = compilerPlugin(
      "org.typelevel" % "kind-projector" % V.kindProjector cross CrossVersion.full
    )
  }
}
