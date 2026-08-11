package io.legado.app.ui.widget.components.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetTarget
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.book.SearchBookGridItem
import io.legado.app.ui.widget.components.card.TextCard
import kotlinx.collections.immutable.ImmutableList

/**
 * 仅清理发现分类标题两端的装饰符号，中间的符号保持原样。
 *
 * 例如：
 * `༺ˇ»`ʚ 男生频道 ɞ´«ˇ༻` -> `男生频道`
 * `[推荐]` -> `推荐`
 * `都市·异能` -> `都市·异能`
 *
 * 如果整个标题都没有字母或数字，则保持原值，避免纯符号标题变成空字符串。
 */
private fun cleanExploreDisplayTitle(raw: String): String {
    val value = raw.trim()
    if (value.isEmpty()) return value

    // 优先把中文（Han）和 ASCII 字母/数字视为“正文字符”。
    // 这样 ʚ / ɞ 这类 Unicode 装饰字母即使 Character 判定为 Letter，
    // 只要它们位于标题两端，也不会被误保留。
    fun Char.isSemanticCore(): Boolean {
        return Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN ||
            this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9'
    }

    var first = value.indexOfFirst { it.isSemanticCore() }
    var last = value.indexOfLast { it.isSemanticCore() }

    // 极少数标题可能没有中文/ASCII（例如纯日文、韩文、希腊文），
    // 再回退到普通 LetterOrDigit，避免误删真实标题。
    if (first < 0 || last < first) {
        first = value.indexOfFirst { it.isLetterOrDigit() }
        last = value.indexOfLast { it.isLetterOrDigit() }
    }

    if (first < 0 || last < first) return value
    return value.substring(first, last + 1).trim()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverySuiteTagBarWidget(
    title: String,
    targets: List<DiscoverySuiteWidgetTarget>,
    selectedTargetTitle: String? = null,
    onTargetClick: (DiscoverySuiteWidgetTarget) -> Unit,
    onLongClick: ((DiscoverySuiteWidgetTarget) -> Unit)? = null
) {
    // 所有层级统一为“单行 + 内联展开更多”。
    // 展开状态使用 rememberSaveable 按层级标题保存。
    // 点击展开区域中的任意选项后自动收起；也可以通过右侧箭头手动展开/收起。
    // 先按真实文字宽度计算当前屏幕能完整放下多少个 Chip；
    // 放不下的项目不显示半个，点击右侧箭头后在当前栏下方多行展开。
    var overflowExpanded by rememberSaveable(title) { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chipTextStyle = LegadoTheme.typography.labelSmallEmphasized
    val chipHorizontalPadding = 12.dp
    val chipSpacing = 8.dp
    val displayTitle = cleanExploreDisplayTitle(title)
    val displayTargetTitles = targets.map { cleanExploreDisplayTitle(it.title) }
    val titleWidth = if (displayTitle.isNotEmpty()) 64.dp else 0.dp
    val moreButtonWidth = 40.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        val totalWidthPx = with(density) { maxWidth.toPx() }
        val titleWidthPx = with(density) { titleWidth.toPx() }
        val spacingPx = with(density) { chipSpacing.toPx() }
        val chipPaddingPx = with(density) { (chipHorizontalPadding * 2).toPx() }
        val moreWidthPx = with(density) { moreButtonWidth.toPx() }

        val chipWidthsPx = displayTargetTitles.map { displayText ->
            textMeasurer.measure(
                text = displayText,
                style = chipTextStyle,
                maxLines = 1
            ).size.width + chipPaddingPx
        }

        fun fitIndexes(availablePx: Float): MutableList<Int> {
            val result = mutableListOf<Int>()
            var used = 0f
            chipWidthsPx.forEachIndexed { index, width ->
                val next = if (result.isEmpty()) width else used + spacingPx + width
                if (next <= availablePx) {
                    result += index
                    used = next
                } else {
                    return@forEachIndexed
                }
            }
            return result
        }

        // 第一次不预留“更多”按钮，看看是否可以全部完整显示。
        val normalAvailablePx = (totalWidthPx - titleWidthPx).coerceAtLeast(0f)
        var visibleIndexes = fitIndexes(normalAvailablePx)
        var hasOverflow = visibleIndexes.size < targets.size

        if (hasOverflow) {
            // 有溢出时为右侧展开/收起箭头预留空间后重新计算。
            val availableWithMorePx =
                (totalWidthPx - titleWidthPx - moreWidthPx - spacingPx).coerceAtLeast(0f)
            visibleIndexes = fitIndexes(availableWithMorePx)

            // 推荐行为：
            // 1. 当前选中项本来就在主行 -> 完全保持原始顺序，避免每次点击都跳动。
            // 2. 当前选中项原本藏在展开区域 -> 把它提到主行第一位，
            //    然后按书源原始顺序继续填充其余可见项。
            // 这里只调整 UI 展示顺序，不修改 targets / ExploreTree 的原始数据顺序。
            val selectedIndex = targets.indexOfFirst { it.title == selectedTargetTitle }
            if (selectedIndex >= 0 && selectedIndex !in visibleIndexes) {
                val promoted = mutableListOf(selectedIndex)
                var used = chipWidthsPx[selectedIndex]

                targets.indices.forEach { index ->
                    if (index == selectedIndex) return@forEach
                    val next = used + spacingPx + chipWidthsPx[index]
                    if (next <= availableWithMorePx) {
                        promoted += index
                        used = next
                    }
                }

                // 即使极端情况下选中项文字比可用区域还宽，也优先显示它；
                // TextCard 本身不做省略，父级仍只保持单行布局。
                visibleIndexes = promoted
            }
        }

        val visibleSet = visibleIndexes.toSet()
        val visibleTargets = visibleIndexes.map { targets[it] }
        val hiddenTargets = targets.filterIndexed { index, _ -> index !in visibleSet }

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (displayTitle.isNotEmpty()) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .width(titleWidth)
                            .padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(chipSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleTargets.forEach { target ->
                        val isSelected = target.title == selectedTargetTitle
                        TextCard(
                            text = cleanExploreDisplayTitle(target.title),
                            onClick = { onTargetClick(target) },
                            onLongClick = onLongClick?.let { cb -> { cb(target) } },
                            backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            cornerRadius = 8.dp,
                            horizontalPadding = chipHorizontalPadding,
                            verticalPadding = 6.dp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }

                if (hiddenTargets.isNotEmpty()) {
                    IconButton(
                        onClick = { overflowExpanded = !overflowExpanded },
                        modifier = Modifier.size(moreButtonWidth)
                    ) {
                        Icon(
                            imageVector = if (overflowExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (overflowExpanded) {
                                "收起${displayTitle.ifBlank { "选项" }}"
                            } else {
                                "展开${displayTitle.ifBlank { "选项" }}"
                            },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 不再弹出 DropdownMenu。点击右侧箭头后，直接在当前分类栏下方
            // 以内联 FlowRow 展开所有溢出选项；展开区从整栏左侧开始，不跟随标题列缩进。
            // 再次点击箭头即可收起。
            if (overflowExpanded && hiddenTargets.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 0.dp,
                            top = 6.dp,
                            end = 0.dp
                        ),
                    horizontalArrangement = Arrangement.spacedBy(chipSpacing),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    hiddenTargets.forEach { target ->
                        val isSelected = target.title == selectedTargetTitle
                        TextCard(
                            text = cleanExploreDisplayTitle(target.title),
                            onClick = {
                                onTargetClick(target)
                                overflowExpanded = false
                            },
                            onLongClick = onLongClick?.let { cb -> { cb(target) } },
                            backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            cornerRadius = 8.dp,
                            horizontalPadding = chipHorizontalPadding,
                            verticalPadding = 6.dp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverySuiteHorizontalBooksWidget(
    books: ImmutableList<SearchBook>,
    onBookClick: (SearchBook) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books) { book ->
            SearchBookGridItem(
                book = book,
                shelfState = BookShelfState.NOT_IN_SHELF,
                onClick = { onBookClick(book) },
                modifier = Modifier.width(100.dp)
            )
        }
    }
}

@Composable
fun DiscoverySuiteRankButtonsWidget(
    title: String,
    targets: List<DiscoverySuiteWidgetTarget>,
    selectedTargetTitle: String?,
    onTargetClick: (DiscoverySuiteWidgetTarget) -> Unit
) {
    DiscoverySuiteTagBarWidget(
        title = title,
        targets = targets,
        selectedTargetTitle = selectedTargetTitle,
        onTargetClick = onTargetClick
    )
}

@Composable
fun DiscoverySuiteWaterfallBooksWidget(
    books: ImmutableList<SearchBook>,
    coverHeight: androidx.compose.ui.unit.Dp = 110.dp,
    onBookClick: (SearchBook) -> Unit
) {
    books.forEach { book ->
        io.legado.app.ui.widget.components.book.SearchBookListItem(
            book = book,
            shelfState = BookShelfState.NOT_IN_SHELF,
            onClick = { onBookClick(book) },
            showPadding = true,
            coverHeight = coverHeight,
            adaptContentToCoverHeight = true
        )
    }
}

@Composable
fun DiscoverySuiteHeader(
    title: String,
    onSettingsClick: (() -> Unit)? = null,
    onScrollToTop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onScrollToTop != null) {
                IconButton(
                    onClick = onScrollToTop,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerticalAlignTop,
                        contentDescription = "Scroll to Top",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Layout Settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoverySuiteGridBooksWidget(
    books: ImmutableList<SearchBook>,
    gridCount: Int,
    onBookClick: (SearchBook) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        val chunks = books.chunked(gridCount)
        chunks.forEach { chunk ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunk.forEach { book ->
                    SearchBookGridItem(
                        book = book,
                        shelfState = BookShelfState.NOT_IN_SHELF,
                        onClick = { onBookClick(book) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (chunk.size < gridCount) {
                    repeat(gridCount - chunk.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
