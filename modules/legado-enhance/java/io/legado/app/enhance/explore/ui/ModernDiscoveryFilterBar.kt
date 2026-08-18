package io.legado.app.enhance.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetTarget
import kotlin.math.roundToInt

/**
 * 现代发现页分类行。
 *
 * 行为直接对齐 yagay/legado:master 的 DiscoverFilterHeader：
 * - 第一排按当前屏幕真实可用宽度动态测量，不固定显示数量；
 * - 全部放得下时不显示展开标志；
 * - 有溢出时为展开标志预留宽度后重新计算；
 * - 当前选中项隐藏在折叠区时优先前置，但超宽放不下时放弃前置；
 * - 展开项单独在下一排 FlowRow 展示；
 * - 点击任意选项后自动收起。
 *
 * 大分类源在折叠状态只计算和分配第一排；剩余索引只有真正展开时才生成，
 * 展开后再分帧加入选项，避免切换分类时为数百个隐藏项制造临时对象和 Compose 节点。
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
        val optionStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
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
                        modifier = Modifier
                            .width(markerWidth)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { expanded = !expanded }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
    onClick: () -> Unit
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        text = stripWrapSymbols(target.title),
        color = foreground,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 0.dp)
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
