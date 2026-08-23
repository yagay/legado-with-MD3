package io.legado.app.ui.main

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import io.legado.app.ui.widget.components.modalBottomSheet.NativeDraggableComposeBottomSheet

/**
 * Presents normal pushed destinations in the same draggable sheet shell.
 *
 * The destination keeps rendering its own Screen/Scaffold/TopAppBar, so page-specific
 * navigation buttons and actions remain unchanged. Routes that own full-screen system
 * behaviour (reader, manga reader and audio player) deliberately stay on the normal
 * single-pane scene. Source login keeps using its existing modal-overlay route.
 */
class SecondaryPageSheetSceneStrategy(
    private val onDismissRequest: () -> Unit,
) : SceneStrategy<NavKey> {

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        if (entries.size < 2) return null

        val entry = entries.lastOrNull() ?: return null
        val route = entry.contentKey
        if (!shouldPresentAsSheet(route)) return null

        return SecondaryPageSheetScene(
            entry = entry,
            previousEntries = entries.dropLast(1),
            onDismissRequest = onDismissRequest,
        )
    }

    private fun shouldPresentAsSheet(route: Any): Boolean = when (route) {
        is MainRouteSourceLogin,
        is MainRouteReadBook,
        is MainRouteReadManga,
        is MainRouteAudioPlay -> false

        else -> true
    }
}

private data class SecondaryPageSheetScene(
    private val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val onDismissRequest: () -> Unit,
) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries.takeLast(1)
    override val content: @Composable () -> Unit = {
        NativeDraggableComposeBottomSheet(
            show = true,
            title = null,
            onDismissRequest = onDismissRequest,
        ) {
            entry.Content()
        }
    }
}
