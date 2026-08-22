package io.legado.app.ui.widget.components.explore

import io.legado.app.data.entities.rule.ExploreKind
import kotlin.math.roundToInt

fun calculateExploreKindRows(
    kinds: List<ExploreKind>,
    maxSpan: Int
): List<List<Pair<ExploreKind, Int>>> = calculateFlexRows(
    items = kinds,
    maxSpan = maxSpan,
    layout = { kind ->
        val style = kind.style()
        FlexItemLayout(
            flexGrow = style.layout_flexGrow,
            basisPercent = style.layout_flexBasisPercent,
            wrapBefore = style.layout_wrapBefore,
        )
    },
)

data class FlexItemLayout(
    val flexGrow: Float = 0f,
    val basisPercent: Float = -1f,
    val wrapBefore: Boolean = false,
)

fun <T> calculateFlexRows(
    items: List<T>,
    maxSpan: Int,
    layout: (T) -> FlexItemLayout,
): List<List<Pair<T, Int>>> {
    val rows = mutableListOf<MutableList<Pair<T, Int>>>()
    var currentRow = mutableListOf<Pair<T, Int>>()
    var currentSpan = 0

    fun pushCurrentRow() {
        if (currentRow.isEmpty()) return
        rows.add(currentRow)
        currentRow = mutableListOf()
        currentSpan = 0
    }

    items.forEach { item ->
        val style = layout(item)
        val span = when {
            style.wrapBefore || style.basisPercent >= 1.0f -> maxSpan
            style.basisPercent > 0 -> (maxSpan * style.basisPercent).roundToInt()
                .coerceIn(1, maxSpan)
            style.flexGrow > 0f -> 3
            else -> 2
        }

        // Preserve the source-defined size. A partially filled row is left as-is;
        // the caller may render the remaining area as spacing instead of stretching
        // the last category and changing the source layout semantics.
        if ((style.wrapBefore && currentRow.isNotEmpty()) || currentSpan + span > maxSpan) {
            pushCurrentRow()
        }

        currentRow.add(item to span)
        currentSpan += span

        if (currentSpan >= maxSpan) {
            pushCurrentRow()
        }
    }

    pushCurrentRow()
    return rows
}
