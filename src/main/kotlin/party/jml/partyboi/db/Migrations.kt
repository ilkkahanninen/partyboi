package party.jml.partyboi.db

import arrow.core.Either
import arrow.core.left
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.flywaydb.core.api.output.ValidateOutput
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InternalServerError
import party.jml.partyboi.system.AppResult

object Migrations {
    suspend fun migrate(db: DatabasePool, schemaName: String = "public"): AppResult<MigrateResult> =
        withContext(Dispatchers.IO) {
            val config = Flyway.configure()
                .dataSource(db.dataSource)
                .schemas(schemaName)
                .group(true)
                .outOfOrder(false)
                .table("migrations")
                .locations("classpath:db/migrations")
                .validateMigrationNaming(true)
                .baselineOnMigrate(true)

            val validatedConfig = config
                .ignoreMigrationPatterns("*:pending")
                .load()
                .validateWithResult()

            if (validatedConfig.validationSuccessful) {
                Either.catch {
                    config.load().migrate()
                }.mapLeft { InternalServerError(it) }
            } else {
                MigrationError(validatedConfig.invalidMigrations).left()
            }
        }
}

class MigrationError(migrationErrors: List<ValidateOutput>) : AppError {
    override val message: String =
        "Migration failed: ${
            migrationErrors.joinToString(", ") {
                "(version=${it.version}, description=${it.description})"
            }
        }"
    override val statusCode: HttpStatusCode = HttpStatusCode.InternalServerError
    override val throwable: Throwable = Exception(message)
}