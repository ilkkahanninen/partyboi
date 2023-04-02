package org.jumalauta.partyboi
package users

import database.Database

import cats.effect.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*
import natchez.Trace.Implicits.noop

class UsersRepository(db: Database) {
  def getUser(username: String): IO[Option[User]] =
    db.session.use(_.prepare(selectUserByUsername).flatMap(_.option(username)))

  private val selectUserByUsername: Query[String, User] =
    sql"SELECT username, password FROM users WHERE username = $text"
      .query(text ~ text)
      .gmap[User]
}

case class User(
    username: String,
    password: String,
)