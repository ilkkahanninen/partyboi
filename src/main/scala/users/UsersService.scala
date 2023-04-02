package org.jumalauta.partyboi
package users

import database.Database

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder

class UsersService(db: Database) extends Logging {
  val users = new UsersRepository(db)

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "user" / username =>
      users.getUser(username).flatMap {
        case Some(user) => Ok(user)
        case None => NotFound()
      }
  }
}
