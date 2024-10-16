package party.jml.partyboi.templates.components

import kotlinx.html.*
import party.jml.partyboi.templates.Javascript

fun FlowContent.icon(icon: String) {
    i(classes = Icon(icon).classes) {}
}

fun FlowContent.icon(icon: Icon) {
    i(classes = icon.classes) {}
}

fun FlowContent.toggleButton(toggled: Boolean, icons: IconSet, urlPrefix: String) {
    val shownIcon = icons.get(toggled)
    button(classes = "flat-button ${if (toggled) "on" else "off"}") {
        if (shownIcon.tooltip != null) { attributes.put("data-tooltip", shownIcon.tooltip) }
        onClick = Javascript.build {
            httpPut("$urlPrefix/${!toggled}")
            refresh()
        }
        icon(shownIcon)
    }
}

data class IconSet(
    val toggled: Icon,
    val notToggled: Icon
) {
    fun get(state: Boolean) = if (state) toggled else notToggled

    companion object {
        val visibility = IconSet(Icon.visible, Icon.hidden)
        val submitting = IconSet(Icon("file-arrow-up", "Submitting open"), Icon("file-arrow-up", "Submitting closed"))
        val voting = IconSet(Icon("check-to-slot", "Voting open"), Icon("check-to-slot", "Voting closed"))
        val qualified = IconSet(Icon("star", "Qualified"), Icon("star", "Unqualified / disqualified"))
    }
}

data class Icon(
    val icon: String,
    val tooltip: String? = null,
) {
    val classes = "fa-solid fa-$icon"

    companion object {
        val visible = Icon("eye", "Visible")
        val hidden = Icon("eye-slash", "Hidden")
    }
}
