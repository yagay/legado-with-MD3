package io.legado.app.ui.book.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme

@Composable
internal fun BookReviewEntry(
    state: BookReviewUiState,
    onClick: () -> Unit,
) {
    if (!state.available) return
    ListItem(
        headlineContent = {
            Text(if (state.totalCount != null) "书评（共 ${state.totalCount} 条）" else "书评")
        },
        leadingContent = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
        trailingContent = { TextButton(onClick = onClick) { Text("查看书评") } },
        colors = ListItemDefaults.colors(containerColor = LegadoTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookReviewSheet(
    state: BookReviewUiState,
    onDismiss: () -> Unit,
    onLoadMore: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (state.totalCount != null) "书评（共 ${state.totalCount} 条）" else "书评",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                state.loading && state.items.isEmpty() -> Text("正在加载书评…", modifier = Modifier.padding(vertical = 24.dp))
                state.items.isEmpty() -> Text("暂无书评", modifier = Modifier.padding(vertical = 24.dp))
                else -> LazyColumn {
                    items(state.items, key = { it.key }) { item ->
                        BookReviewItem(item)
                    }
                    if (state.hasMore) {
                        item {
                            Button(
                                onClick = onLoadMore,
                                enabled = !state.loadingMore,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            ) { Text(if (state.loadingMore) "正在加载…" else "加载更多") }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BookReviewItem(item: BookReviewItemUi) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.name.ifBlank { "匿名用户" }, fontWeight = FontWeight.SemiBold)
            item.time?.takeIf { it.isNotBlank() }?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        }
        if (item.badges.isNotEmpty()) {
            Text(item.badges.joinToString(" · "), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        item.content?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.padding(top = 4.dp))
        }
        val meta = buildList {
            item.likeCount?.takeIf { it > 0 }?.let { add("赞 $it") }
            item.replyCount?.takeIf { it > 0 }?.let { add("回复 $it") }
        }
        if (meta.isNotEmpty()) Text(meta.joinToString("  "), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        item.replies.forEach { reply ->
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp)) {
                Text("${reply.name.ifBlank { "匿名" }}：${reply.content.orEmpty()}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }
    }
}
