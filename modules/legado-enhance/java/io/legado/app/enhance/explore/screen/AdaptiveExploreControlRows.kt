package io.legado.app.enhance.explore.screen

import androidx.compose.foundation.layout.Arrangement
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
 * Places source-native action controls in one compact row above the category rows.
 *
 * Width is derived from the visible label length, so short actions such as "登录"
 * take less space while longer actions such as "刷新发现页" receive more room.
 * Category/url/select/tree rows are rendered elsewhere and are intentionally excluded.
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
    if (controls.isEmpty()) return

    val weights = remember(controls) {
        controls.map(::controlWidthUnits)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        controls.forEachIndexed { index, kind ->
            ExploreKindMultiTypeItem(
                kind = kind,
                sourceUrl = sourceUrl,
                onOpenUrl = { url -> onOpenUrl(kind, url) },
                onRefreshKinds = onRefreshKinds,
                modifier = Modifier
                    .weight(weights[index])
                    .fillMaxWidth(),
                isMiuix = false,
                useCase = useCase,
            )
        }
    }
}

internal fun controlWidthUnits(kind: ExploreKind): Float {
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

    val chromeUnits = when (kind.type) {
        ExploreKind.Type.text -> 7f
        ExploreKind.Type.button,
        ExploreKind.Type.toggle -> 5f
        else -> 4f
    }
    val minimum = if (kind.type == ExploreKind.Type.text) 10f else 6f
    return (textUnits + chromeUnits).coerceAtLeast(minimum)
}
