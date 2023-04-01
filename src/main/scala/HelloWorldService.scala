package org.jumalauta.partyboi

import cats.data.*
import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.jumalauta.partyboi.database.Database

class HelloWorldService(app: Application) extends Logging {
  val db: Database = app.db

  val routes =
    HttpRoutes.of[IO] {

      case GET -> Root / "hello" / name =>
        Ok(s"Hello, $name!")

      case GET -> Root =>
        Ok(db.testValues)

      case GET -> Root / "error" =>
        throw new Exception("Kaikki meni")

      case _ =>
        Ok("Hih", "Server" -> "Partyboi")

    }.orNotFound

}
