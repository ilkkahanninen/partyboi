package party.jml.partyboi.screen

import arrow.core.*
import arrow.core.raise.either
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotliquery.Row
import kotliquery.TransactionalSession
import party.jml.partyboi.AppServices
import party.jml.partyboi.Logging
import party.jml.partyboi.data.AppError
import party.jml.partyboi.data.InvalidInput
import party.jml.partyboi.data.Numbers.positiveInt
import party.jml.partyboi.data.throwOnError
import party.jml.partyboi.db.*
import party.jml.partyboi.db.DbBasicMappers.asBoolean
import party.jml.partyboi.replication.DataExport
import party.jml.partyboi.screen.slides.ImageSlide
import party.jml.partyboi.screen.slides.QrCodeSlide
import party.jml.partyboi.screen.slides.ScheduleSlide
import party.jml.partyboi.screen.slides.TextSlide
import party.jml.partyboi.signals.Signal
import party.jml.partyboi.templates.NavItem

class ScreenRepository(private val app: AppServices) : Logging() {
    val db = app.db

    init {
        upsertSlideSet(SlideSetRow.ADHOC, "Ad hoc", "bolt").throwOnError()
        upsertSlideSet(SlideSetRow.DEFAULT, "Default", "circle-info").throwOnError()
    }

    fun upsertSlideSet(id: String, name: String, icon: String) = db.use {
        it.exec(
            queryOf(
                """
                INSERT INTO slideset (id, name, icon)
                    VALUES (?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name,
                        icon = EXCLUDED.icon
                """,
                id,
                name,
                icon,
            )
        )
    }

    fun getSlideSets(): Either<AppError, List<SlideSetRow>> = db.use {
        it.many(queryOf("SELECT * FROM slideset ORDER BY name").map(SlideSetRow.fromRow))
    }

    fun adHocExists(tx: TransactionalSession?) = db.use(tx) {
        it.one(queryOf("SELECT count(*) FROM screen WHERE slideset_id = ?", SlideSetRow.ADHOC).map(asBoolean))
    }

    fun getAdHoc(): Either<AppError, Option<ScreenRow>> = db.use {
        it.option(queryOf("SELECT * FROM screen WHERE slideset_id = ?", SlideSetRow.ADHOC).map(ScreenRow.fromRow))
    }

    fun getSlide(id: Int): Either<AppError, ScreenRow> = db.use {
        it.one(queryOf("SELECT * FROM screen WHERE id = ?", id).map(ScreenRow.fromRow))
    }

    fun getAllSlides(): Either<AppError, List<ScreenRow>> = db.use {
        it.many(queryOf("SELECT * FROM screen").map(ScreenRow.fromRow))
    }

    fun getSlideSetSlides(name: String): Either<AppError, List<ScreenRow>> = db.use {
        it.many(
            queryOf(
                "SELECT * FROM screen WHERE slideset_id = ? ORDER BY run_order, id",
                name
            ).map(ScreenRow.fromRow)
        )
    }

    fun setAdHoc(slide: Slide<*>): Either<AppError, ScreenRow> = db.transaction {
        either {
            val (type, content) = getTypeAndJson(slide)
            val query = if (adHocExists(it).bind()) {
                "UPDATE screen SET type = ?, content = ?::jsonb WHERE slideset_id = '${SlideSetRow.ADHOC}' RETURNING *"
            } else {
                "INSERT INTO screen(slideset_id, type, content) VALUES('adhoc', ?, ?::jsonb) RETURNING *"
            }
            it.one(queryOf(query, type, content).map(ScreenRow.fromRow)).bind()
        }
    }

    fun add(
        slideSet: String,
        slide: Slide<*>,
        makeVisible: Boolean,
        readOnly: Boolean,
        tx: TransactionalSession? = null
    ): Either<AppError, ScreenRow> = db.use(tx) {
        val (type, content) = getTypeAndJson(slide)
        it.one(
            queryOf(
                "INSERT INTO screen(slideset_id, type, content, visible, readonly) VALUES(?, ?, ?::jsonb, ?, ?) RETURNING *",
                slideSet,
                type,
                content,
                makeVisible,
                readOnly,
            ).map(ScreenRow.fromRow)
        )
    }

    fun update(id: Int, slide: Slide<*>): Either<AppError, ScreenRow> = db.use {
        val (type, content) = getTypeAndJson(slide)
        it.one(
            queryOf(
                "UPDATE screen SET type = ?, content = ?::jsonb WHERE id = ? AND NOT readonly RETURNING *",
                type,
                content,
                id
            ).map(ScreenRow.fromRow)
        )
    }

    fun delete(id: Int): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("DELETE FROM screen WHERE id = ?", id))
    }

    fun deleteAll(): Either<AppError, Unit> = db.use {
        it.exec(queryOf("DELETE FROM screen"))
    }

    fun replaceGeneratedSlideSet(slideSet: String, slides: List<Slide<*>>): Either<AppError, List<ScreenRow>> =
        db.transaction {
            either {
                val tx = it
                it.exec(queryOf("DELETE FROM screen WHERE slideset_id = ? AND readonly", slideSet)).bind()
                slides.map { slide -> add(slideSet, slide, makeVisible = true, readOnly = true, tx) }.bindAll()
            }
        }

    fun getFirstSlide(slideSet: String): Either<AppError, ScreenRow> = db.use {
        it.one(
            queryOf(
                "SELECT * FROM screen WHERE slideset_id = ? AND visible ORDER BY run_order, id LIMIT 1",
                slideSet
            ).map(ScreenRow.fromRow)
        )
    }

    fun getNext(slideSet: String, currentId: Int): Either<AppError, ScreenRow> = either {
        val screens = getSlideSetSlides(slideSet).bind()
        val index = positiveInt(screens.indexOfFirst { it.id == currentId })
            .toEither { InvalidInput("$currentId not in slide set '$slideSet'") }
            .bind()
        (screens.slice((index + 1)..<(screens.size)) + screens.slice(0..index))
            .filter { it.visible }
            .toNonEmptyListOrNone()
            .toEither { InvalidInput("No visible slides in slide set '$slideSet'") }
            .map { it.first() }
            .bind()
    }

    fun setVisible(id: Int, visible: Boolean): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("UPDATE screen SET visible = ? WHERE id = ?", visible, id))
    }

    fun showOnInfo(id: Int, visible: Boolean): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("UPDATE screen SET show_on_info = ? WHERE id = ?", visible, id))
    }

    fun setRunOrder(id: Int, order: Int): Either<AppError, Unit> = db.use {
        it.updateOne(queryOf("UPDATE screen SET run_order = ? WHERE id = ?", order, id))
    }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.slideSets.size} slide sets")
        data.slideSets.map {
            tx.exec(
                queryOf(
                    "INSERT INTO slideset (id, name, icon) VALUES (?, ?, ?)",
                    it.id,
                    it.name,
                    it.icon,
                )
            )
        }

        log.info("Import ${data.slides.size} slides")
        data.slides.map {
            tx.exec(
                queryOf(
                    "INSERT INTO screen (id, slideset_id, type, content, visible, run_order, show_on_info, readonly) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)",
                    it.id,
                    it.slideSet,
                    it.type,
                    it.content,
                    it.visible,
                    it.runOrder,
                    it.showOnInfoPage,
                    it.readOnly,
                )
            )
        }.bindAll()
    }

    private fun getTypeAndJson(slide: Slide<*>) = Pair(slide.javaClass.name, slide.toJson())
}

@Serializable
data class SlideSetRow(
    val id: String,
    val name: String,
    val icon: String,
) {
    fun toNavItem() = NavItem("/admin/screen/$id", name)

    companion object {
        val ADHOC = "adhoc"
        val DEFAULT = "default"

        val fromRow: (Row) -> SlideSetRow = { row ->
            SlideSetRow(
                id = row.string("id"),
                name = row.string("name"),
                icon = row.string("icon"),
            )
        }
    }
}

@Serializable
data class ScreenRow(
    val id: Int,
    val slideSet: String,
    val type: String,
    val content: String,
    val visible: Boolean,
    val runOrder: Int,
    val showOnInfoPage: Boolean,
    val readOnly: Boolean,
) {
    fun getSlide(): Slide<*> =
        when (type) {
            TextSlide::class.qualifiedName -> Json.decodeFromString<TextSlide>(content)
            QrCodeSlide::class.qualifiedName -> Json.decodeFromString<QrCodeSlide>(content)
            ImageSlide::class.qualifiedName -> Json.decodeFromString<ImageSlide>(content)
            ScheduleSlide::class.qualifiedName -> Json.decodeFromString<ScheduleSlide>(content)
            else -> TODO("JSON decoding not implemented for $type")
        }

    fun whenShown(): Signal = Signal.slideShown(id)

    companion object {
        val fromRow: (Row) -> ScreenRow = { row ->
            ScreenRow(
                id = row.int("id"),
                slideSet = row.string("slideset_id"),
                type = row.string("type"),
                content = row.string("content"),
                visible = row.boolean("visible"),
                runOrder = row.int("run_order"),
                showOnInfoPage = row.boolean("show_on_info"),
                readOnly = row.boolean("readonly"),
            )
        }
    }
}

enum class ScreenTheme(val displayName: String, val dir: String) {
    SIXTYEIGHTK_INSIDE("68k Inside", "68k_inside"),
    WIN95("Windows 95", "win95"),
    NOVEMBER_GAMES("November Games", "novembergames");

    companion object {
        fun getTheme(dir: String): Option<ScreenTheme> = try {
            Some(entries.first { t -> t.dir == dir })
        } catch (_: Throwable) {
            None
        }
    }
}