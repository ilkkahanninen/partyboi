package party.jml.partyboi.voting.admin

import arrow.core.raise.either
import arrow.core.right
import io.ktor.server.application.*
import io.ktor.server.routing.*
import party.jml.partyboi.AppServices
import party.jml.partyboi.auth.adminRouting
import party.jml.partyboi.data.parameterString
import party.jml.partyboi.data.processForm
import party.jml.partyboi.form.Form
import party.jml.partyboi.templates.Redirection
import party.jml.partyboi.templates.respondEither
import party.jml.partyboi.templates.respondPage
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun Application.configureAdminVotingRouting(app: AppServices) {
    adminRouting {
        get("/admin/voting") {
            call.respondEither({
                either {
                    val voteKeys = app.voteKeys.getAllVoteKeys().bind()
                    val users = app.users.getUsers().bind()
                    AdminVotingPage.render(voteKeys, users)
                }
            })
        }

        get("/admin/voting/generate") {
            val form = Form(
                GenerateVoteKeysPage.GenerateVoteKeySettings::class,
                GenerateVoteKeysPage.GenerateVoteKeySettings.Empty,
                initial = true,
            )
            call.respondPage(GenerateVoteKeysPage.renderForm(form))
        }

        post("/admin/voting/generate") {
            call.processForm<GenerateVoteKeysPage.GenerateVoteKeySettings>(
                {
                    either {
                        val keySet = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        app.voteKeys.createTickets(it.numberOfKeys, keySet).bind()
                        Redirection("/admin/voting/print/$keySet")
                    }
                },
                { GenerateVoteKeysPage.renderForm(it).right() }
            )
        }

        get("/admin/voting/print/{set}") {
            call.respondEither({
                either {
                    val keySet = call.parameterString("set").bind()
                    val tickets = app.voteKeys.getVoteKeySet(keySet).bind()
                    GenerateVoteKeysPage.renderTickets(tickets)
                }
            })
        }
    }
}