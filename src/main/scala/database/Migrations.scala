package org.jumalauta.partyboi
package database

import cats.effect.Sync
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.Location
import org.flywaydb.core.api.configuration.FluentConfiguration

object Migrations extends Logging {
  private val migrationsLocation = "classpath:migrations"
  def migrate[F[_]: Sync](settings: DatabaseSettings): F[Int] =
    Sync[F].delay {
      log.info(s"Running migrations from classpath:migrations")
      val count = unsafeMigrate(settings)
      log.info(s"Executed $count migrations")
      count
    }

  private def unsafeMigrate(settings: DatabaseSettings): Int = {
    val m = Flyway.configure
      .dataSource(
        settings.url,
        settings.user,
        settings.password,
      )
      .group(true)
      .outOfOrder(false)
      .table("migration")
      .locations(new Location(migrationsLocation))
      .baselineOnMigrate(true)

    m.load().migrate().migrationsExecuted
  }
}
