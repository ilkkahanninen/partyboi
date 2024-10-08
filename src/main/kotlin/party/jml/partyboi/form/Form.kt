package party.jml.partyboi.form

import arrow.core.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*
import kotlinx.html.InputType
import party.jml.partyboi.Config
import party.jml.partyboi.data.*
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters

class Form<T : Validateable<T>>(
    val kclass: KClass<T>,
    val data: T,
    val initial: Boolean,
    val accumulatedValidationErrors: List<ValidationError.Message> = emptyList(),
    val error: AppError? = null,
) {
    fun validated() = data.validate()

    val errors: List<ValidationError.Message> by lazy {
        if (initial) {
            accumulatedValidationErrors
        } else {
            data.validate().fold(
                { (accumulatedValidationErrors + it.errors).distinct() },
                { accumulatedValidationErrors }
            )
        }
    }

    fun forEach(block: (FieldData) -> Unit) {
        kclass.memberProperties
            .flatMap { prop ->
                val field = prop.findAnnotation<Field>()
                if (field == null) emptyList() else listOf(field to prop)
            }
            .sortedBy { it.first.order }
            .forEach { pair ->
                val (meta, prop) = pair
                val key = prop.name
                val value = prop.get(data)
                    .toOption()
                    .map { when (it) {
                        is String -> it
                        is Int -> it.toString()
                        else -> ""
                    } }
                    .getOrElse { "" }
                val error = errors
                    .filter { it.target == key }
                    .toNonEmptyListOrNone()
                    .map { it.joinToString { it.message } }
                block(FieldData(
                    label = meta.label,
                    key = key,
                    value = value,
                    error = error,
                    isFileInput = prop.returnType.toString() == "party.jml.partyboi.form.FileUpload",
                    type = meta.type,
                    large = meta.large,
                ))
            }
    }

    fun with(error: AppError): Form<T> {
        val errors = accumulatedValidationErrors + when(error) {
            is ValidationError -> error.errors
            else -> emptyList()
        }
        val uniqueErrors = errors.distinct()
        return Form(kclass, data, initial, uniqueErrors, if (error is ValidationError) null else error)
    }

    data class FieldData(
        val label: String,
        val key: String,
        val value: String,
        val error: Option<String>,
        val isFileInput: Boolean,
        val type: InputType,
        val large: Boolean,
    )

    companion object {
        suspend inline fun <reified T: Validateable<T>> fromParameters(parameters: MultiPartData): Either<AppError, Form<T>> {
            return try {
                val ctor = T::class.primaryConstructor ?: throw NotImplementedError("Primary constructor missing")

                var stringParams: MutableMap<String, String> = mutableMapOf()
                val fileParams: MutableMap<String, FileUpload> = mutableMapOf()

                parameters.forEachPart { part ->
                    val name = part.name ?: throw Error("Anonymous parameters not supported")
                    when (part) {
                        is PartData.FormItem -> {
                            stringParams[name] = part.value
                            part.dispose()
                        }
                        is PartData.FileItem -> {
                            fileParams[name] = FileUpload(
                                name = part.originalFileName ?: throw Error("File name missing for parameter '$name'"),
                                fileItem = part,
                            )
                        }
                        else -> part.dispose()
                    }
                }

                val args: List<Any> = ctor.valueParameters.map {
                    val name = it.name ?: throw Error("Anonymous parameters not supported")

                    val stringValue by lazy { stringParams.get(name) ?: "" }

                    when (it.type.toString()) {
                        "kotlin.String" -> {
                            stringValue
                        }
                        "kotlin.Int" -> {
                            try {
                                stringValue.toInt()
                            } catch (_: NumberFormatException) { -1 }
                        }
                        "kotlin.Boolean" -> {
                            try { stringValue.toBoolean() } catch (_: NumberFormatException) { false }
                        }
                        "party.jml.partyboi.form.FileUpload" -> {
                            fileParams.get(name) ?: throw Error("File parameter '$name' not found")
                        }
                        else -> {
                            throw error("Unsupported data type on property '$name': ${it.type}")
                        }
                    }
                }
                val data = ctor.call(*args.toTypedArray())
                Form(T::class, data, initial = false).right()
            } catch (e: Error) {
                FormError(e.message ?: e.toString()).left()
            }
        }
    }
}

annotation class Field(
    val order: Int = 0,
    val label: String = "",
    val type: InputType = InputType.text,
    val large: Boolean = false,
)

data class FileUpload(
    val name: String,
    val fileItem: PartData.FileItem,
) {
    fun write(storageFilename: Path): Either<AppError, Unit> {
        return try {
            val source = fileItem.streamProvider()
            val file = Config.getEntryDir().resolve(storageFilename).toFile()
            File(file.parent).mkdirs()
            file.outputStream().use { out ->
                while (true) {
                    val bytes = source.readNBytes(1024)
                    if (bytes.isEmpty()) break
                    out.write(bytes)
                }
                source.close()
            }
            fileItem.dispose()
            Unit.right()
        } catch (err: Error) {
            InternalServerError(err.message ?: err.toString()).left()
        }
    }

    val isDefined = name.isNotEmpty()

    companion object {
        val Empty = FileUpload("", PartData.FileItem(
            { throw Error("Empty file") },
            { },
            Headers.Empty
        ))
    }
}