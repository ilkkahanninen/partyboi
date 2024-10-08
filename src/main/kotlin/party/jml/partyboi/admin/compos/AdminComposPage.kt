package party.jml.partyboi.admin.compos

import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.compos.NewCompo
import party.jml.partyboi.entries.Entry
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.form.switchLink
import party.jml.partyboi.templates.Page

object AdminComposPage {
    fun render(newCompoForm: Form<NewCompo>, compos: List<Compo>, entries: Map<Int, List<Entry>>) =
        Page("Compos") {
            form(method = FormMethod.post, action = "/admin/compos", encType = FormEncType.multipartFormData) {
                if (compos.isNotEmpty()) {
                    article {
                        header { +"Compos" }
                        table {
                            thead {
                                tr {
                                    th { +"Name" }
                                    th { +"Visible" }
                                    th { +"Submitting"}
                                    th { +"Voting" }
                                    th { +"Entries" }
                                }
                            }
                            tbody {
                                compos.forEach { compo ->
                                    tr {
                                        td { a(href = "/admin/compos/${compo.id}") { +compo.name } }
                                        td {
                                            switchLink(
                                                toggled = compo.visible,
                                                labelOn = "Visible",
                                                labelOff = "Hidden",
                                                urlPrefix = "/admin/compos/${compo.id}/setVisible",
                                            )
                                        }
                                        td {
                                            switchLink(
                                                toggled = compo.allowSubmit,
                                                labelOn = "Open",
                                                labelOff = "Closed",
                                                urlPrefix = "/admin/compos/${compo.id}/setSubmit",
                                                disable = compo.allowVote,
                                            )
                                        }
                                        td {
                                            switchLink(
                                                toggled = compo.allowVote,
                                                labelOn = "Open",
                                                labelOff = "Closed",
                                                urlPrefix = "/admin/compos/${compo.id}/setVoting",
                                                disable = compo.allowSubmit,
                                            )
                                        }
                                        td {
                                            val count = entries.get(compo.id)?.size ?: 0
                                            +count.toString()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                article {
                    header { +"Add new compo" }
                    fieldSet { renderForm(newCompoForm) }
                    footer { submitInput { value = "Add" } }
                }
            }
        }
}