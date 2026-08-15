package io.legado.app.enhance.explore.builder

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreMode
import io.legado.app.utils.GSON

/**
 * 现代发现页分类引擎。
 *
 * 对齐 yagay/legado:master 的现代发现分类入口：
 * 1. 优先读取 exploreKindsJson() 的原始 children；
 * 2. 原始 JSON 没有真实树结构时才回退 exploreKinds()；
 * 3. 恢复“频道 -> 分类 -> 状态/榜单”矩阵为真实树；
 * 4. 普通 SECTION 保留 SECTION 语义，运行时按原始 sourceIndex 排序筛选行；
 * 5. TREE 会过滤纯装饰标题、无 URL 且无 children 的伪节点，避免瀑布流出现空行、错层和重复分类；
 * 6. 所有节点始终保持书源原始出现顺序，不按名称重新排序。
 */
object ModernExploreClassificationEngine {

    data class Result(
        val kinds: List<ExploreKind>,
        val mode: ExploreMode
    )

    fun classify(flatKinds: List<ExploreKind>, rawJson: String): Result {
        val parsedTree = parseRawTree(rawJson)
        val base = parsedTree.takeIf { it.hasChildrenDeep() } ?: flatKinds

        // 特殊二维结构优先恢复为真正 TREE。
        buildSectionMatrixTree(base)?.let { rebuilt ->
            return Result(sanitizeTree(rebuilt), ExploreMode.TREE)
        }

        // 原书源已经提供 children：忠实保留路径，只移除 UI 不应显示的伪节点。
        if (base.hasChildrenDeep()) {
            return Result(sanitizeTree(base), ExploreMode.TREE)
        }

        return when (detectMode(base)) {
            ExploreMode.SECTION -> {
                val sectionTree = buildSectionNavigationTree(base)
                if (sectionTree.size >= 2) {
                    // 仍以树形数据承载“频道 -> 当前分类”的联动，但保留 SECTION 模式。
                    // UI 运行时据此像 legado 一样用原始 sourceIndex 对频道/分类/select 行做稳定排序。
                    Result(sanitizeTree(sectionTree), ExploreMode.SECTION)
                } else if (sectionTree.size == 1) {
                    // 只有一个标题分段时标题本身不需要成为额外一级，直接显示它下面的分类。
                    Result(sanitizeFlat(sectionTree.first().children.orEmpty()), ExploreMode.FLAT)
                } else {
                    Result(sanitizeFlat(base), ExploreMode.FLAT)
                }
            }
            ExploreMode.FLAT -> Result(sanitizeFlat(base), ExploreMode.FLAT)
            ExploreMode.TREE -> Result(sanitizeTree(base), ExploreMode.TREE)
        }
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

    private fun detectMode(kinds: List<ExploreKind>): ExploreMode {
        if (kinds.hasChildrenDeep()) return ExploreMode.TREE
        return if (kinds.any(::isSectionHeader)) ExploreMode.SECTION else ExploreMode.FLAT
    }

    private fun List<ExploreKind>.hasChildrenDeep(): Boolean =
        any { !it.children.isNullOrEmpty() || it.children.orEmpty().hasChildrenDeep() }

    /**
     * 与 legado TREE 的 visibleItems 规则一致：
     * 只有“有子节点”或“有有效目标 URL”的节点进入现代分类 UI。
     */
    private fun sanitizeTree(kinds: List<ExploreKind>): List<ExploreKind> {
        return kinds.mapNotNull { kind ->
            val children = sanitizeTree(kind.children.orEmpty())
            when {
                children.isNotEmpty() -> kind.copy(children = children)
                !targetUrl(kind).isNullOrBlank() -> kind.copy(children = null)
                else -> null
            }
        }
    }

    /** 平铺模式仅保留真正可打开分类 URL 的项。 */
    private fun sanitizeFlat(kinds: List<ExploreKind>): List<ExploreKind> =
        kinds.filter { !targetUrl(it).isNullOrBlank() }
            .map { it.copy(children = null) }

    /**
     * 普通 SECTION 转换为现代布局实际需要的导航结构：
     *
     * 男频/分组A
     *   分类1
     *   分类2
     * 女频/分组B
     *   分类3
     *   分类4
     *
     * Header 自身不作为 URL 项；URL 项只归属它之后、下一个 Header 之前的分组。
     */
    private fun buildSectionNavigationTree(kinds: List<ExploreKind>): List<ExploreKind> {
        data class Section(
            val header: ExploreKind,
            val children: MutableList<ExploreKind> = mutableListOf()
        )

        val sections = mutableListOf<Section>()
        var current: Section? = null
        val leading = mutableListOf<ExploreKind>()

        kinds.forEach { kind ->
            if (isSectionHeader(kind)) {
                current = Section(kind).also(sections::add)
                return@forEach
            }
            if (!targetUrl(kind).isNullOrBlank()) {
                if (current == null) leading += kind else current!!.children += kind
            }
        }

        val result = mutableListOf<ExploreKind>()
        if (leading.isNotEmpty()) {
            result += ExploreKind(title = "分类", children = leading)
        }
        sections.filter { it.children.isNotEmpty() }.forEach { section ->
            result += section.header.copy(
                url = null,
                action = null,
                children = section.children
            )
        }
        return result
    }

    /**
     * 对齐参考源码 buildDiscoverSectionMatrixTree：
     *
     * 男频
     *   玄幻
     *     推荐 / 完结 / 连载 ...
     *     热门 / 完结 / 连载 ...
     * 女频
     *   现言
     *     推荐 / 完结 / 连载 ...
     *
     * 这类书源实际上用样式和排列表示二维矩阵，没有 children；现代布局在这里恢复层级。
     */
    private fun buildSectionMatrixTree(kinds: List<ExploreKind>): List<ExploreKind>? {
        if (kinds.any { !it.children.isNullOrEmpty() }) return null

        val channels = mutableListOf<MatrixChannel>()
        var currentChannel: MatrixChannel? = null
        var currentCategory: MatrixCategory? = null

        kinds.forEach { kind ->
            val target = targetUrl(kind)
            val isHeader = target.isNullOrBlank() &&
                kind.action.isNullOrBlank() &&
                isFullWidth(kind) &&
                cleanTitle(kind.title).isNotBlank()

            if (isHeader) {
                val title = cleanTitle(kind.title)
                if (isChannelTitle(title)) {
                    currentChannel = MatrixChannel(kind, mutableListOf()).also(channels::add)
                    currentCategory = null
                } else if (currentChannel != null) {
                    currentCategory = MatrixCategory(kind, mutableListOf()).also {
                        currentChannel!!.categories += it
                    }
                }
                return@forEach
            }

            if (!target.isNullOrBlank() && currentCategory != null) {
                currentCategory!!.leaves += kind
            }
        }

        if (channels.size < 2 || channels.any { it.categories.isEmpty() }) return null
        val rebuilt = channels.mapNotNull { channel ->
            val categories = channel.categories.mapNotNull(::buildMatrixCategory)
            if (categories.isEmpty()) null else channel.header.copy(
                url = null,
                action = null,
                children = categories
            )
        }
        return rebuilt.takeIf { it.size >= 2 }
    }

    private fun buildMatrixCategory(category: MatrixCategory): ExploreKind? {
        val rankOrder = mutableListOf<String>()
        val statusOrder = mutableListOf<String>()
        val combinations = linkedMapOf<String, LinkedHashMap<String, ExploreKind>>()
        var currentRank: String? = null

        category.leaves.forEach { leaf ->
            val title = cleanTitle(leaf.title)
            if (isRankTitle(title)) {
                currentRank = title
                if (title !in rankOrder) rankOrder += title
            }
            val rank = currentRank ?: return@forEach
            val status = if (isRankTitle(title)) "全部" else title
            if (status.isBlank()) return@forEach
            if (status !in statusOrder) statusOrder += status
            combinations.getOrPut(status) { linkedMapOf() }[rank] = leaf
        }

        if (rankOrder.size < 2 || statusOrder.size < 2) return null
        if (statusOrder.any { combinations[it].orEmpty().size < 2 }) return null

        val statusNodes = statusOrder.mapNotNull { status ->
            val rankLeaves = rankOrder.mapNotNull { rank ->
                combinations[status]?.get(rank)?.copy(title = rank, children = null)
            }
            if (rankLeaves.isEmpty()) null else ExploreKind(
                title = status,
                children = rankLeaves
            )
        }
        if (statusNodes.isEmpty()) return null
        return category.header.copy(url = null, action = null, children = statusNodes)
    }

    private fun isSectionHeader(kind: ExploreKind): Boolean {
        if (!targetUrl(kind).isNullOrBlank() || !kind.action.isNullOrBlank()) return false
        val title = cleanTitle(kind.title)
        if (title.isBlank()) return false
        if (isFullWidth(kind)) return true
        val compact = title.replace("\\s+".toRegex(), "")
        val decorations = compact.count {
            it == '◎' || it == '●' || it == '○' || it == '◆' || it == '◇' || it == '=' || it == '-'
        }
        return decorations >= 2 || compact.endsWith("分类") ||
            compact.endsWith("排行") || compact.endsWith("排行榜")
    }

    private fun targetUrl(kind: ExploreKind): String? {
        val actionTarget = if (kind.type == ExploreKind.Type.url) kind.action else null
        return actionTarget?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
            ?: kind.url?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
    }

    private fun isFullWidth(kind: ExploreKind): Boolean {
        val style = kind.style()
        return style.layout_flexBasisPercent >= 0.95f ||
            (style.layout_flexGrow >= 1f && style.layout_flexBasisPercent < 0f)
    }

    private fun isChannelTitle(title: String): Boolean =
        title.contains("男频") || title.contains("女频") ||
            title.contains("男生频道") || title.contains("女生频道")

    private fun isRankTitle(title: String): Boolean =
        title in RANK_TITLES || title.endsWith("榜") || title.contains("排行")

    private fun cleanTitle(title: String): String = title
        .replace(Regex("[\\[\\]【】?（）<>《》]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
        .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
        .trim()

    private data class MatrixChannel(
        val header: ExploreKind,
        val categories: MutableList<MatrixCategory>
    )

    private data class MatrixCategory(
        val header: ExploreKind,
        val leaves: MutableList<ExploreKind>
    )

    private val RANK_TITLES = setOf(
        "推荐", "评分", "热门", "周榜", "月榜", "总榜", "日榜", "本周", "本月", "本日"
    )
}
