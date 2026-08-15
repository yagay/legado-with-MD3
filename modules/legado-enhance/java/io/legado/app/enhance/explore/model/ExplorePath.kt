package io.legado.app.enhance.explore.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * 发现页路径追踪
 */
@Immutable
data class ExplorePath(
    val selectedNodes: ImmutableList<ExploreNode> = persistentListOf()
) {
    val lastUrl: String? get() = selectedNodes.lastOrNull()?.url

    val displayPath: String get() = selectedNodes.joinToString(" · ") { it.title }

    fun append(node: ExploreNode): ExplorePath {
        // 如果点击的是已有层级，则裁剪后续层级并替换
        val index = selectedNodes.indexOfFirst { it.level == node.level }
        val newList = if (index != -1) {
            selectedNodes.subList(0, index).toMutableList()
        } else {
            selectedNodes.toMutableList()
        }
        newList.add(node)
        return copy(selectedNodes = newList.toImmutableList())
    }

    fun reset(): ExplorePath = copy(selectedNodes = persistentListOf())
}
