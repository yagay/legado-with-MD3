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
        val sourceKey: String,
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

    data class NativeControlsResult(
        val searchControl: SearchControl?,
        val visibleControls: List<ExploreKind>,
    )

    /**
     * Snapshot of the exact ExploreKind order used for the current modern discovery page.
     * It lets the Compose layer interleave select/category rows with native button/text/toggle
     * rows without changing the upstream ExploreKind model or teaching the UI source semantics.
     */
    @Volatile
    private var sourceOrderSnapshot: List<ExploreKind> = emptyList()

    fun fromFlatKinds(kinds: List<ExploreKind>): List<SelectControl> =
        kinds.mapIndexedNotNull { index, kind ->
            kind.toSelectControl(index, index.toString())
        }

    /**
     * Explicit tree JSON can place select controls at any depth. Walk the whole
     * tree instead of only the root so nested status/ranking controls are not
     * silently lost in the waterfall layout.
     */
    fun fromTreeRoot(nodes: List<ExploreNode>): List<SelectControl> {
        val result = mutableListOf<SelectControl>()
        val stack = ArrayDeque<ExploreNode>()
        nodes.asReversed().forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node.originalKind
                ?.toSelectControl(node.sourceIndex, node.sourceKey)
                ?.let(result::add)
            node.children.asReversed().forEach(stack::addLast)
        }
        return result
    }

    /**
     * Produces the exact native-control list that the modern UI should render.
     * Search recognition and hiding are intentionally performed in one pass so the UI cannot
     * observe a SearchControl whose original text/button pair was filtered by separate state.
     */
    fun flattenOriginalKinds(nodes: List<ExploreNode>): List<ExploreKind> {
        val result = mutableListOf<ExploreKind>()
        val stack = ArrayDeque<ExploreNode>()
        nodes.asReversed().forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node.originalKind?.let(result::add)
            node.children.asReversed().forEach(stack::addLast)
        }
        return result
    }

    fun extractNativeControls(kinds: List<ExploreKind>): NativeControlsResult {
        // refreshNativeControls() is invoked only after the active-source generation check,
        // therefore this snapshot always belongs to the page that will be rendered next.
        sourceOrderSnapshot = kinds.toList()
        val searchControl = findSearchControl(kinds)
        val hiddenIndexes = searchControl?.hiddenSourceIndexes.orEmpty()
        val visibleControls = kinds.mapIndexedNotNull { index, kind ->
            if (index in hiddenIndexes) return@mapIndexedNotNull null
            kind.takeIf {
                it.type == ExploreKind.Type.text ||
                    it.type == ExploreKind.Type.button ||
                    it.type == ExploreKind.Type.toggle
            }
        }
        return NativeControlsResult(
            searchControl = searchControl,
            visibleControls = visibleControls,
        )
    }

    /** Exact original position for a native control. Identity wins over structural equality. */
    fun sourceIndexOf(kind: ExploreKind): Int {
        val snapshot = sourceOrderSnapshot
        val identityIndex = snapshot.indexOfFirst { it === kind }
        if (identityIndex >= 0) return identityIndex
        return snapshot.indexOf(kind)
    }

    /** Original position of a source-native select row. */
    fun sourceIndexOfSelect(title: String): Int {
        val normalized = cleanTitle(title)
        if (normalized.isBlank()) return -1
        return sourceOrderSnapshot.indexOfFirst { kind ->
            kind.type == ExploreKind.Type.select && cleanTitle(kind.title) == normalized
        }
    }

    /**
     * A URL item that the source itself lays out as a full-width row is an independent entry,
     * not merely another option inside a modern category selector. This covers shelves,
     * recommendation/history entrances and similar source-defined destinations without relying
     * on their visible names. Compact URL cells remain category options.
     */
    fun isStandaloneUrlEntry(kind: ExploreKind): Boolean {
        if (kind.type != ExploreKind.Type.url || kind.url.isNullOrBlank()) return false
        val style = kind.style()
        return style.layout_wrapBefore || style.layout_flexBasisPercent >= 1f
    }

    /** Standalone URL entries in their exact source declaration order. */
    fun standaloneUrlEntries(): List<ExploreKind> =
        sourceOrderSnapshot.filter(::isStandaloneUrlEntry)

    /** True when this modern selector target represents a source-defined standalone URL row. */
    fun isStandaloneUrlTarget(title: String, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val snapshot = sourceOrderSnapshot
        val urlMatch = snapshot.firstOrNull { it.url == url && isStandaloneUrlEntry(it) }
        if (urlMatch != null) return true

        // URL is authoritative. Title is only used to disambiguate equivalent normalized URL
        // wrappers that may be recreated by dynamic source JavaScript.
        val normalized = cleanTitle(title)
        if (normalized.isBlank()) return false
        return snapshot.any { kind ->
            isStandaloneUrlEntry(kind) &&
                kind.url == url &&
                cleanTitle(kind.title) == normalized
        }
    }

    /**
     * Original position represented by a dynamic category/tree target.
     * URL is authoritative because different branches often reuse the same visible title.
     * Title matching is only a fallback for structural blank-URL headers and synthetic matrix
     * dimension labels.
     */
    fun sourceIndexOfTarget(title: String, url: String?): Int {
        val snapshot = sourceOrderSnapshot
        if (!url.isNullOrBlank()) {
            val urlIndex = snapshot.indexOfFirst { it.url == url }
            if (urlIndex >= 0) return urlIndex
        }
        val normalized = cleanTitle(title)
        if (normalized.isBlank()) return -1
        return snapshot.indexOfFirst { kind -> cleanTitle(kind.title) == normalized }
    }

    fun findSearchControl(kinds: List<ExploreKind>): SearchControl? {
        val textControls = kinds.mapIndexedNotNull { index, kind ->
            if (kind.type == ExploreKind.Type.text && kind.title.isNotBlank()) index to kind else null
        }
        if (textControls.isEmpty()) return null

        // 1. Strongest match: button explicitly reads this text's InfoMap key.
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

        // 2. Explicit search action: some sources hide the real search implementation in
        // jsLib helpers such as exploreSearch()/doSearch(), while others call java.searchBook
        // or java.open('search', ...) directly. Pair such a button with the nearest preceding
        // text control even when the page contains other text fields returned dynamically.
        kinds.forEachIndexed { buttonIndex, button ->
            if (
                button.type != ExploreKind.Type.button ||
                !actionLooksLikeSearch(button.action.orEmpty())
            ) return@forEachIndexed

            val matched = textControls
                .asSequence()
                .filter { (textIndex, _) -> textIndex < buttonIndex }
                .maxByOrNull { (textIndex, _) -> textIndex }
                ?: return@forEachIndexed

            return SearchControl(matched.second, button, matched.first, buttonIndex)
        }

        if (textControls.size == 1) {
            val (textIndex, text) = textControls.single()

            // 3. Common Legado pattern: button only asks the explore UI to refresh.
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

            // 4. Wrapped-function fallback: a single text followed by the next native button.
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

    internal fun actionLooksLikeSearch(action: String): Boolean {
        if (action.isBlank()) return false
        val js = normalizeAction(action)

        if (Regex("(?:java\\s*\\.\\s*)?searchBook\\s*\\(", RegexOption.IGNORE_CASE).containsMatchIn(js)) {
            return true
        }
        if (Regex("(?:java\\s*\\.\\s*)?open\\s*\\(\\s*['\"]search['\"]", RegexOption.IGNORE_CASE).containsMatchIn(js)) {
            return true
        }
        return Regex(
            "\\b[A-Za-z_$][A-Za-z0-9_$]*search[A-Za-z0-9_$]*\\s*\\(",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(js)
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

    private fun ExploreKind.toSelectControl(
        sourceIndex: Int,
        sourceKey: String,
    ): SelectControl? {
        if (type != ExploreKind.Type.select) return null
        val values = chars.orEmpty()
            .filterNotNull()
            .map(String::trim)
            .filter(String::isNotEmpty)
        if (values.isEmpty()) return null
        return SelectControl(
            kind = this,
            sourceIndex = sourceIndex,
            sourceKey = sourceKey,
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
