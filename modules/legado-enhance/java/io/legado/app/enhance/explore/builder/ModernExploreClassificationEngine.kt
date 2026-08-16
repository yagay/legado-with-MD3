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
 * 3. 没有显式 children 时，仅依据无可执行目标的 Header 判断 SECTION/FLAT；
 * 4. 不根据“男频/女频/热门/完结/排行榜”等名称猜测层级；
 * 5. 不过滤 text/button/toggle/select，也不生成“全部/分类”等伪 ExploreKind。
 *
 * 这样增强层只增加现代布局所需的结构元数据，真正的书源语义继续由
 * HapeLee/TeamLegado 的 ExploreKind、InfoMap、ExploreKindUiUseCase 与 WebBook 处理。
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

        val mode = if (flatKinds.any(::isSectionHeader)) {
            ExploreMode.SECTION
        } else {
            ExploreMode.FLAT
        }
        return Result(flatKinds, mode)
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

    private fun List<ExploreKind>.hasChildrenDeep(): Boolean =
        any { !it.children.isNullOrEmpty() || it.children.orEmpty().hasChildrenDeep() }

    private fun isSectionHeader(kind: ExploreKind): Boolean =
        kind.url.isNullOrBlank() && kind.action.isNullOrBlank() && kind.title.isNotBlank()
}
