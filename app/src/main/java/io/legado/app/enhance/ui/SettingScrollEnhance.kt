package io.legado.app.enhance.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.enhance.settingssearch.SettingScrollInfo

@Composable
fun LaunchSettingScrollEffect(
    scrollInfo: SettingScrollInfo?,
    listState: LazyListState,
    groupHeaderHeight: Dp = 48.dp,
    itemHeight: Dp = 72.dp,
    groupIndexOffset: Int = 0 
) {
    val density = LocalDensity.current
    if (scrollInfo == null) return
    
    LaunchedEffect(scrollInfo) {
        val offsetPx = with(density) {
            (itemHeight * scrollInfo.itemIndexInGroup + groupHeaderHeight).toPx().toInt()
        }
        listState.animateScrollToItem(scrollInfo.groupIndex + groupIndexOffset, offsetPx)
    }
}
