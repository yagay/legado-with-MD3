package io.legado.app.enhance.explore.builder

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreMode
import io.legado.app.utils.GSON

/**
 * 现代发现页只负责识别“展示结构”，不重新解释书源协议。
 *
 * 原则：
 * 1. source.exploreKinds() 始终是书源行为的事实来源，保留原始顺序与完整 type/action/chars/default/style；
 * 2. 只有原始 JSON 明确提供 children 时才建立 TREE；
 * 3. 没有显式 children 时，仅依据纯展示 URL Header 和原始顺序恢复 SECTION；
 * 4. 不根据“男频/女频/热门/完结/排行榜”等名称猜测层级；
 * 5. 不过滤 text/button/toggle/select，也不生成“全部/分类”等伪 ExploreKind。
 */
object ModernExploreClassificationEngine {

    data class Result(
        val kinds: List<ExploreKind>,
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

        return Result(flatKinds, ExploreMode.FLAT)
    }

    private fun parseRawTree(json: String): List<ExploreKind> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            GSON.fromJson(json, JsonArray::class.java).mapNotNull(::parseNode)
        }.getOrDefault(emptyList())
    }

    private fun parseNode(element: JsonElement): ExploreKind? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        val kind = GSON.fromJson(obj, ExploreKind::class.java) ?: return null
        val children = obj.get("children")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?.mapNotNull(::parseNode)
            .orEmpty()
        return if (children.isEmpty()) kind else kind.copy(children = children)
    }

    /**
     * 只按书源原始顺序切分 Header 后的连续区间。
     * Header 本身只作为展示容器；它后面的原始 ExploreKind 不做任何类型转换或过滤。
     */
    private fun buildSectionTree(kinds: List<ExploreKind>): List<ExploreKind> {
        val result = mutableListOf<ExploreKind>()
        var currentHeader: ExploreKind? = null
        var currentChildren = mutableListOf<ExploreKind>()

        fun flushSection() {
            val header = currentHeader ?: return
            result += header.copy(children = currentChildren.toList())
            currentChildren = mutableListOf()
        }

        kinds.forEach { kind ->
            if (isSectionHeader(kind)) {
                flushSection()
                currentHeader = kind
            } else if (currentHeader == null) {
                // Header 前的项目保持根级原始项，不制造额外“分类”节点。
                result += kind
            } else {
                currentChildren += kind
            }
        }
        flushSection()
        return result
    }

    private fun List<ExploreKind>.hasChildrenDeep(): Boolean =
        any { !it.children.isNullOrEmpty() || it.children.orEmpty().hasChildrenDeep() }

    /**
     * text/button/toggle/select 没有 URL 很正常，它们是上游协议控件，不是分段 Header。
     * 只有默认 url 类型且没有任何可执行目标的条目才作为纯展示 Header。
     */
    private fun isSectionHeader(kind: ExploreKind): Boolean =
        kind.type == ExploreKind.Type.url &&
            kind.url.isNullOrBlank() &&
            kind.action.isNullOrBlank() &&
            kind.title.isNotBlank()
}
