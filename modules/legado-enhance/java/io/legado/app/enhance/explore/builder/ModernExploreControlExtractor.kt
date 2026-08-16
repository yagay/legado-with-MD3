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

        // 1. Strong match: button explicitly reads this text's InfoMap key.
        kinds.forEachIndexed { buttonIndex, button ->
            if (button.type != ExploreKind.Type.button || button.action.isNullOrBlank()) return@forEachIndexed
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
                return SearchControl(
                    textKind = text,
                    buttonKind = refreshButton.second,
                    textSourceIndex = textIndex,
                    buttonSourceIndex = refreshButton.first,
                )
            }

            // 3. Wrapped-function fallback: many shared sources hide the actual InfoMap/refresh
            // work inside doSearch()/load()/run() helpers. If the non-category control block has
            // only one text field and the next native control is a button, the pair is structural
            // enough to reuse without guessing from its visible title. Multi-text forms never use
            // this fallback, so account/password/captcha forms remain visible.
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

            if (nextNative?.second?.type == ExploreKind.Type.button) {
                return SearchControl(
                    textKind = text,
                    buttonKind = nextNative.second,
                    textSourceIndex = textIndex,
                    buttonSourceIndex = nextNative.first,
                )
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
