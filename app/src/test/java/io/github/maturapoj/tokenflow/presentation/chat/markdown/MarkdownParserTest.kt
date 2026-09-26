package io.github.maturapoj.tokenflow.presentation.chat.markdown

import io.github.maturapoj.tokenflow.presentation.chat.markdown.MdBlock.Code
import io.github.maturapoj.tokenflow.presentation.chat.markdown.MdBlock.Heading
import io.github.maturapoj.tokenflow.presentation.chat.markdown.MdBlock.ListItem
import io.github.maturapoj.tokenflow.presentation.chat.markdown.MdBlock.Paragraph
import io.github.maturapoj.tokenflow.presentation.chat.markdown.MdBlock.Quote
import io.github.maturapoj.tokenflow.presentation.chat.markdown.MdBlock.Rule
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {

    private fun parse(s: String) = MarkdownParser.parse(s.trimIndent())

    @Test fun `closed fence keeps language and code verbatim`() {
        val blocks = parse(
            """
            Here:
            ```kotlin
            fun main() {
                println("hi")
            }
            ```
            Done.
            """,
        )
        assertEquals(
            listOf(
                Paragraph("Here:"),
                Code("kotlin", "fun main() {\n    println(\"hi\")\n}", closed = true),
                Paragraph("Done."),
            ),
            blocks,
        )
    }

    @Test fun `unclosed fence mid-stream becomes an open code block`() {
        assertEquals(
            listOf(Code("py", "print(1)\nx =", closed = false)),
            parse("```py\nprint(1)\nx ="),
        )
    }

    @Test fun `markdown inside a code block is not parsed`() {
        assertEquals(
            listOf(Code("", "# not a heading\n- not a list", closed = true)),
            parse("```\n# not a heading\n- not a list\n```"),
        )
    }

    @Test fun `headings, lists, quote and rule`() {
        val blocks = parse(
            """
            ## Steps
            - one
              - nested
            2) two
            > note
            ---
            """,
        )
        assertEquals(
            listOf(
                Heading(2, "Steps"),
                ListItem("•", "one", indent = 0),
                ListItem("•", "nested", indent = 1),
                ListItem("2.", "two", indent = 0),
                Quote("note"),
                Rule,
            ),
            blocks,
        )
    }

    @Test fun `blank line splits paragraphs, single newline keeps a line break`() {
        assertEquals(
            listOf(Paragraph("line one\nline two"), Paragraph("next")),
            parse("line one\nline two\n\nnext"),
        )
    }

    @Test fun `partial list marker stays a paragraph`() {
        assertEquals(listOf(Paragraph("-")), parse("-"))
    }
}
