ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.2.2"

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

val http4sVersion = "0.23.18"
val circeVersion = "0.14.5"

lazy val root = (project in file("."))
  .settings(
    name := "partyboi",
    idePackagePrefix := Some("org.jumalauta.partyboi")
  )

libraryDependencies ++= Seq(
  // Configuration
  "com.typesafe" % "config"             % "1.4.2",
  // Logging
  "ch.qos.logback" % "logback-classic"  % "1.3.6",
  // Server framework
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl"          % http4sVersion,
  // JSON codec
  "org.http4s" %% "http4s-circe"        % http4sVersion,
  "io.circe" %% "circe-generic"         % circeVersion,
  "io.circe" %% "circe-literal"         % circeVersion,
  // Database
  "org.tpolecat" %% "skunk-core"        % "0.5.1",
  "org.flywaydb" % "flyway-core"        % "9.16.0",
  "org.postgresql" % "postgresql"       % "42.6.0",
)

Compile / mainClass := Some("org.jumalauta.App")
