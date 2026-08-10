package io.legado.app.help.source

import io.legado.app.data.entities.rule.ExploreKind

/**
 * 兼容旧调用的门面。新代码统一使用 [ExploreSourceParser]。
 */
@Deprecated(
    message = "Use ExploreSourceParser",
    replaceWith = ReplaceWith("ExploreSourceParser.parse(kinds)")
)
object ExploreKindTreeBuilder {
    fun build(kinds: List<ExploreKind>): List<ExploreKind> = ExploreSourceParser.parse(kinds)
}
