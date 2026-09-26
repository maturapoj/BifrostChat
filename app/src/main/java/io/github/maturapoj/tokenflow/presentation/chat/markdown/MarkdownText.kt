package io.github.maturapoj.tokenflow.presentation.chat.markdown

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.maturapoj.tokenflow.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Renders Markdown as a column of blocks. Each block is its own Text, so while a reply
 * streams only the last block changes; finished blocks keep equal inputs and skip
 * recomposition and re-layout.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val colors = MaterialTheme.colorScheme
    val codeStyle = remember(colors) {
        // Lowest container contrasts with the assistant bubble (surfaceVariant) in both themes.
        SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerLowest, fontSize = 13.sp)
    }
    val linkStyle = remember(colors) { SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline) }
    fun inline(text: String) = inlineMarkdown(text, codeStyle, linkStyle)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(inline(block.text))
                is MdBlock.Heading -> Text(
                    inline(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                is MdBlock.ListItem -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
                    Text(block.marker, modifier = Modifier.width(if (block.marker == "•") 16.dp else 24.dp))
                    Text(inline(block.text))
                }
                is MdBlock.Quote -> Row {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(colors.outline),
                    )
                    Text(inline(block.text), fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 8.dp))
                }
                is MdBlock.Code -> CodeBlock(block)
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerLowest),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceContainerLow)
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                block.language.ifEmpty { stringResource(R.string.code_label) },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // Copy only once the fence has closed, so a half-streamed snippet isn't copied.
            if (block.closed) {
                Text(
                    stringResource(if (copied) R.string.copied else R.string.copy),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("code", block.code)))
                                copied = true
                                delay(1_500)
                                copied = false
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            block.code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
        )
    }
}
