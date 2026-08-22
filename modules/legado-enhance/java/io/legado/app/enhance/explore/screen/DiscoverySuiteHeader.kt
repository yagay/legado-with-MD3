package io.legado.app.ui.widget.components.explore

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerticalAlignTop
import io.legado.app.ui.widget.components.divider.PillHeaderDivider
import io.legado.app.ui.widget.components.topbar.TopBarActionButton

/**
 * Small modern-discovery adapter built entirely from upstream UI components.
 */
@Composable
fun DiscoverySuiteHeader(
    title: String,
    onSettingsClick: () -> Unit,
    onScrollToTop: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillHeaderDivider(
            title = title,
            modifier = Modifier.weight(1f),
        )
        TopBarActionButton(
            onClick = onScrollToTop,
            imageVector = Icons.Default.VerticalAlignTop,
            contentDescription = "回到顶部",
        )
        TopBarActionButton(
            onClick = onSettingsClick,
            imageVector = Icons.Default.Settings,
            contentDescription = "布局设置",
        )
    }
}
