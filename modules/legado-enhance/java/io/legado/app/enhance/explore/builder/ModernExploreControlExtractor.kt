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

    fun fromTreeRoot(nodes: List<ExploreNode>): List<SelectControl> =
        nodes.mapNotNull { node ->
            val kind = node.originalKind ?: return@mapNotNull null
            if (node.children.isNotEmpty() || kind.type != ExploreKind.Type.select) null
            else kind.toSelectControl(node.sourceIndex)
        }

    fun findSearchControl(kinds: List<ExploreKind>): SearchControl? {
        val textControls = kinds.mapIndexedNotNull { index, kind ->
            if (kind.type == ExploreKind.Type.text && kind.title.isNotBlank()) index to kind else null
        }
        if (textControls.isEmpty()) return null

        // 1. Strong match: button explicitly reads this text's InfoMap key.
        kinds.forEachIndexed { buttonIndex, button ->
            if (button.type != ExploreKind.Type.button || button.action.isNullOrBlank()) return@forEachIndexed
            val matched = textControls
                .asSequence()
                .filter { (textIndex, _) -> textIndex < buttonIndex }
                .filter { (_, text) -> actionReadsInfoMapKey(button.action.orEmpty(), text.title) }
                .maxByOrNull { (textIndex, _) -> textIndex }
                ?: return@forEachIndexed

            return SearchControl(matched.second, button, matched.first, buttonIndex)
        }

        if (textControls.size == 1) {
            val (textIndex, text) = textControls.single()

            // 2. Common Legado pattern: button only asks the explore UI to refresh.
            val refreshButton = kinds
                .mapIndexedNotNull { index, kind ->
                    if (
                        index > textIndex &&
                        kind.type == ExploreKind.Type.button &&
                        actionRefreshesExplore(kind.action.orEmpty())
                    ) index to kind else null
                }
                .minByOrNull { (index, _) -> index }

            if (refreshButton != null) {
                return SearchControl(text, refreshButton.second, textIndex, refreshButton.first)
            }

            // 3. Wrapped-function fallback: a single text followed by the next native button.
            // This catches doSearch()/load()/run() wrappers whose body is not visible here.
            // Explicit login/browser/form actions are excluded, and multi-text forms never enter
            // this branch, so account/password/captcha controls remain visible.
            val nextNative = kinds
                .mapIndexedNotNull { index, kind ->
                    if (index <= textIndex) return@mapIndexedNotNull null
                    if (
                        kind.type == ExploreKind.Type.text ||
                        kind.type == ExploreKind.Type.button ||
                        kind.type == ExploreKind.Type.toggle
                    ) index to kind else null
                }
                .minByOrNull { (index, _) -> index }

            if (
                nextNative?.second?.type == ExploreKind.Type.button &&
                !actionLooksLikeExplicitForm(nextNative.second.action.orEmpty())
            ) {
                return SearchControl(text, nextNative.second, textIndex, nextNative.first)
            }
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

        if (key.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) {
            val propertyRead = Regex("infoMap\\s*\\.\\s*${Regex.escape(key)}\\b")
            if (propertyRead.containsMatchIn(js)) return true
        }
        return false
    }

    internal fun actionRefreshesExplore(action: String): Boolean {
        if (action.isBlank()) return false
        val js = normalizeAction(action)
        return listOf(
            "refreshExplore",
            "reLoginView",
            "upLoginData",
            "reUiView",
            "upUiData",
        ).any { method ->
            Regex("(?:java\\s*\\.\\s*)?$method\\s*\\(").containsMatchIn(js)
        }
    }

    internal fun actionLooksLikeExplicitForm(action: String): Boolean {
        if (action.isBlank()) return false
        val js = normalizeAction(action)
        return Regex("(?:java\\s*\\.\\s*)?(?:login|showBrowser|openLogin)\\s*\\(", RegexOption.IGNORE_CASE)
            .containsMatchIn(js) ||
            Regex("(?:java\\s*\\.\\s*)?open\\s*\\(\\s*['\"]login['\"]", RegexOption.IGNORE_CASE)
                .containsMatchIn(js)
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
