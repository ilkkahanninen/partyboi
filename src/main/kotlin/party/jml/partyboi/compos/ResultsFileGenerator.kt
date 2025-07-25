package party.jml.partyboi.compos

import arrow.core.Option
import arrow.core.none
import party.jml.partyboi.voting.CompoResult

object ResultsFileGenerator {
    const val LINE_WIDTH = 80
    const val PLACE_COL_WIDTH = 4
    const val POINTS_COL_WIDTH = 9
    const val SPACE_BEFORE_ENTRY = 3
    const val ENTRY_WIDTH = LINE_WIDTH - PLACE_COL_WIDTH - POINTS_COL_WIDTH - SPACE_BEFORE_ENTRY

    fun generate(header: String, results: List<CompoResult>, includeInfo: Boolean): String {
        val txt = StringBuilder()

        txt.append(header + "\n\n")

        CompoResult.groupResults(results).forEach { compo, compoResults ->
            heading(txt, "${compo.name} compo")
            compoResults.forEach { (place, resultsForPlace) ->
                resultsForPlace.forEachIndexed { index, result ->
                    entry(
                        builder = txt,
                        place = if (index == 0) place else null,
                        points = result.points,
                        title = result.title,
                        author = result.author,
                        info = if (includeInfo) result.info else none(),
                    )
                }
            }
        }

        separator(txt)
        centered(txt, "Results generated by Partyboi")
        return txt.toString()
    }

    private fun heading(builder: StringBuilder, text: String) {
        builder.append("\n")
        centered(builder, "[ $text ]", '-')
        builder.append("\n")
    }

    private fun separator(builder: StringBuilder) {
        builder.append("\n" + "-".repeat(LINE_WIDTH) + "\n\n")
    }

    private fun centered(builder: StringBuilder, text: String, padChar: Char = ' ') {
        val len = text.length
        val charsLeft = (LINE_WIDTH - len) / 2
        val charsRight = LINE_WIDTH - charsLeft - len

        builder.append("$padChar".repeat(charsLeft))
        builder.append(text)
        builder.append("$padChar".repeat(charsRight))
        builder.append("\n")
    }

    private fun entry(
        builder: StringBuilder,
        place: Int?,
        points: Int,
        title: String,
        author: String,
        info: Option<String>,
    ) {
        builder.append((if (place == null) "" else "$place.").padStart(PLACE_COL_WIDTH, ' '))
        builder.append("$points pts".padStart(POINTS_COL_WIDTH, ' '))
        builder.append(" ".repeat(SPACE_BEFORE_ENTRY))

        val singleLine = "$title by $author"
        if (singleLine.length <= ENTRY_WIDTH) {
            builder.append(singleLine)
            builder.append("\n")
        } else {
            multiline(builder, LINE_WIDTH - ENTRY_WIDTH, title)
            builder.append(" ".repeat(LINE_WIDTH - ENTRY_WIDTH))
            multiline(builder, LINE_WIDTH - ENTRY_WIDTH + 1, author, "  by ")
        }

        info.onSome {
            builder.append("\n")
            builder.append(" ".repeat(LINE_WIDTH - ENTRY_WIDTH))
            multiline(builder, LINE_WIDTH - ENTRY_WIDTH + 1, it, "")
            builder.append("\n")
        }
    }

    private fun multiline(builder: StringBuilder, indent: Int, text: String, prefix: String = "") {
        builder.append(prefix)
        var cursor = prefix.length
        val whitespace = Regex("\\s+")
        text.split(whitespace).forEachIndexed { index, token ->
            val len = token.length
            if (cursor + len > ENTRY_WIDTH) {
                builder.append("\n" + " ".repeat(indent))
                cursor = 0
            }
            if (index > 0) {
                builder.append(" ")
                cursor += 1
            }
            builder.append(token)
            cursor += len
        }
        builder.append("\n")
    }
}