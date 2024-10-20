package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.Option
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.queryOf
import party.jml.partyboi.AppServices

class PropertyRepository(app: AppServices) {
    private val db = app.db

    init {
        db.init("""
           CREATE TABLE IF NOT EXISTS property (
                key text PRIMARY KEY,
                value jsonb NOT NULL
            ); 
        """)
    }

    fun set(key: String, value: String) = store(key, Json.encodeToString(value))
    fun set(key: String, value: Long) = store(key, Json.encodeToString(value))
    fun set(key: String, value: Boolean) = store(key, Json.encodeToString(value))

    fun get(key: String): Either<AppError, Option<PropertyRow>> =
        db.use {
            it.option(queryOf("SELECT * FROM property WHERE key = ?", key).map(PropertyRow.fromRow))
        }

    inline fun <reified A> getOrElse(key: String, value: A): Either<AppError, PropertyRow> =
        get(key).map { it.fold( { PropertyRow(key, Json.encodeToString(value)) }, { it }) }

    private fun store(key: String, jsonValue: String) =
        db.use {
            it.exec(queryOf("""
                    INSERT INTO property (key, value)
                    VALUES (?, ?::jsonb)
                    ON CONFLICT (key) DO UPDATE SET
                        value = EXCLUDED.value::jsonb
                """.trimIndent(),
                key,
                jsonValue
            ))
        }
}

data class PropertyRow(
    val key: String,
    val json: String,
) {
    fun string(): Either<AppError, String> = decode<String>()
    fun long(): Either<AppError, Long> = decode<Long>()
    fun boolean(): Either<AppError, Boolean> = decode<Boolean>()

    private inline fun <reified A> decode() =
        Either.catch { Json.decodeFromString<A>(json) }
            .mapLeft { InternalServerError(it.message ?: it.toString()) }

    companion object {
        val fromRow: (Row) -> PropertyRow = { row ->
            PropertyRow(
                key = row.string("key"),
                json = row.string("value"),
            )
        }
    }
}
