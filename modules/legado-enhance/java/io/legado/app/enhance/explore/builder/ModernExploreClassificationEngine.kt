package io.legado.app.enhance.explore.builder

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreMode
import io.legado.app.enhance.explore.model.ExploreNode
import io.legado.app.utils.GSON

/**
 * 现代发现页只识别展示结构，不修改上游 ExploreKind 协议模型。
 *
 * - source.exploreKinds() 保持原始 type/action/chars/default/style 与顺序；
 * - 只有原始 JSON 明确提供 children 时才建立 TREE；
 * - 无显式树时，仅根据纯展示 Header 与原始顺序建立 SECTION；
 * - 不根据分类名称猜测频道、状态、榜单等业务语义。
 */
object ModernExploreClassificationEngine {

    data class Result(
        val nodes: List<ExploreNode>,
        val mode: ExploreMode
    )

    fun classify(flatKinds: List<ExploreKind>, rawJson: String): Result {
        val explicitTree = parseRawTree(rawJson)
            .takeIf { it.hasChildrenDeep() }

        if (explicitTree != null) {
            return Result(explicitTree, ExploreMode.TREE)
        }

        if (flatKinds.any(::isSectionHeader)) {
            return Result(buildSectionTree(flatKinds), ExploreMode.SECTION)
        }

        return Result(
            nodes = flatKinds.mapIndexed { index, kind -> kind.toNode(sourceIndex = index) },
            mode = ExploreMode.FLAT
        )
    }

    private fun parseRawTree(json: String): List<ExploreNode> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            GSON.fromJson(json, JsonArray::class.java)
                .mapIndexedNotNull { index, element -> parseNode(element, level = 0, sourceIndex = index) }
        }.getOrDefault(emptyList())
    }

    private fun parseNode(
        element: JsonElement,
        level: Int,
        sourceIndex: Int,
    ): ExploreNode? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        // Gson ignores the JSON-only children member because upstream ExploreKind does not own hierarchy.
        val kind = GSON.fromJson(obj, ExploreKind::class.java) ?: return null
        val children = obj.get("children")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?.mapIndexedNotNull { index, child ->
                parseNode(child, level = level + 1, sourceIndex = index)
            }
            .orEmpty()
        return kind.toNode(
            children = children,
            level = level,
            sourceIndex = sourceIndex,
        )
    }

    /**
     * 只按 Header 边界切分连续区间，原始 ExploreKind 不做转换或过滤。
     */
    private fun buildSectionTree(kinds: List<ExploreKind>): List<ExploreNode> {
        val result = mutableListOf<ExploreNode>()
        var currentHeader: IndexedValue<ExploreKind>? = null
        var currentChildren = mutableListOf<ExploreNode>()

        fun flushSection() {
            val indexed = currentHeader ?: return
            result += indexed.value.toNode(
                children = currentChildren.toList(),
                level = 0,
                sourceIndex = indexed.index,
            )
            currentChildren = mutableListOf()
        }

        kinds.withIndex().forEach { indexed ->
            val kind = indexed.value
            if (isSectionHeader(kind)) {
                flushSection()
                currentHeader = indexed
            } else if (currentHeader == null) {
                result += kind.toNode(level = 0, sourceIndex = indexed.index)
            } else {
                currentChildren += kind.toNode(level = 1, sourceIndex = indexed.index)
            }
        }
        flushSection()
        return result
    }

    private fun List<ExploreNode>.hasChildrenDeep(): Boolean =
        any { it.children.isNotEmpty() || it.children.hasChildrenDeep() }

    private fun ExploreKind.toNode(
        children: List<ExploreNode> = emptyList(),
        level: Int = 0,
        sourceIndex: Int = -1,
    ): ExploreNode = ExploreNode(
        title = title,
        url = modernTargetUrl(),
        children = children,
        originalKind = this,
        level = level,
        sourceIndex = sourceIndex,
    )

    private fun isSectionHeader(kind: ExploreKind): Boolean =
        kind.type == ExploreKind.Type.url &&
            kind.modernTargetUrl().isNullOrBlank() &&
            kind.title.isNotBlank()
}
