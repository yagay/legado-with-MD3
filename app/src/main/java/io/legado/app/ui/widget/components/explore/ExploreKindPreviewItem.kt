package io.legado.app.ui.widget.components.explore

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase

/**
 * Rendering overload used by the enhanced-layout source-kind preview sheet.
 *
 * The preview keeps its precomputed rows for fast opening, but names are normalized at render
 * time so the sheet follows the list-layout presentation rule without rebuilding the row model.
 */
@Composable
fun ExploreKindMultiTypeItem(
    kind: ExploreKind,
    sourceUrl: String?,
    onOpenUrl: (String) -> Unit,
    onRefreshKinds: () -> Unit,
    modifier: Modifier,
    backgroundColor: Color,
    isMiuix: Boolean,
    displayNameOverride: String?,
    valueOverride: String?,
    onValueChange: ((String) -> Unit)?,
    onRunAction: (() -> Unit)?,
    useCase: ExploreKindUiUseCase,
) {
    val displayName = normalizeExploreKindDisplayName(
        displayNameOverride ?: kind.title
    )

    if (displayName.isEmpty()) return

    // Supplying activity explicitly keeps this call on the shared list-layout implementation
    // instead of recursively selecting this preview-specific overload.
    ExploreKindMultiTypeItem(
        kind = kind,
        sourceUrl = sourceUrl,
        activity = null,
        onOpenUrl = onOpenUrl,
        onRefreshKinds = onRefreshKinds,
        modifier = modifier,
        backgroundColor = backgroundColor,
        isMiuix = isMiuix,
        displayNameOverride = displayName,
        valueOverride = valueOverride,
        onValueChange = onValueChange,
        onRunAction = onRunAction,
        useCase = useCase,
    )
}

private fun normalizeExploreKindDisplayName(value: String): String {
    val text = value.trim()
    if (text.isEmpty()) return ""

    val firstContent = text.indexOfFirst { it.isLetterOrDigit() }
    val lastContent = text.indexOfLast { it.isLetterOrDigit() }
    if (firstContent < 0 || lastContent < firstContent) return ""

    return text.substring(firstContent, lastContent + 1).trim()
}
