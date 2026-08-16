package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 现代发现布局自己的结构辅助函数。
 * 这些判断只服务 enhance，不向上游 ExploreKind 增加层级状态。
 */
internal fun ExploreKind.modernTargetUrl(): String? {
    // Keep the same protocol semantics as the upstream ExploreKind URL item:
    // a URL item opens kind.url. action belongs to native control execution and
    // must never replace the list URL merely because both fields are present.
    return url?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
}

internal fun ExploreKind.isModernSectionHeader(): Boolean =
    type == ExploreKind.Type.url &&
        modernTargetUrl().isNullOrBlank() &&
        title.isNotBlank()
