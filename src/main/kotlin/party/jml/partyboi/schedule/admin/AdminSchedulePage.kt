package party.jml.partyboi.schedule.admin

import kotlinx.datetime.TimeZone
import kotlinx.html.*
import party.jml.partyboi.compos.Compo
import party.jml.partyboi.form.Form
import party.jml.partyboi.form.renderForm
import party.jml.partyboi.schedule.Event
import party.jml.partyboi.schedule.NewEvent
import party.jml.partyboi.system.displayDate
import party.jml.partyboi.system.toDate
import party.jml.partyboi.templates.Page
import party.jml.partyboi.templates.components.*
import party.jml.partyboi.triggers.FailedTriggerRow
import party.jml.partyboi.triggers.NewScheduledTrigger
import party.jml.partyboi.triggers.SuccessfulTriggerRow
import party.jml.partyboi.triggers.TriggerRow

object AdminSchedulePage {
    fun render(
        newEventForm: Form<NewEvent>,
        events: List<Event>,
        timeZone: TimeZone
    ) =
        Page("Schedule") {
            h1 { +"Schedule" }

            columns(
                if (events.isNotEmpty()) {
                    {
                        events
                            .groupBy { it.startTime.toDate() }
                            .forEach { (date, events) ->
                                article {
                                    header { +date.displayDate() }
                                    table {
                                        thead {
                                            tr {
                                                th(classes = "narrow") { +"Time" }
                                                th { +"Name" }
                                                th(classes = "narrow") {}
                                            }
                                        }
                                        tbody {
                                            events.forEach { event ->
                                                tr {
                                                    td { +event.formatTime(timeZone) }
                                                    td { a(href = "/admin/schedule/events/${event.id}") { +event.name } }
                                                    td(classes = "settings") {
                                                        deleteButton(
                                                            url = "/admin/schedule/events/${event.id}",
                                                            tooltipText = "Delete event",
                                                            confirmation = "Are you sure you want to delete event '${event.name}'?"
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                } else null
            ) {
                renderForm(
                    title = "New Event",
                    url = "/admin/schedule/events",
                    form = newEventForm,
                    submitButtonLabel = "Add event",
                )
            }
            flatpickr()
        }

    fun renderEdit(
        eventForm: Form<Event>,
        newTriggerForm: Form<NewScheduledTrigger>,
        triggers: List<TriggerRow>,
        compos: List<Compo>,
    ) =
        Page("Edit event") {
            h1 { +"Edit event" }

            columns({
                renderForm(
                    url = "/admin/schedule/events/${eventForm.data.id}",
                    form = eventForm,
                )
            }, {
                if (triggers.isNotEmpty()) {
                    article {
                        header { +"Triggers" }
                        p { +"These actions will happen at the scheduled start time of the event" }
                        table {
                            thead {
                                tr {
                                    th { +"Action" }
                                    th(classes = "narrow") {}
                                }
                            }
                            tbody {
                                triggers.forEach { trigger ->
                                    tr {
                                        td { +trigger.description }
                                        td(classes = "settings") {
                                            when (trigger) {
                                                is SuccessfulTriggerRow -> icon(
                                                    "circle-check",
                                                    "Triggered at ${trigger.executionTime}"
                                                )

                                                is FailedTriggerRow -> icon("circle-exclamation", trigger.error)
                                                else -> toggleButton(
                                                    trigger.enabled,
                                                    IconSet.scheduled,
                                                    "/admin/schedule/triggers/${trigger.triggerId}/setEnabled"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                renderForm(
                    title = "Add new trigger",
                    url = "/admin/schedule/triggers",
                    form = newTriggerForm,
                    options = mapOf(
                        "action" to NewScheduledTrigger.TriggerOptions,
                        "compoId" to compos
                    ),
                    submitButtonLabel = "Add trigger"
                )
            })
            flatpickr()
        }

    fun FlowContent.flatpickr() {
        link {
            rel = "stylesheet"
            href = "/assets/flatpickr.min.css"
        }
        script(src = "/assets/flatpickr.js") {}
    }
}

