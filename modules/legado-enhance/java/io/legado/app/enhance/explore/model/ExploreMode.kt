package io.legado.app.enhance.explore.model

/**
 * 发现页展示模式
 */
enum class ExploreMode {
    FLAT,    // 平铺模式：支持多行分组过滤（Legado 默认行为）
    SECTION, // 分段模式：支持通过 Header 分段展示类目
    TREE     // 树状模式：支持递归层级导航（通过 children 字段判定）
}
