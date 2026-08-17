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
 * - 无显式树时，优先恢复旧式书源通过“满行空 URL 标题 + 标题外框”表达的视觉层级；
 * - 对层级/区块内完整的二维 URL 组合，仅在标题模式和 URL 参数同时证明为笛卡尔积时拆成两个选择维度；
 * - 其余平面书源仍仅根据纯展示 Header 与原始顺序建立 SECTION；
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

        buildVisualHierarchy(flatKinds)?.let { hierarchy ->
            return Result(hierarchy, ExploreMode.SECTION)
        }

        if (flatKinds.any(::isSectionHeader)) {
            return Result(buildSectionTree(flatKinds), ExploreMode.SECTION)
        }

        return Result(
            nodes = flatKinds.mapIndexed { index, kind ->
                kind.toNode(sourceIndex = index, sourceKey = index.toString())
            },
            mode = ExploreMode.FLAT
        )
    }

    private fun parseRawTree(json: String): List<ExploreNode> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            GSON.fromJson(json, JsonArray::class.java)
                .mapIndexedNotNull { index, element ->
                    parseNode(
                        element = element,
                        level = 0,
                        sourceIndex = index,
                        sourceKey = index.toString(),
                    )
                }
        }.getOrDefault(emptyList())
    }

    private fun parseNode(
        element: JsonElement,
        level: Int,
        sourceIndex: Int,
        sourceKey: String,
    ): ExploreNode? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        val kind = GSON.fromJson(obj, ExploreKind::class.java) ?: return null
        val children = obj.get("children")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?.mapIndexedNotNull { index, child ->
                parseNode(
                    element = child,
                    level = level + 1,
                    sourceIndex = index,
                    sourceKey = "$sourceKey.$index",
                )
            }
            .orEmpty()
        return kind.toNode(
            children = children,
            level = level,
            sourceIndex = sourceIndex,
            sourceKey = sourceKey,
        )
    }

    /**
     * 兼容旧式 Legado 书源的平面视觉层级。
     *
     * 一些书源并不提供 children，而是连续输出：
     * 一级满行标题 -> 二级满行标题 -> 若干真实 URL -> 二级满行标题 ...
     * 并通过标题两侧不同的装饰外框区分一级/二级。默认布局只按 flex/style 排版，
     * 现代布局需要恢复这个已经存在的视觉信息，不能把每个空 URL 都当成同级 Header。
     *
     * 这里只使用协议已有的 URL/style、原始顺序和标题外框，不识别“男频/女频”等业务名称。
     */
    private fun buildVisualHierarchy(kinds: List<ExploreKind>): List<ExploreNode>? {
        if (kinds.size < 3) return null

        val framedHeaders = kinds.withIndex()
            .filter { isFullWidthHeader(it.value) }
            .map { indexed -> indexed to titleFrameSignature(indexed.value.title) }
            .filter { (_, signature) -> signature.isNotBlank() }

        if (framedHeaders.size < 3) return null

        val occurrences = framedHeaders.groupingBy { it.second }.eachCount()
        val parentSignatures = framedHeaders.asSequence()
            .filter { (_, signature) -> (occurrences[signature] ?: 0) >= 2 }
            .filter { (indexed, signature) ->
                val next = kinds.getOrNull(indexed.index + 1) ?: return@filter false
                isFullWidthHeader(next) && titleFrameSignature(next.title) != signature
            }
            .map { it.second }
            .toSet()

        if (parentSignatures.size != 1) return null
        val parentSignature = parentSignatures.single()

        val result = mutableListOf<ExploreNode>()
        var parentKind: IndexedValue<ExploreKind>? = null
        var parentChildren = mutableListOf<ExploreNode>()
        var categoryKind: IndexedValue<ExploreKind>? = null
        var categoryChildren = mutableListOf<ExploreNode>()

        fun flushCategory() {
            val indexed = categoryKind ?: return
            parentChildren += indexed.value.toNode(
                children = ModernExploreMatrixFactorizer.factor(categoryChildren.toList()),
                level = 1,
                sourceIndex = indexed.index,
                sourceKey = indexed.index.toString(),
            )
            categoryKind = null
            categoryChildren = mutableListOf()
        }

        fun flushParent() {
            val indexed = parentKind ?: return
            flushCategory()
            result += indexed.value.toNode(
                children = parentChildren.toList(),
                level = 0,
                sourceIndex = indexed.index,
                sourceKey = indexed.index.toString(),
            )
            parentKind = null
            parentChildren = mutableListOf()
        }

        kinds.withIndex().forEach { indexed ->
            val kind = indexed.value
            val isHeader = isFullWidthHeader(kind)
            val signature = if (isHeader) titleFrameSignature(kind.title) else ""

            when {
                isHeader && signature == parentSignature -> {
                    flushParent()
                    parentKind = indexed
                }

                parentKind != null && isHeader -> {
                    flushCategory()
                    categoryKind = indexed
                }

                parentKind != null && categoryKind != null -> {
                    categoryChildren += kind.toNode(
                        level = 2,
                        sourceIndex = indexed.index,
                        sourceKey = indexed.index.toString(),
                    )
                }

                parentKind != null -> {
                    parentChildren += kind.toNode(
                        level = 1,
                        sourceIndex = indexed.index,
                        sourceKey = indexed.index.toString(),
                    )
                }

                else -> {
                    result += kind.toNode(
                        level = 0,
                        sourceIndex = indexed.index,
                        sourceKey = indexed.index.toString(),
                    )
                }
            }
        }
        flushParent()

        return result.takeIf { nodes ->
            nodes.count { it.originalKind?.let(::isFullWidthHeader) == true && it.children.isNotEmpty() } >= 2
        }
    }

    /**
     * 只按 Header 边界切分连续区间，原始 ExploreKind 不做转换或过滤。
     * 区间本身若恰好构成经 URL 参数和标题重复模式双重验证的完整二维矩阵，
     * 复用同一个 MatrixFactorizer 恢复维度；验证失败则原样保留。
     */
    private fun buildSectionTree(kinds: List<ExploreKind>): List<ExploreNode> {
        val result = mutableListOf<ExploreNode>()
        var currentHeader: IndexedValue<ExploreKind>? = null
        var currentChildren = mutableListOf<ExploreNode>()

        fun flushSection() {
            val indexed = currentHeader ?: return
            result += indexed.value.toNode(
                children = ModernExploreMatrixFactorizer.factor(currentChildren.toList()),
                level = 0,
                sourceIndex = indexed.index,
                sourceKey = indexed.index.toString(),
            )
            currentChildren = mutableListOf()
        }

        kinds.withIndex().forEach { indexed ->
            val kind = indexed.value
            if (isSectionHeader(kind)) {
                flushSection()
                currentHeader = indexed
            } else if (currentHeader == null) {
                result += kind.toNode(level = 0, sourceIndex = indexed.index, sourceKey = indexed.index.toString())
            } else {
                currentChildren += kind.toNode(level = 1, sourceIndex = indexed.index, sourceKey = indexed.index.toString())
            }
        }
        flushSection()
        return result
    }

    private fun isFullWidthHeader(kind: ExploreKind): Boolean {
        if (!isSectionHeader(kind)) return false
        val style = kind.style()
        return style.layout_wrapBefore || style.layout_flexBasisPercent >= 1f
    }

    private fun titleFrameSignature(title: String): String {
        val value = title.trim()
        if (value.isEmpty()) return ""
        val first = value.indexOfFirst(::isTitleContentChar)
        val last = value.indexOfLast(::isTitleContentChar)
        if (first < 0 || last < first) return ""
        val prefix = value.substring(0, first).replace(Regex("\\s+"), " ").trim()
        val suffix = value.substring(last + 1).replace(Regex("\\s+"), " ").trim()
        if (prefix.isEmpty() && suffix.isEmpty()) return ""
        return "$prefix|$suffix"
    }

    private fun isTitleContentChar(ch: Char): Boolean =
        (ch.code <= 0x7f && ch.isLetterOrDigit()) ||
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HIRAGANA ||
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.KATAKANA ||
            Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.HANGUL_SYLLABLES

    private fun List<ExploreNode>.hasChildrenDeep(): Boolean =
        any { it.children.isNotEmpty() || it.children.hasChildrenDeep() }

    private fun ExploreKind.toNode(
        children: List<ExploreNode> = emptyList(),
        level: Int = 0,
        sourceIndex: Int = -1,
        sourceKey: String = sourceIndex.takeIf { it >= 0 }?.toString().orEmpty(),
    ): ExploreNode = ExploreNode(
        title = title,
        url = modernTargetUrl(),
        children = children,
        originalKind = this,
        level = level,
        sourceIndex = sourceIndex,
        sourceKey = sourceKey,
    )

    private fun isSectionHeader(kind: ExploreKind): Boolean =
        kind.type == ExploreKind.Type.url &&
            kind.modernTargetUrl().isNullOrBlank() &&
            kind.title.isNotBlank()
}
