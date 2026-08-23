package io.legado.app.ui.main

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

/**
 * Global modal/secondary-page scene strategy.
 *
 * Explicit modal entries (currently source login) keep rendering their own modal content.
 * Normal pushed destinations are hosted by the shared AppModalBottomSheet while the previous
 * destination remains composed underneath. Destination content is untouched, so its Scaffold,
 * TopAppBar, navigation icon and page-specific action icons remain intact.
 *
 * Reader, manga-reader and audio-player routes keep the normal full-screen scene because they
 * own system bars, input handling and other full-screen behaviour.
 */
class ModalOverlaySceneStrategy : SceneStrategy<NavKey> {

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        val entry = entries.lastOrNull() ?: return null
        val previousEntries = entries.dropLast(1)
        if (previousEntries.isEmpty()) return null

        if (entry.metadata[MetadataKey] != null) {
            return ModalOverlayScene(
                entry = entry,
                previousEntries = previousEntries,
            )
        }

        if (!shouldPresentAsSecondarySheet(entry.contentKey)) return null

        return SecondaryPageSheetScene(
            entry = entry,
            previousEntries = previousEntries,
        )
    }

    private fun shouldPresentAsSecondarySheet(route: Any): Boolean = when (route) {
        is MainRouteReadBook,
        is MainRouteReadManga,
        is MainRouteAudioPlay -> false

        else -> true
    }

    companion object {
        private object MetadataKey : NavMetadataKey<Unit>

        fun modalOverlay(): Map<String, Any> = metadata { put(MetadataKey, Unit) }
    }
}

private data class ModalOverlayScene(
    private val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries.takeLast(1)
    override val content: @Composable () -> Unit = { entry.Content() }
}

private data class SecondaryPageSheetScene(
    private val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
) : OverlayScene<NavKey> {
    override val key: Any = "secondary-sheet:${entry.contentKey}"
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries.takeLast(1)
    override val content: @Composable () -> Unit = {
        val activity = LocalContext.current.findComponentActivity()
        AppModalBottomSheet(
            show = true,
            onDismissRequest = { activity?.onBackPressedDispatcher?.onBackPressed() },
            contentPaddingEnabled = false,
            containerColor = LegadoTheme.colorScheme.background,
        ) {
            entry.Content()
        }
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}
