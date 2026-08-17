package io.legado.app.enhance.explore.builder

import io.legado.app.enhance.explore.model.ExploreNode

/**
 * 将旧式书源按顺序展开的二维 URL 组合恢复成两个独立选择维度。
 *
 * 例如源数据可能平铺为：
 * A0/B0, B1, B2, A1/B0, B1, B2, A2/B0, B1, B2。
 * 只有标题重复模式与 URL 查询参数同时证明它是完整 A×B 笛卡尔积时才拆分；
 * 否则原样返回，避免对普通分类做业务语义猜测。
 *
 * 第一维仍属于当前真实分类，因此其行标题可以继承父分类名称；
 * 第二维只是为了 URL 映射挂载在第一维之下，语义上是独立筛选维度，
 * 通过一个单节点容器与父选择解耦，避免把“推荐/评分/热门”等选项名误当成下一行类别名。
 */
internal object ModernExploreMatrixFactorizer {

    fun factor(items: List<ExploreNode>): List<ExploreNode> {
        if (items.size < 4 || items.any { it.children.isNotEmpty() || it.url.isNullOrBlank() }) {
            return items
        }

        // URL parsing and parameter-shape discovery do not depend on blockSize. Previously these
        // were repeated for every candidate block size, which becomes expensive on sources with
        // hundreds of discover entries. Parse once, then only validate the candidate arrangement.
        val parsed = items.map { parseUrl(it.url!!) ?: return items }
        if (parsed.map { it.base }.distinct().size != 1) return items

        val commonKeys = parsed.map { it.query.keys }.reduce { left, right -> left intersect right }
        val varyingKeys = commonKeys.filter { key ->
            parsed.asSequence().map { it.query[key] }.distinct().take(3).count() > 1
        }
        if (varyingKeys.size != 2) return items

        val cleanTitles = items.map { cleanDimensionTitle(it.title) }

        for (blockSize in 2..(items.size / 2)) {
            if (items.size % blockSize != 0) continue
            val blockCount = items.size / blockSize
            if (blockCount < 2) continue
            if (!hasRepeatedTitlePattern(cleanTitles, blockSize, blockCount)) continue

            val blockKey = varyingKeys.singleOrNull { key ->
                (0 until blockCount).all { block ->
                    val start = block * blockSize
                    val value = parsed[start].query[key]
                    (0 until blockSize).all { offset -> parsed[start + offset].query[key] == value }
                } && (0 until blockCount)
                    .map { block -> parsed[block * blockSize].query[key] }
                    .distinct()
                    .size == blockCount
            } ?: continue

            val positionKey = varyingKeys.singleOrNull { key ->
                key != blockKey &&
                    (0 until blockSize).all { offset ->
                        val value = parsed[offset].query[key]
                        (0 until blockCount).all { block -> parsed[block * blockSize + offset].query[key] == value }
                    } && (0 until blockSize)
                    .map { offset -> parsed[offset].query[key] }
                    .distinct()
                    .size == blockSize
            } ?: continue

            val pairs = parsed.map { it.query[blockKey] to it.query[positionKey] }
            if (pairs.distinct().size != items.size) continue

            return (0 until blockCount).map { block ->
                val start = block * blockSize
                val head = items[start]
                val independentLeaves = (0 until blockSize).map { offset ->
                    val leaf = items[start + offset]
                    leaf.copy(
                        title = if (offset == 0) {
                            defaultPositionTitle(parsed[start + offset].query[positionKey])
                        } else {
                            cleanTitles[start + offset]
                        },
                        level = leaf.level + 2,
                        sourceKey = "${leaf.sourceKey}.matrixB",
                    )
                }
                ExploreNode(
                    title = cleanTitles[start],
                    url = null,
                    children = listOf(
                        ExploreNode(
                            title = "分类",
                            url = null,
                            children = independentLeaves,
                            originalKind = null,
                            level = head.level + 1,
                            sourceIndex = head.sourceIndex,
                            sourceKey = "${head.sourceKey}.matrixIndependent",
                        )
                    ),
                    originalKind = null,
                    level = head.level,
                    sourceIndex = head.sourceIndex,
                    sourceKey = "${head.sourceKey}.matrixA",
                )
            }
        }

        return items
    }

    private fun hasRepeatedTitlePattern(
        cleanTitles: List<String>,
        blockSize: Int,
        blockCount: Int,
    ): Boolean {
        val headTitles = (0 until blockCount)
            .map { block -> cleanTitles[block * blockSize] }
        if (headTitles.any(String::isBlank) || headTitles.distinct().size != blockCount) return false

        return (1 until blockSize).all { offset ->
            val titles = (0 until blockCount)
                .map { block -> cleanTitles[block * blockSize + offset] }
            titles.firstOrNull()?.isNotBlank() == true && titles.distinct().size == 1
        }
    }

    private data class ParsedUrl(
        val base: String,
        val query: Map<String, String>,
    )

    private fun parseUrl(url: String): ParsedUrl? {
        val question = url.indexOf('?')
        if (question <= 0 || question == url.lastIndex) return null
        val base = url.substring(0, question)
        val queryPart = url.substring(question + 1).substringBefore('#')
        val query = linkedMapOf<String, String>()
        queryPart.split('&').forEach { part ->
            if (part.isBlank()) return@forEach
            val separator = part.indexOf('=')
            val key = if (separator >= 0) part.substring(0, separator) else part
            val value = if (separator >= 0) part.substring(separator + 1) else ""
            if (key.isBlank() || query.put(key, value) != null) return null
        }
        return ParsedUrl(base, query)
    }

    private fun cleanDimensionTitle(title: String): String {
        var value = title.trim()
        val wrappers = listOf("[" to "]", "【" to "】", "(" to ")", "（" to "）")
        wrappers.forEach { (left, right) ->
            if (value.startsWith(left) && value.endsWith(right) && value.length > left.length + right.length) {
                value = value.substring(left.length, value.length - right.length).trim()
            }
        }
        return value
    }

    private fun defaultPositionTitle(rawValue: String?): String = when {
        rawValue.isNullOrBlank() -> "全部"
        rawValue.equals("all", ignoreCase = true) -> "全部"
        else -> "默认"
    }
}
