package com.example.bifrostchat.presentation.chat.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Renders `**bold**`, `*italic*`, `~~strike~~`, `` `code` `` and `[links](url)`.
 * Anything left unclosed (common mid-stream) stays as literal text until its closer arrives.
 */
fun inlineMarkdown(text: String, codeStyle: SpanStyle, linkStyle: SpanStyle): AnnotatedString =
    buildAnnotatedString { appendInline(text, codeStyle, linkStyle) }

private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
private val STRIKE = SpanStyle(textDecoration = TextDecoration.LineThrough)

private fun AnnotatedString.Builder.appendInline(s: String, code: SpanStyle, link: SpanStyle) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) append(plain.toString())
        plain.clear()
    }

    var i = 0
    while (i < s.length) {
        val c = s[i]

        if (c == '`') {
            val end = s.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                withStyle(code) { append(s.substring(i + 1, end)) }
                i = end + 1
                continue
            }
        }

        val pair = s.startsWith("**", i) || s.startsWith("__", i) || s.startsWith("~~", i)
        if (pair) {
            val delimiter = s.substring(i, i + 2)
            val end = s.indexOf(delimiter, i + 2)
            if (end > i + 2) {
                flush()
                withStyle(if (delimiter == "~~") STRIKE else BOLD) { appendInline(s.substring(i + 2, end), code, link) }
                i = end + 2
                continue
            }
        }

        if ((c == '*' || c == '_') && opensEmphasis(s, i)) {
            val end = closeOfEmphasis(s, i)
            if (end > 0) {
                flush()
                withStyle(ITALIC) { appendInline(s.substring(i + 1, end), code, link) }
                i = end + 1
                continue
            }
        }

        if (c == '[') {
            // The label ends at the first `]`; it only makes a link if `(` follows right away,
            // so `arr[0] … [docs](url)` doesn't pull "0] … [docs" into the label.
            val close = s.indexOf(']', i + 1)
            val mid = if (close > 0 && s.startsWith("](", close)) close else -1
            val end = if (mid > 0) s.indexOf(')', mid + 2) else -1
            if (end > 0 && '\n' !in s.substring(i, end)) {
                flush()
                withLink(LinkAnnotation.Url(s.substring(mid + 2, end), TextLinkStyles(link))) {
                    appendInline(s.substring(i + 1, mid), code, link)
                }
                i = end + 1
                continue
            }
        }

        plain.append(c)
        i++
    }
    flush()
}

// `_` only counts at a word boundary so snake_case identifiers stay intact; `* ` is a literal star.
private fun opensEmphasis(s: String, i: Int): Boolean {
    val next = s.getOrNull(i + 1) ?: return false
    if (next.isWhitespace() || next == s[i]) return false
    return s[i] == '*' || s.getOrNull(i - 1)?.isLetterOrDigit() != true
}

private fun closeOfEmphasis(s: String, open: Int): Int {
    val marker = s[open]
    var j = open + 2
    while (j < s.length) {
        if (s[j] == marker && !s[j - 1].isWhitespace() &&
            (marker == '*' || s.getOrNull(j + 1)?.isLetterOrDigit() != true)
        ) return j
        j++
    }
    return -1
}
