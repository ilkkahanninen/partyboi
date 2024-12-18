package party.jml.partyboi.auth

import arrow.core.Either
import arrow.core.Option
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import kotliquery.Row
import kotliquery.TransactionalSession
import org.mindrot.jbcrypt.BCrypt
import party.jml.partyboi.AppServices
import party.jml.partyboi.Config
import party.jml.partyboi.Logging
import party.jml.partyboi.admin.settings.AutomaticVoteKeys
import party.jml.partyboi.data.*
import party.jml.partyboi.db.exec
import party.jml.partyboi.db.many
import party.jml.partyboi.db.one
import party.jml.partyboi.db.queryOf
import party.jml.partyboi.form.Field
import party.jml.partyboi.form.FieldPresentation
import party.jml.partyboi.replication.DataExport

class UserRepository(private val app: AppServices) : Logging() {
    private val db = app.db

    init {
        createAdminUser().throwOnError()
    }

    fun getUsers(): Either<AppError, List<User>> = db.use {
        it.many(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                """,
            ).map(User.fromRow)
        )
    }

    fun getUser(name: String): Either<AppError, User> = db.use {
        it.one(
            queryOf(
                """
                SELECT
                    id,
                    name,
                    password,
                    is_admin,
                    votekey.key is not null as voting_enabled
                FROM appuser
                LEFT JOIN votekey ON votekey.appuser_id = appuser.id
                WHERE name = ?
                """,
                name
            ).map(User.fromRow)
        )
    }

    fun addUser(user: NewUser, ip: String): Either<AppError, User> = db.use {
        either {
            val createdUser = it.one(
                queryOf(
                    "insert into appuser(name, password) values (?, ?) returning *, false as voting_enabled",
                    user.name,
                    user.hashedPassword(),
                ).map(User.fromRow)
            ).bind()

            when (app.settings.automaticVoteKeys.get().bind()) {
                AutomaticVoteKeys.PER_USER -> {
                    val votekey = "user:${createdUser.id}"
                    it.exec(queryOf("INSERT INTO votekey(key, appuser_id) values(?, ?)", votekey, createdUser.id))
                        .bind()
                    createdUser.copy(votingEnabled = true)
                }

                AutomaticVoteKeys.PER_IP_ADDRESS -> {
                    val votekey = "ip:$ip"
                    it.exec(queryOf("INSERT INTO votekey(key, appuser_id) values(?, ?)", votekey, createdUser.id)).fold(
                        { createdUser },
                        { createdUser.copy(votingEnabled = true) }
                    )
                }

                else -> {
                    createdUser
                }
            }
        }
    }

    fun import(tx: TransactionalSession, data: DataExport) = either {
        log.info("Import ${data.users.size} users")
        data.users.map {
            tx.exec(
                queryOf(
                    "INSERT INTO appuser (id, name, password, is_admin) VALUES (?, ?, ?, ?)",
                    it.id,
                    it.name,
                    it.hashedPassword,
                    it.isAdmin,
                )
            )
        }.bindAll()

        // TODO: Move to an own repository
        log.info("Import ${data.voteKeys.size} vote keys")
        data.voteKeys.map {
            tx.exec(
                queryOf(
                    "INSERT INTO votekey (key, appuser_id) VALUES (?, ?)",
                    it.key,
                    it.userId,
                )
            )
        }.bindAll()
    }

    private fun createAdminUser() = db.use {
        val password = Config.getAdminPassword()
        val admin = NewUser(Config.getAdminUserName(), password, password)
        it.exec(
            queryOf(
                "INSERT INTO appuser(name, password, is_admin) VALUES (?, ?, true) ON CONFLICT DO NOTHING",
                admin.name,
                admin.hashedPassword()
            )
        )
    }
}

@Serializable
data class User(
    val id: Int,
    val name: String,
    val hashedPassword: String,
    val isAdmin: Boolean,
    val votingEnabled: Boolean,
) : Principal {
    fun authenticate(plainPassword: String): Either<AppError, User> =
        if (BCrypt.checkpw(plainPassword, hashedPassword)) {
            this.right()
        } else {
            LoginError.left()
        }

    companion object {
        val fromRow: (Row) -> User = { row ->
            User(
                id = row.int("id"),
                name = row.string("name"),
                hashedPassword = row.string("password"),
                isAdmin = row.boolean("is_admin"),
                votingEnabled = row.boolean("voting_enabled"),
            )
        }

        val LoginError = ValidationError("name", "Invalid user name or password", "")
        val InvalidSessionError = RedirectInterruption("/login")
    }
}

data class NewUser(
    @property:Field(1, "User name")
    val name: String,
    @property:Field(2, "Password", presentation = FieldPresentation.secret)
    val password: String,
    @property:Field(3, "Password again", presentation = FieldPresentation.secret)
    val password2: String,
) : Validateable<NewUser> {
    override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
        expectNotEmpty("name", name),
        expectMaxLength("name", name, 64),
        expectMinLength("password", password, 8),
        expectMinLength("password2", password2, 8),
        expectEqual("password2", password2, password)
    )

    fun hashedPassword(): String = BCrypt.hashpw(password, BCrypt.gensalt())

    companion object {
        val Empty = NewUser("", "", "")
    }
}

