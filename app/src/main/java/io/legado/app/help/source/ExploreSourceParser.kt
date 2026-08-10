package io.legado.app.help.source

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.main.explore.ExploreNode
import kotlin.math.abs

/**
 * 通用发现源结构解析器。
 *
 * Legado 的 exploreUrl 最终通常只是一个有序 List<ExploreKind>，协议本身没有 level/parent。
 * 本构建器只使用“通用结构信号”恢复导航树，不针对任何书源名称或域名：
 *
 * 1. 无 URL/Action 的项目：视为逻辑 Header，并按原始顺序切分子区间。
 * 2. 连续 Header：自动识别父 Header -> 子 Header 的嵌套关系。
 * 3. 可点击整行项 + 后续窄项：在出现多个重复块时识别为父项。
 * 4. 重复尾项模式：例如 [分类, 完结, 连载] 重复 N 次，自动恢复为
 *    分类 -> [全部, 完结, 连载]。
 *
 * layout_flexBasisPercent 只作为“块边界辅助信号”，绝不直接等同于层级。
 */
object ExploreSourceParser {

    private const val MAX_REPEATED_BLOCK = 8
    private const val EPS = 0.02f

    fun parse(kinds: List<ExploreKind>): List<ExploreNode> {
        if (kinds.isEmpty()) return emptyList()
        return buildLevel(kinds)
    }

    private fun buildLevel(input: List<ExploreKind>): List<ExploreNode> {
        if (input.isEmpty()) return emptyList()

        buildByHeaders(input)?.let { return it }
        buildByFullWidthAnchors(input)?.let { return it }
        buildByRepeatedTail(input)?.let { return it }

        return input.map(::leafNode)
    }

    /**
     * Header 规则：仅 target 为空。
     *
     * 特别处理这种通用序列：
     *   父Header, 子Header, payload..., 子Header, payload..., 父Header, 子Header...
     * 其中“后面紧跟 Header 的 Header”会被识别为更高一级父节点。
     */
    private fun buildByHeaders(items: List<ExploreKind>): List<ExploreNode>? {
        val headerIndices = items.indices.filter { items[it].isGroupHeader() }
        if (headerIndices.isEmpty()) return null

        val parentHeaderIndices = headerIndices.filter { index ->
            index + 1 < items.size && items[index + 1].isGroupHeader()
        }

        // 至少两个父 Header 才认为存在稳定的“父分组 -> 子分组”重复结构。
        if (parentHeaderIndices.size >= 2) {
            val firstParent = parentHeaderIndices.first()
            val result = mutableListOf<ExploreNode>()
            if (firstParent > 0) {
                result += buildLevel(items.subList(0, firstParent))
            }

            parentHeaderIndices.forEachIndexed { idx, start ->
                val end = parentHeaderIndices.getOrNull(idx + 1) ?: items.size
                val parent = items[start]
                val childSlice = items.subList(start + 1, end)
                result += node(parent, buildLevel(childSlice))
            }
            return result
        }

        // 普通 Header + 连续子项。
        val result = mutableListOf<ExploreNode>()
        val firstHeader = headerIndices.first()
        if (firstHeader > 0) {
            result += buildLevel(items.subList(0, firstHeader))
        }

        headerIndices.forEachIndexed { idx, start ->
            val end = headerIndices.getOrNull(idx + 1) ?: items.size
            val header = items[start]
            val childSlice = items.subList(start + 1, end)
            result += node(header, buildLevel(childSlice))
        }
        return result
    }

    /**
     * 识别：父项(basis≈1, 有URL) + 一组窄项，且这种块至少出现两次。
     * basis 仅用于确认重复块边界，不作为层级本身。
     */
    private fun buildByFullWidthAnchors(items: List<ExploreKind>): List<ExploreNode>? {
        val anchors = items.indices.filter { index ->
            val item = items[index]
            item.targetUrl() != null && isFullWidth(item)
        }
        if (anchors.size < 2) return null

        val usefulAnchors = anchors.filterIndexed { i, start ->
            val end = anchors.getOrNull(i + 1) ?: items.size
            end - start > 1 && items.subList(start + 1, end).any { !isFullWidth(it) }
        }
        if (usefulAnchors.size < 2) return null

        // 必须从首个 anchor 开始形成稳定块；前缀保留为平级，避免误吞。
        val first = usefulAnchors.first()
        val result = mutableListOf<ExploreNode>()
        if (first > 0) result += items.subList(0, first).map(::leafNode)

        usefulAnchors.forEachIndexed { idx, start ->
            val end = usefulAnchors.getOrNull(idx + 1) ?: items.size
            val anchor = items[start]
            val childSlice = items.subList(start + 1, end)
            val children = buildLevel(childSlice)
            result += node(anchor, withDefaultChildIfUseful(anchor, children))
        }
        return result
    }

    /**
     * 识别重复尾模式：
     *   A, X, Y, B, X, Y, C, X, Y ...
     * => A->[全部,X,Y], B->[全部,X,Y], C->[全部,X,Y]
     *
     * 这是纯结构算法：要求 >=3 个完整块、尾部标题在各块相同、首项标题大多不同。
     */
    private fun buildByRepeatedTail(items: List<ExploreKind>): List<ExploreNode>? {
        val n = items.size
        if (n < 6) return null

        val maxBlock = minOf(MAX_REPEATED_BLOCK, n / 3)
        for (blockSize in 2..maxBlock) {
            if (n % blockSize != 0) continue
            val blocks = n / blockSize
            if (blocks < 3) continue

            val leaders = (0 until blocks).map { b -> cleanTitle(items[b * blockSize].title) }
            if (leaders.distinct().size < (blocks * 0.7).toInt().coerceAtLeast(2)) continue

            var repeatedTailCount = 0
            var tailStable = true
            for (pos in 1 until blockSize) {
                val values = (0 until blocks).map { b -> cleanTitle(items[b * blockSize + pos].title) }
                if (values.distinct().size == 1 && values.first().isNotBlank()) {
                    repeatedTailCount++
                } else {
                    tailStable = false
                    break
                }
            }
            if (!tailStable || repeatedTailCount != blockSize - 1) continue

            // 再用样式做弱校验：同一位置的 basis 应基本一致，降低误判率。
            val styleStable = (0 until blockSize).all { pos ->
                val basis = (0 until blocks).map { b -> basis(items[b * blockSize + pos]) }
                basis.maxOrNull()!! - basis.minOrNull()!! <= EPS || basis.all { it < 0f }
            }
            if (!styleStable) continue

            return (0 until blocks).map { b ->
                val start = b * blockSize
                val leader = items[start]
                val tail = items.subList(start + 1, start + blockSize)
                val children = buildLevel(tail)
                node(leader, withDefaultChildIfUseful(leader, children))
            }
        }
        return null
    }

    private fun withDefaultChildIfUseful(
        parent: ExploreKind,
        children: List<ExploreNode>
    ): List<ExploreNode> {
        val parentUrl = parent.targetUrl() ?: return children
        if (children.isEmpty()) return children
        if (children.any { isDefaultTitle(it.title) }) return children

        val childTitles = children.map { cleanTitle(it.title) }
        val looksLikeStatusDimension = childTitles.any { it in STATUS_TITLES }
        if (!looksLikeStatusDimension) return children

        val sampleStyle = children.firstOrNull()?.originalKind?.style
        val defaultNode = ExploreKind(
            title = "全部",
            url = parentUrl,
            style = sampleStyle
        )
        return listOf(leafNode(defaultNode)) + children
    }

    private fun leafNode(kind: ExploreKind) = node(kind, emptyList())

    private fun node(kind: ExploreKind, children: List<ExploreNode>) = ExploreNode(
        title = kind.title,
        url = kind.targetUrl(),
        children = children,
        originalKind = kind
    )

    private fun ExploreKind.isGroupHeader(): Boolean =
        url.isNullOrBlank() && action.isNullOrBlank()

    private fun ExploreKind.targetUrl(): String? =
        action?.takeIf { it.isNotBlank() } ?: url?.takeIf { it.isNotBlank() }

    private fun isFullWidth(kind: ExploreKind): Boolean = basis(kind) >= 1f - EPS

    private fun basis(kind: ExploreKind): Float = kind.style?.layout_flexBasisPercent ?: -1f

    private fun isDefaultTitle(title: String): Boolean = cleanTitle(title) in DEFAULT_TITLES

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("[\\[\\]【】()（）<>《》]"), "")
            .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
            .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
            .trim()
    }

    private val DEFAULT_TITLES = setOf("全部", "默认", "推荐")
    private val STATUS_TITLES = setOf(
        "完结", "连载", "完本", "在更", "已完结", "连载中", "Finished", "Loading"
    )
}
