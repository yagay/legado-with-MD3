package io.legado.app.ui.book.info

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    onImageClick: (String) -> Unit,
    onAudioClick: (String) -> Unit,
    onLoadReplies: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (state.totalCount != null) "书评（共 ${state.totalCount} 条）" else "书评",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                state.loading && state.items.isEmpty() -> Text("正在加载书评…", modifier = Modifier.padding(vertical = 24.dp))
                state.items.isEmpty() -> Text("暂无书评", modifier = Modifier.padding(vertical = 24.dp))
                else -> LazyColumn {
                    items(state.items, key = { it.key }) { item ->
                        BookReviewItem(item = item, onImageClick = onImageClick, onAudioClick = onAudioClick, onLoadReplies = onLoadReplies)
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
private fun BookReviewItem(
    item: BookReviewItemUi,
    onImageClick: (String) -> Unit,
    onAudioClick: (String) -> Unit,
    onLoadReplies: (String) -> Unit,
) {
    var repliesExpanded by rememberSaveable(item.key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReviewAvatar(item.avatarUrl, item.name)
            Column(Modifier.weight(1f)) {
                Text(item.name.ifBlank { "匿名用户" }, fontWeight = FontWeight.SemiBold)
                item.time?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (item.badges.isNotEmpty()) {
            Text(
                item.badges.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 46.dp, top = 2.dp),
            )
        }
        item.content?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.padding(start = 46.dp, top = 6.dp))
        }
        item.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = "评论图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(start = 46.dp, top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onImageClick(imageUrl) },
            )
        }
        item.audioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->
            TextButton(
                onClick = { onAudioClick(audioUrl) },
                modifier = Modifier.padding(start = 34.dp, top = 2.dp),
            ) {
                Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null)
                Text("播放语音", modifier = Modifier.padding(start = 6.dp))
            }
        }
        val replyCount = item.replyCount?.takeIf { it > 0 }
        item.likeCount?.takeIf { it > 0 }?.let { likeCount ->
            Text(
                "赞 $likeCount",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 46.dp, top = 6.dp),
            )
        }
        val hasReplyEntry = replyCount != null || item.replies.isNotEmpty() || item.canLoadMoreReplies || item.repliesLoading
        if (hasReplyEntry) {
            TextButton(
                onClick = {
                    val opening = !repliesExpanded
                    repliesExpanded = opening
                    if (opening && item.replies.isEmpty() && !item.repliesLoading) {
                        onLoadReplies(item.key)
                    }
                },
                modifier = Modifier.padding(start = 34.dp),
            ) {
                val count = replyCount ?: item.replies.size
                Text(if (repliesExpanded) "收起回复" else "回复 $count  ·  查看")
            }
            if (repliesExpanded) {
                Surface(
                    color = LegadoTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 46.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (item.repliesLoading && item.replies.isEmpty()) {
                            Text("正在加载回复…", style = MaterialTheme.typography.bodySmall)
                        } else if (!item.repliesLoading && item.replies.isEmpty()) {
                            Text("暂无可显示的回复内容", style = MaterialTheme.typography.bodySmall)
                        }
                        item.replies.forEach { reply ->
                            ReviewReplyItem(reply, onImageClick, onAudioClick)
                        }
                        if (item.canLoadMoreReplies && item.replies.isNotEmpty()) {
                            TextButton(
                                onClick = { onLoadReplies(item.key) },
                                enabled = !item.repliesLoading,
                            ) {
                                Text(if (item.repliesLoading) "正在加载…" else "加载更多回复")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewReplyItem(reply: BookReviewItemUi, onImageClick: (String) -> Unit, onAudioClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReviewAvatar(reply.avatarUrl, reply.name, size = 26)
            Text(reply.name.ifBlank { "匿名" }, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
            reply.time?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
        }
        reply.content?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 34.dp, top = 3.dp))
        }
        reply.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = "回复图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .padding(start = 34.dp, top = 6.dp)
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onImageClick(imageUrl) },
            )
        }
        reply.audioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->
            TextButton(onClick = { onAudioClick(audioUrl) }, modifier = Modifier.padding(start = 22.dp)) {
                Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null)
                Text("播放语音", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun ReviewAvatar(avatarUrl: String?, name: String, size: Int = 36) {
    val modifier = Modifier.size(size.dp).clip(CircleShape)
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = if (name.isBlank()) "用户头像" else "$name 的头像",
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = LegadoTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size((size * 0.68f).dp),
            )
        }
    }
}
