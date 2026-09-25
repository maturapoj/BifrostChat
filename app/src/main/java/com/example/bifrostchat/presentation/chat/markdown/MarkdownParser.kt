package com.example.bifrostchat.presentation.chat.markdown

/** Block-level Markdown, enough for typical LLM replies. Inline syntax is handled by [inlineMarkdown]. */
sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock

    /** [closed] is false while a fence is still streaming in; the rest of the text is treated as code. */
    data class Code(val language: String, val code: String, val closed: Boolean) : MdBlock
    data object Rule : MdBlock
}

/**
 * Line-based parser that tolerates partial input, since it runs on every frame of a stream:
 * an unclosed fence becomes an open code block, and unfinished syntax falls back to plain text.
 * Not supported: tables, nested block quotes, HTML.
 */
object MarkdownParser {

    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val LIST_ITEM = Regex("""^(\s*)([-*+]|\d{1,3}[.)])\s+(.*)$""")
    private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")

    fun parse(source: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        fun flushParagraph() {
            if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
            paragraph.clear()
        }

        val lines = source.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            val fence = fenceOf(trimmed)

            when {
                fence != null -> {
                    flushParagraph()
                    val indent = line.length - line.trimStart().length
                    val language = trimmed.removePrefix(fence).trim()
                    val code = mutableListOf<String>()
                    var closed = false
                    i++
                    while (i < lines.size) {
                        if (lines[i].trim() == fence) {
                            closed = true
                            break
                        }
                        code += lines[i].dropLeadingSpaces(indent)
                        i++
                    }
                    blocks += MdBlock.Code(language, code.joinToString("\n"), closed)
                }
                trimmed.isEmpty() -> flushParagraph()
                RULE.matches(trimmed) -> {
                    flushParagraph()
                    blocks += MdBlock.Rule
                }
                else -> {
                    val heading = HEADING.find(trimmed)
                    val item = LIST_ITEM.find(line)
                    when {
                        heading != null -> {
                            flushParagraph()
                            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
                        }
                        item != null -> {
                            flushParagraph()
                            val (spaces, marker, text) = item.destructured
                            val shown = if (marker[0].isDigit()) marker.dropLast(1) + "." else "•"
                            blocks += MdBlock.ListItem(shown, text.trim(), indent = spaces.length / 2)
                        }
                        trimmed.startsWith(">") -> {
                            flushParagraph()
                            blocks += MdBlock.Quote(trimmed.removePrefix(">").trim())
                        }
                        else -> paragraph += trimmed
                    }
                }
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    private fun fenceOf(trimmed: String): String? = when {
        trimmed.startsWith("```") -> "```"
        trimmed.startsWith("~~~") -> "~~~"
        else -> null
    }

    private fun String.dropLeadingSpaces(max: Int): String {
        var n = 0
        while (n < max && n < length && this[n] == ' ') n++
        return substring(n)
    }
}
