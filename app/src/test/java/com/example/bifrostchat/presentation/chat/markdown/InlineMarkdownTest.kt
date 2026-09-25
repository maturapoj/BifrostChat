package com.example.bifrostchat.presentation.chat.markdown

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineMarkdownTest {

    private val code = SpanStyle(fontFamily = FontFamily.Monospace)
    private val link = SpanStyle()
    private fun render(s: String) = inlineMarkdown(s, code, link)

    /** (styled text, style) pairs, for readable assertions. */
    private fun spans(s: String) = render(s).let { a -> a.spanStyles.map { a.text.substring(it.start, it.end) to it.item } }

    @Test fun `bold italic and code`() {
        val result = render("a **b** *c* `d`")
        assertEquals("a b c d", result.text)
        assertEquals(
            listOf("b" to SpanStyle(fontWeight = FontWeight.Bold), "c" to SpanStyle(fontStyle = FontStyle.Italic), "d" to code),
            spans("a **b** *c* `d`"),
        )
    }

    @Test fun `unclosed markers stay literal mid-stream`() {
        assertEquals("a **bol", render("a **bol").text)
        assertEquals("run `cmd", render("run `cmd").text)
        assertTrue(render("a **bol").spanStyles.isEmpty())
    }

    @Test fun `snake_case and arithmetic are not italic`() {
        assertEquals("use my_var_name and 2 * 3 * 4", render("use my_var_name and 2 * 3 * 4").text)
        assertTrue(render("use my_var_name and 2 * 3 * 4").spanStyles.isEmpty())
    }

    @Test fun `markdown inside inline code is literal`() {
        assertEquals(listOf("**x**" to code), spans("`**x**`"))
    }

    @Test fun `link keeps label and url`() {
        val result = render("see [docs](https://example.com) now")
        assertEquals("see docs now", result.text)
        val url = result.getLinkAnnotations(0, result.length).single()
        assertEquals("https://example.com", (url.item as LinkAnnotation.Url).url)
        assertEquals("docs", result.text.substring(url.start, url.end))
    }
}
