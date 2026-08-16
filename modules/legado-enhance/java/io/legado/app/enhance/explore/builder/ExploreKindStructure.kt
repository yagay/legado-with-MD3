package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 现代发现布局自己的结构辅助函数。
 *
 * 这些判断只服务 enhance 的展示树，不属于上游 ExploreKind 协议模型。
 */
internal fun ExploreKind.hasModernChildren(): Boolean = !children.isNullOrEmpty()

internal fun ExploreKind.modernTargetUrl(): String? {
    val actionTarget = if (type == ExploreKind.Type.url) action else null
    return actionTarget?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
        ?: url?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
}

internal fun ExploreKind.isModernSectionHeader(): Boolean =
    type == ExploreKind.Type.url &&
        modernTargetUrl().isNullOrBlank() &&
        !hasModernChildren() &&
        title.isNotBlank()
