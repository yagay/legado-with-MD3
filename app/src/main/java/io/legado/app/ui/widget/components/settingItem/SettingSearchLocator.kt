package io.legado.app.ui.widget.components.settingItem

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private val LocalSettingSearchTarget = staticCompositionLocalOf<String?> { null }

@Composable
fun ProvideSettingSearchTarget(
    searchTarget: String?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSettingSearchTarget provides searchTarget, content = content)
}

@Composable
internal fun currentSettingSearchTarget(explicitTarget: String?): String? =
    explicitTarget ?: LocalSettingSearchTarget.current

/** Performs the final search-result scroll using the setting row's real bounds. */
@Composable
internal fun Modifier.locateSettingSearchTarget(
    title: String,
    searchTarget: String?,
): Modifier {
    val effectiveTarget = currentSettingSearchTarget(searchTarget)
    val isTarget = !effectiveTarget.isNullOrBlank() &&
        title.equals(effectiveTarget, ignoreCase = true)
    if (!isTarget) return this

    val requester = remember(title, effectiveTarget) { BringIntoViewRequester() }
    LaunchedEffect(requester, title, effectiveTarget) {
        // Let the page's lazy-list group jump and the following layout pass finish.
        delay(100)
        requester.bringIntoView()
    }
    return bringIntoViewRequester(requester)
}
