package org.jumalauta.partyboi
package database

import cats.effect.*
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*
import natchez.Trace.Implicits.noop

class Database(settings: DatabaseSettings) {
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host      = settings.host,
      port      = settings.port,
      user      = settings.user,
      database  = settings.database,
      password  = Some(settings.password),
    )

  val query: Query[Void, Testi] =
    sql"SELECT * FROM test"
      .query(int4 ~ text)
      .gmap[Testi]

  val testValues: IO[List[Testi]] =
    session.use(_.execute(query))
}

case class Testi(
    id: Int,
    value: String,
)