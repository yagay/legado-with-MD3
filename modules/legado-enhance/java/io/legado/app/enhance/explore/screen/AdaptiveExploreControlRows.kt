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
import io.legado.app.ui.widget.components.explore.calculateExploreKindRows

/**
 * Renders source-native controls with the same flex/span rules as the upstream list layout.
 * The modern layout only decides where these rows appear; sizing and item styling stay upstream.
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

    val rows = remember(controls) {
        calculateExploreKindRows(controls, maxSpan = 6)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { (kind, span) ->
                    ExploreKindMultiTypeItem(
                        kind = kind,
                        sourceUrl = sourceUrl,
                        onOpenUrl = { url -> onOpenUrl(kind, url) },
                        onRefreshKinds = onRefreshKinds,
                        modifier = Modifier.weight(span.toFloat()),
                        isMiuix = false,
                        useCase = useCase,
                    )
                }
            }
        }
    }
}
