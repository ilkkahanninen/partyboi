package party.jml.partyboi.data

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.option
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.replication.DataExport
import kotlin.enums.enumEntries

class PropertyRepository(app: AppServices) : Logging() {
    private val db = app.db

    fun set(key: String, value: String) = store(key, Json.encodeToString(value))
    fun set(key: String, value: Long) = store(key, Json.encodeToString(value))
    fun set(key: String, value: Boolean) = store(key, Json.encodeToString(value))
    fun set(key: String, value: java.time.LocalDateTime) =
        store(key, Json.encodeToString(value.toKotlinLocalDateTime().format(LocalDateTime.Formats.ISO)))

    fun get(key: String): Either<AppError, Option<PropertyRow>> = db.use {
        it.option(queryOf("SELECT * FROM property WHERE key = ?", key).map(PropertyRow.fromRow))
    }

    fun getAll(): Either<AppError, List<PropertyRow>> = db.use {
        it.many(queryOf("SELECT * FROM property").map(PropertyRow.fromRow))
    }

    inline fun <reified A> getOrElse(key: String, value: A): Either<AppError, PropertyRow> =
        get(key).map { it.fold({ PropertyRow(key, Json.encodeToString(value)) }, { it }) }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.properties.size} properties")
        data.properties.map {
            tx.exec(
                queryOf(
                    "INSERT INTO property (key, value) VALUES (?, ?::jsonb)",
                    it.key,
                    it.json,
                )
            )
        }.bindAll()
    }

    private fun store(key: String, jsonValue: String) =
        db.use {
            it.exec(
                queryOf(
                    """
                    INSERT INTO property (key, value)
                    VALUES (?, ?::jsonb)
                    ON CONFLICT (key) DO UPDATE SET
                        value = EXCLUDED.value::jsonb
                """.trimIndent(),
                    key,
                    jsonValue
                )
            )
        }
}

@Serializable
data class PropertyRow(
    val key: String,
    val json: String,
) {
    fun string(): Either<AppError, String> = decode<String>()
    fun long(): Either<AppError, Long> = decode<Long>()
    fun boolean(): Either<AppError, Boolean> = decode<Boolean>()
    fun localDateTime(): Either<AppError, java.time.LocalDateTime> =
        string().map { LocalDateTime.parse(it).toJavaLocalDateTime() }

    private inline fun <reified A> decode() =
        Either.catch { Json.decodeFromString<A>(json) }
            .mapLeft { InternalServerError(it) }

    companion object {
        val fromRow: (Row) -> PropertyRow = { row ->
            PropertyRow(
                key = row.string("key"),
                json = row.string("value"),
            )
        }
    }
}

class StringProperty(val properties: PropertyRepository, val key: String, val defaultValue: String = "") {
    fun get(): Either<AppError, String> = either {
        properties.get(key).bind()
            .map { it.string().bind() }
            .getOrElse { defaultValue }
    }

    fun set(value: String): Either<AppError, Unit> =
        properties.set(key, value)
}

class MappedProperty<T>(
    val properties: PropertyRepository,
    val key: String,
    val defaultValue: T,
    val stringToValue: (String) -> T,
    val valueToString: (T) -> String,
) {
    companion object {
        inline fun <reified T : Enum<T>> enum(properties: PropertyRepository, key: String, defaultValue: T) =
            MappedProperty(
                properties,
                key,
                defaultValue,
                { s -> enumEntries<T>().first { it.name == s } },
                { it.name }
            )
    }

    fun get(): Either<AppError, T> = either {
        properties.get(key).bind()
            .map { stringToValue(it.string().bind()) }
            .getOrElse { defaultValue }
    }

    fun set(value: T): Either<AppError, Unit> =
        properties.set(key, valueToString(value))
}