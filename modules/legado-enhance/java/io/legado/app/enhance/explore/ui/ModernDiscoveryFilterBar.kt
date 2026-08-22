package io.legado.app.enhance.explore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetTarget
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.text.AppText
import kotlin.math.roundToInt

/**
 * 现代发现页分类行。
 *
 * 只保留现代布局需要的“单排动态容纳 + 溢出展开 + 选中项前置”行为，
 * 文字、颜色和选项容器全部复用项目现有主题与 TextCard 样式。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModernDiscoveryFilterBar(
    title: String,
    targets: List<DiscoverySuiteWidgetTarget>,
    selectedTargetTitle: String?,
    onTargetClick: (DiscoverySuiteWidgetTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    if (targets.isEmpty()) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        var expanded by remember(title, targets) { mutableStateOf(false) }
        var expandedVisibleCount by remember(title, targets) { mutableIntStateOf(EXPANDED_BATCH_SIZE) }
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val titleColumnWidth = 62.dp
        val markerWidth = 34.dp
        val optionHorizontalPadding = 3.dp
        val optionSpacing = 6.dp
        val optionStyle = LegadoTheme.typography.bodyMedium.copy(fontSize = 14.sp)
        val displayTitle = remember(title) { stripWrapSymbols(title) }
        val selectedIndex = remember(targets, selectedTargetTitle) {
            targets.indexOfFirst { it.title == selectedTargetTitle }
        }

        val contentWidthPx = with(density) {
            (maxWidth - if (displayTitle.isBlank()) 0.dp else titleColumnWidth).toPx()
        }.roundToInt().coerceAtLeast(0)
        val markerWidthPx = with(density) { markerWidth.toPx() }.roundToInt()

        val firstLine = remember(
            targets,
            selectedIndex,
            contentWidthPx,
            markerWidthPx,
            textMeasurer,
            optionStyle,
            density
        ) {
            fun fitIndices(indices: Sequence<Int>, widthPx: Int): List<Int> {
                val chipPaddingPx = with(density) {
                    (optionHorizontalPadding * 2).toPx()
                }.roundToInt()
                val spacingPx = with(density) { optionSpacing.toPx() }.roundToInt()
                val result = ArrayList<Int>(8)
                var usedWidth = 0
                for (index in indices) {
                    if (index !in targets.indices) continue
                    val option = stripWrapSymbols(targets[index].title)
                    val textWidth = textMeasurer.measure(
                        text = AnnotatedString(option),
                        style = optionStyle,
                        maxLines = 1
                    ).size.width
                    val chipWidth = textWidth + chipPaddingPx + if (result.isNotEmpty()) spacingPx else 0
                    if (usedWidth + chipWidth > widthPx) break
                    usedWidth += chipWidth
                    result += index
                }
                return result
            }

            val naturalFirst = fitIndices(targets.indices.asSequence(), contentWidthPx)
            val needMarker = naturalFirst.size < targets.size
            val actualWidthPx = (contentWidthPx - if (needMarker) markerWidthPx else 0)
                .coerceAtLeast(0)

            if (selectedIndex in targets.indices && selectedIndex >= naturalFirst.size) {
                val promoted = sequence {
                    yield(selectedIndex)
                    targets.indices.forEach { index ->
                        if (index != selectedIndex) yield(index)
                    }
                }
                fitIndices(promoted, actualWidthPx).takeIf { it.isNotEmpty() }
                    ?: fitIndices(targets.indices.asSequence(), actualWidthPx)
            } else {
                fitIndices(targets.indices.asSequence(), actualWidthPx)
            }
        }

        val expandable = firstLine.size < targets.size
        val restSize = (targets.size - firstLine.size).coerceAtLeast(0)
        val restLine = remember(expanded, firstLine, targets.size) {
            if (!expanded || restSize == 0) {
                emptyList()
            } else {
                val firstSet = firstLine.toHashSet()
                targets.indices.filterNot { it in firstSet }
            }
        }

        LaunchedEffect(expanded, restSize) {
            if (!expanded) {
                expandedVisibleCount = EXPANDED_BATCH_SIZE
                return@LaunchedEffect
            }
            expandedVisibleCount = minOf(EXPANDED_BATCH_SIZE, restSize)
            while (expandedVisibleCount < restSize) {
                withFrameNanos { }
                expandedVisibleCount = minOf(
                    expandedVisibleCount + EXPANDED_BATCH_SIZE,
                    restSize
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (displayTitle.isNotBlank()) {
                    AppText(
                        text = displayTitle,
                        color = LegadoTheme.colorScheme.onSurface,
                        style = LegadoTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(titleColumnWidth)
                            .padding(start = 8.dp)
                    )
                }

                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(optionSpacing),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    maxLines = 1
                ) {
                    firstLine.forEach { optionIndex ->
                        ModernDiscoveryFilterOption(
                            target = targets[optionIndex],
                            selected = optionIndex == selectedIndex,
                            textStyle = optionStyle,
                            onClick = {
                                expanded = false
                                onTargetClick(targets[optionIndex])
                            }
                        )
                    }
                }

                if (expandable) {
                    TextCard(
                        text = if (expanded) "︿" else "﹀",
                        onClick = { expanded = !expanded },
                        modifier = Modifier.width(markerWidth),
                        backgroundColor = Color.Transparent,
                        contentColor = LegadoTheme.colorScheme.primary,
                        cornerRadius = 8.dp,
                        horizontalPadding = 6.dp,
                        verticalPadding = 2.dp,
                        textStyle = LegadoTheme.typography.bodyLargeEmphasized,
                    )
                }
            }

            if (expanded && expandable) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(optionSpacing),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    restLine.take(expandedVisibleCount).forEach { optionIndex ->
                        ModernDiscoveryFilterOption(
                            target = targets[optionIndex],
                            selected = optionIndex == selectedIndex,
                            textStyle = optionStyle,
                            onClick = {
                                expanded = false
                                onTargetClick(targets[optionIndex])
                            }
                        )
                    }
                }
            }
        }
    }
}

private const val EXPANDED_BATCH_SIZE = 64

@Composable
private fun ModernDiscoveryFilterOption(
    target: DiscoverySuiteWidgetTarget,
    selected: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    onClick: () -> Unit
) {
    TextCard(
        text = stripWrapSymbols(target.title),
        onClick = onClick,
        backgroundColor = if (selected) LegadoTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) LegadoTheme.colorScheme.onPrimary else LegadoTheme.colorScheme.onSurface,
        cornerRadius = 2.dp,
        horizontalPadding = 3.dp,
        verticalPadding = 0.dp,
        textStyle = textStyle,
    )
}

internal fun stripWrapSymbols(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return value

    fun Char.isSemanticCore(): Boolean =
        Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN ||
            this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'

    var first = value.indexOfFirst { it.isSemanticCore() }
    var last = value.indexOfLast { it.isSemanticCore() }
    if (first < 0 || last < first) {
        first = value.indexOfFirst { it.isLetterOrDigit() }
        last = value.indexOfLast { it.isLetterOrDigit() }
    }
    if (first < 0 || last < first) return value
    return value.substring(first, last + 1).trim()
}
