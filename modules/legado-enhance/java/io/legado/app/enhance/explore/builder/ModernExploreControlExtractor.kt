package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 现代发现页书源自定义筛选控件提取器。
 *
 * 对齐 yagay/legado:master 的 buildDiscoverTagItems / TREE rootControls 行为：
 * - select/chars 不属于分类树，不参与 TREE / SECTION / FLAT 层级判断；
 * - 只把真正的 select 作为独立筛选行；
 * - 保留书源原始 sourceIndex，后续可和频道/分类按原始顺序稳定合并；
 * - 保留 default/action/chars，点击后由运行时写入 infoMap 并按书源规则刷新分类。
 */
object ModernExploreControlExtractor {

    data class SelectControl(
        val kind: ExploreKind,
        val sourceIndex: Int,
        val title: String,
        val options: List<String>,
        val defaultValue: String?
    )

    /**
     * 非树形书源：直接从原始 exploreKinds() 中提取全局 select。
     */
    fun fromFlatKinds(kinds: List<ExploreKind>): List<SelectControl> =
        kinds.mapIndexedNotNull { index, kind -> kind.toSelectControl(index) }

    /**
     * TREE 模式与 legado 一致：只取根级“无 children 且非 url”的控制项。
     * 子树里的节点仍属于路径，不会被错误提升成全局筛选。
     */
    fun fromTreeRoot(kinds: List<ExploreKind>): List<SelectControl> =
        kinds.mapIndexedNotNull { index, kind ->
            if (!kind.children.isNullOrEmpty() || kind.type == ExploreKind.Type.url) {
                null
            } else {
                kind.toSelectControl(index)
            }
        }

    private fun ExploreKind.toSelectControl(sourceIndex: Int): SelectControl? {
        if (type != ExploreKind.Type.select) return null
        val values = chars.orEmpty()
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (values.isEmpty()) return null
        return SelectControl(
            kind = copy(type = ExploreKind.Type.select),
            sourceIndex = sourceIndex,
            title = cleanTitle(title).ifBlank { title },
            options = values,
            defaultValue = default?.takeIf { it.isNotBlank() } ?: values.firstOrNull()
        )
    }

    private fun cleanTitle(value: String): String = value
        .replace(Regex("[\\[\\]【】?（）<>《》]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
        .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
        .trim()
}
