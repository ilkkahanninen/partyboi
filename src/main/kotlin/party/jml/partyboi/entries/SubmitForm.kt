package party.jml.partyboi.entries

import kotlinx.html.*
import party.jml.partyboi.database.Compo
import party.jml.partyboi.database.NewEntry
import party.jml.partyboi.form.Form
import arrow.core.*
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.database.EntryUpdate

fun FlowContent.submitNewEntryForm(url: String, compos: List<Compo>, values: Form<NewEntry>) {
    form(classes = "submitForm appForm", method = FormMethod.post, action = url, encType = FormEncType.multipartFormData) {
        article {
            header {
                +"Submit a new entry"
            }
            fieldSet {
                renderForm(values, mapOf(
                    "compoId" to compos.map { DropdownOption.fromCompo(it) }
                ))
            }
            footer {
                submitInput { value = "Submit" }
            }
        }
    }
}

fun FlowContent.editEntryForm(url: String, compos: List<Compo>, values: Form<EntryUpdate>) {
    form(classes = "submitForm appForm", method = FormMethod.post, action = url, encType = FormEncType.multipartFormData) {
        article {
            header {
                +"Edit an entry"
            }
            fieldSet {
                renderForm(values, mapOf(
                    "compoId" to compos.map { DropdownOption.fromCompo(it) }
                ))
            }
            footer {
                submitInput { value = "Submit" }
            }
        }
    }
}

inline fun <T : Validateable<T>> FIELDSET.renderForm(form: Form<T>, options: Map<String, List<DropdownOption>>? = null) {
    form.forEach { data ->
        if (data.isFileInput) {
            formFileInput(data)
        } else if (data.type == InputType.hidden) {
            formHiddenValue(data)
        } else {
            val opts = options?.get(data.key)
            if (opts == null) {
                formTextInput(data)
            } else {
                dropdown(data, opts)
            }
        }
    }
}

inline fun FlowContent.formError(error: Option<String>) {
    error.map { small(classes = "error") { +it } }
}

inline fun FlowOrInteractiveOrPhrasingContent.formTextInput(data: Form.FieldData, crossinline block : INPUT.() -> Unit = {}) {
    label {
        span { +data.label }
        textInput(name = data.key) {
            value = data.value
            type = data.type
            block()
        }
        formError(data.error)
    }
}

inline fun FlowOrInteractiveOrPhrasingContent.formHiddenValue(data: Form.FieldData, crossinline block : INPUT.() -> Unit = {}) {
    hiddenInput(name = data.key) {
        value = data.value
    }
}

inline fun FlowOrInteractiveOrPhrasingContent.formFileInput(data: Form.FieldData) {
    label {
        span { +data.label }
        fileInput(name = data.key)
        formError(data.error)
    }
}

inline fun FlowOrInteractiveOrPhrasingContent.dropdown(data: Form.FieldData, options: List<DropdownOption>) {
    label {
        span { +data.label }
        select {
            name = data.key
            options.map { opt ->
                option {
                    value = opt.value
                    selected = opt.value == data.value
                    +opt.label
                }
            }
        }
        formError(data.error)
    }
}

data class DropdownOption(val value: String, val label: String) {
    companion object {
        fun fromCompo(compo: Compo): DropdownOption {
            return DropdownOption(compo.id.toString(), compo.name)
        }
    }
}