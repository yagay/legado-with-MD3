package io.legado.app.enhance.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetTarget
import kotlin.math.roundToInt

/**
 * 现代发现页分类行。
 *
 * 所有由书源 url/select/tree 分类生成的 selector 都走这里：
 * - 第一排先按真实文字宽度判断可以放多少项；
 * - 同一排内再按文字实际宽度比例分配剩余空间，整排铺满；
 * - 短文字占较少空间，长文字占较多空间，不缩小字体；
 * - 当前选中项隐藏在折叠区时优先前置；
 * - 展开区使用同一套按文字宽度比例分行逻辑；
 * - 点击任意选项后自动收起。
 */
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
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val titleColumnWidth = 62.dp
        val markerWidth = 34.dp
        val optionHorizontalPadding = 6.dp
        val optionSpacing = 4.dp
        val optionStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
        val displayTitle = stripWrapSymbols(title)
        val displayOptions = targets.map { stripWrapSymbols(it.title) }

        val contentWidthPx = with(density) {
            (maxWidth - if (displayTitle.isBlank()) 0.dp else titleColumnWidth).toPx()
        }.roundToInt().coerceAtLeast(0)
        val markerWidthPx = with(density) { markerWidth.toPx() }.roundToInt()
        val spacingPx = with(density) { optionSpacing.toPx() }.roundToInt()
        val chipPaddingPx = with(density) { (optionHorizontalPadding * 2).toPx() }.roundToInt()

        val measuredWidths = remember(displayOptions, textMeasurer, optionStyle, chipPaddingPx) {
            displayOptions.map { option ->
                textMeasurer.measure(
                    text = AnnotatedString(option),
                    style = optionStyle,
                    maxLines = 1
                ).size.width + chipPaddingPx
            }
        }

        fun countFor(indices: List<Int>, widthPx: Int): Int {
            var used = 0
            var count = 0
            for (index in indices) {
                val itemWidth = measuredWidths.getOrElse(index) { chipPaddingPx }
                val required = itemWidth + if (count > 0) spacingPx else 0
                if (used + required > widthPx) break
                used += required
                count++
            }
            return count
        }

        fun rowsFor(indices: List<Int>, widthPx: Int): List<List<Int>> {
            if (indices.isEmpty()) return emptyList()
            val rows = mutableListOf<MutableList<Int>>()
            var current = mutableListOf<Int>()
            var used = 0
            indices.forEach { index ->
                val itemWidth = measuredWidths.getOrElse(index) { chipPaddingPx }
                    .coerceAtMost(widthPx.coerceAtLeast(1))
                val required = itemWidth + if (current.isNotEmpty()) spacingPx else 0
                if (current.isNotEmpty() && used + required > widthPx) {
                    rows += current
                    current = mutableListOf()
                    used = 0
                }
                used += itemWidth + if (current.isNotEmpty()) spacingPx else 0
                current += index
            }
            if (current.isNotEmpty()) rows += current
            return rows
        }

        val naturalIndices = targets.indices.toList()
        val naturalCount = countFor(naturalIndices, contentWidthPx)
        val needMarker = naturalCount < targets.size
        val firstLineWidthPx = (contentWidthPx - if (needMarker) markerWidthPx else 0).coerceAtLeast(0)
        val selectedIndex = targets.indexOfFirst { it.title == selectedTargetTitle }

        val ordered = remember(targets, selectedTargetTitle, naturalCount) {
            if (selectedIndex in targets.indices && selectedIndex >= naturalCount) {
                listOf(selectedIndex) + targets.indices.filter { it != selectedIndex }
            } else {
                naturalIndices
            }
        }
        val firstLineCount = countFor(ordered, firstLineWidthPx)
            .coerceAtLeast(if (ordered.isNotEmpty()) 1 else 0)
            .coerceAtMost(targets.size)
        val firstLine = ordered.take(firstLineCount)
        val restLine = ordered.drop(firstLineCount)
        val expandable = restLine.isNotEmpty()
        val expandedRows = rowsFor(restLine, with(density) { maxWidth.toPx() }.roundToInt())

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (displayTitle.isNotBlank()) {
                    Text(
                        text = displayTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(titleColumnWidth)
                            .padding(start = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    firstLine.forEachIndexed { position, optionIndex ->
                        val weight = measuredWidths.getOrElse(optionIndex) { chipPaddingPx }
                            .coerceAtLeast(1)
                            .toFloat()
                        ModernDiscoveryFilterOption(
                            target = targets[optionIndex],
                            selected = optionIndex == selectedIndex,
                            modifier = Modifier
                                .weight(weight)
                                .padding(start = if (position == 0) 0.dp else optionSpacing / 2, end = optionSpacing / 2),
                            onClick = {
                                expanded = false
                                onTargetClick(targets[optionIndex])
                            }
                        )
                    }
                }

                if (expandable) {
                    Text(
                        text = if (expanded) "︿" else "﹀",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(markerWidth)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            if (expanded && expandable) {
                expandedRows.forEach { rowIndices ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowIndices.forEachIndexed { position, optionIndex ->
                            val weight = measuredWidths.getOrElse(optionIndex) { chipPaddingPx }
                                .coerceAtLeast(1)
                                .toFloat()
                            ModernDiscoveryFilterOption(
                                target = targets[optionIndex],
                                selected = optionIndex == selectedIndex,
                                modifier = Modifier
                                    .weight(weight)
                                    .padding(start = if (position == 0) 0.dp else optionSpacing / 2, end = optionSpacing / 2),
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
}

@Composable
private fun ModernDiscoveryFilterOption(
    target: DiscoverySuiteWidgetTarget,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        text = stripWrapSymbols(target.title),
        color = foreground,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 0.dp)
    )
}

private fun stripWrapSymbols(raw: String): String {
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
