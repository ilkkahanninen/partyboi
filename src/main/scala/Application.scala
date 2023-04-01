package org.jumalauta.partyboi

import database.{Database, Migrations}

import cats.effect.IO
import org.http4s.HttpApp
import org.http4s.server.middleware.Logger

class Application {
  val settings: Settings = Settings.load

  lazy val db: Database = new Database(settings.db)
  lazy val helloWorld = new HelloWorldService(this)

  lazy val httpApp: HttpApp[IO] = Logger.httpApp(
    logHeaders = true,
    logBody = true,
  )(
    helloWorld.routes
  )
}
