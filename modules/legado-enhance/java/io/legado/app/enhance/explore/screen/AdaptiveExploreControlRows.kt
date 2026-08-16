package io.legado.app.enhance.explore.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem

/**
 * Packs source-native controls into compact rows without changing their behavior.
 *
 * Width is derived from visible text rather than a fixed column count: short labels
 * naturally share a row, while long labels receive more room. Text inputs stay on
 * their own row so editable content is never squeezed into a tiny cell.
 */
@Composable
fun AdaptiveExploreControlRows(
    controls: List<ExploreKind>,
    sourceUrl: String?,
    useCase: ExploreKindUiUseCase,
    onOpenUrl: (ExploreKind, String) -> Unit,
    onRefreshKinds: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(controls) { packAdaptiveControlRows(controls) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val weights = row.map(::controlWidthUnits)
                row.forEachIndexed { index, kind ->
                    ExploreKindMultiTypeItem(
                        kind = kind,
                        sourceUrl = sourceUrl,
                        onOpenUrl = { url -> onOpenUrl(kind, url) },
                        onRefreshKinds = onRefreshKinds,
                        modifier = Modifier.weight(weights[index]).fillMaxWidth(),
                        isMiuix = false,
                        useCase = useCase,
                    )
                }
            }
        }
    }
}

internal fun packAdaptiveControlRows(
    controls: List<ExploreKind>,
    maxUnitsPerRow: Float = 24f,
): List<List<ExploreKind>> {
    if (controls.isEmpty()) return emptyList()

    val rows = mutableListOf<MutableList<ExploreKind>>()
    var current = mutableListOf<ExploreKind>()
    var currentUnits = 0f

    fun flush() {
        if (current.isNotEmpty()) rows += current
        current = mutableListOf()
        currentUnits = 0f
    }

    controls.forEach { kind ->
        // Editable fields need usable typing space and should not be compressed beside buttons.
        if (kind.type == ExploreKind.Type.text) {
            flush()
            rows += mutableListOf(kind)
            return@forEach
        }

        val units = controlWidthUnits(kind)
        if (current.isNotEmpty() && currentUnits + units > maxUnitsPerRow) {
            flush()
        }
        current += kind
        currentUnits += units
    }
    flush()
    return rows
}

internal fun controlWidthUnits(kind: ExploreKind): Float {
    if (kind.type == ExploreKind.Type.text) return 24f

    val label = kind.viewName?.takeIf { it.isNotBlank() } ?: kind.title
    val textUnits = label.sumOf { ch ->
        when {
            ch.code <= 0x7f -> 1.0
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS -> 2.0
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS -> 2.0
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HIRAGANA -> 2.0
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.KATAKANA -> 2.0
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HANGUL_SYLLABLES -> 2.0
            else -> 1.5
        }
    }.toFloat()

    // Reserve room for horizontal padding and the optional trailing type icon.
    val chromeUnits = when (kind.type) {
        ExploreKind.Type.button,
        ExploreKind.Type.toggle,
        ExploreKind.Type.select -> 5f
        else -> 4f
    }
    return (textUnits + chromeUnits).coerceIn(6f, 24f)
}
