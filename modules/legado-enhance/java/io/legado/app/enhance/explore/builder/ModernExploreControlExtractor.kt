package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreNode

/**
 * 现代发现页书源自定义控件提取器。
 *
 * 控件行为继续由 original ExploreKind 承载；enhance 只识别展示关系，
 * 不根据“搜索/登录”等按钮文字猜业务语义。
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
     * 书源发现页中可由现代顶栏输入框接管的 text + button 组合。
     *
     * 只有 button.action 明确从 InfoMap 读取该 text.title 对应的 key 时才匹配。
     * 因此相邻的账号/密码/验证码输入框不会仅凭位置或显示名称被误判成搜索框。
     */
    data class SearchControl(
        val textKind: ExploreKind,
        val buttonKind: ExploreKind,
        val textSourceIndex: Int,
        val buttonSourceIndex: Int,
    ) {
        val hiddenSourceIndexes: Set<Int>
            get() = setOf(textSourceIndex, buttonSourceIndex)
    }

    fun fromFlatKinds(kinds: List<ExploreKind>): List<SelectControl> =
        kinds.mapIndexedNotNull { index, kind -> kind.toSelectControl(index) }

    /** TREE 只提升根级、无子节点的 select 控件。 */
    fun fromTreeRoot(nodes: List<ExploreNode>): List<SelectControl> =
        nodes.mapNotNull { node ->
            val kind = node.originalKind ?: return@mapNotNull null
            if (node.children.isNotEmpty() || kind.type != ExploreKind.Type.select) {
                null
            } else {
                kind.toSelectControl(node.sourceIndex)
            }
        }

    fun findSearchControl(kinds: List<ExploreKind>): SearchControl? {
        val textControls = kinds.mapIndexedNotNull { index, kind ->
            if (kind.type == ExploreKind.Type.text && kind.title.isNotBlank()) index to kind else null
        }
        if (textControls.isEmpty()) return null

        kinds.forEachIndexed { buttonIndex, button ->
            if (button.type != ExploreKind.Type.button || button.action.isNullOrBlank()) return@forEachIndexed

            // Prefer the nearest preceding text control whose InfoMap key is actually read
            // by this button action. Position only breaks ties; it never establishes semantics.
            val matched = textControls
                .asSequence()
                .filter { (textIndex, _) -> textIndex < buttonIndex }
                .filter { (_, text) -> actionReadsInfoMapKey(button.action.orEmpty(), text.title) }
                .maxByOrNull { (textIndex, _) -> textIndex }
                ?: return@forEachIndexed

            return SearchControl(
                textKind = matched.second,
                buttonKind = button,
                textSourceIndex = matched.first,
                buttonSourceIndex = buttonIndex,
            )
        }
        return null
    }

    internal fun actionReadsInfoMapKey(action: String, key: String): Boolean {
        if (key.isBlank() || action.isBlank()) return false
        val js = normalizeAction(action)
        if (!js.contains("infoMap")) return false

        val escaped = Regex.escape(key)
        val quotedKey = "(['\"])$escaped\\1"
        val getCall = Regex("infoMap\\s*\\.\\s*get\\s*\\(\\s*$quotedKey\\s*\\)")
        val bracketRead = Regex("infoMap\\s*\\[\\s*$quotedKey\\s*]")
        if (getCall.containsMatchIn(js) || bracketRead.containsMatchIn(js)) return true

        // Some sources use property notation for simple identifier keys.
        if (key.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) {
            val propertyRead = Regex("infoMap\\s*\\.\\s*${Regex.escape(key)}\\b")
            if (propertyRead.containsMatchIn(js)) return true
        }
        return false
    }

    private fun normalizeAction(action: String): String = when {
        action.startsWith("<js>") && action.endsWith("</js>") ->
            action.removePrefix("<js>").removeSuffix("</js>")
        action.startsWith("{{") && action.endsWith("}}") ->
            action.removePrefix("{{").removeSuffix("}}")
        else -> action
    }

    private fun ExploreKind.toSelectControl(sourceIndex: Int): SelectControl? {
        if (type != ExploreKind.Type.select) return null
        val values = chars.orEmpty()
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (values.isEmpty()) return null
        return SelectControl(
            kind = this,
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
