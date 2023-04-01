package org.jumalauta.partyboi

import database.Migrations

import cats.data.Kleisli
import cats.effect.*
import cats.effect.IO.asyncForIO
import com.comcast.ip4s.*
import com.typesafe.config.ConfigFactory
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

import java.util.UUID

object App extends IOApp with Logging {
  val app = new Application

  override def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- Migrations.migrate(app.settings.db)
      server <-
        EmberServerBuilder
          .default[IO]
          .withHost(app.settings.server.host)
          .withPort(app.settings.server.port)
          .withHttpApp(app.httpApp)
          .withErrorHandler(errorHandler(_))
          .build
          .use(_ => IO.never)
          .as(ExitCode.Success)
    } yield server

  private def errorHandler(error: Throwable): IO[Response[IO]] = {
    val id = UUID.randomUUID().toString
    log.error(s"Unhandled error (id = $id)", error)
    Status.InternalServerError(InternalServerErrorResponse(id = Some(id)).asJson)
  }
}