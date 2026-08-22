package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreNode
import java.util.IdentityHashMap

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

    /** Exact ExploreKind order used by the current modern discovery page. */
    @Volatile
    private var sourceOrderSnapshot: List<ExploreKind> = emptyList()

    /**
     * Hot-path lookup caches rebuilt once whenever sourceOrderSnapshot changes.
     * Compose rendering can ask about hundreds of targets, so these queries must not rescan the
     * complete source list for every target.
     */
    private var sourceIndexByIdentity = IdentityHashMap<ExploreKind, Int>()
    private var firstIndexByUrl: Map<String, Int> = emptyMap()
    private var firstIndexByCleanTitle: Map<String, Int> = emptyMap()
    private var firstSelectIndexByCleanTitle: Map<String, Int> = emptyMap()
    private var standaloneSourceIndexes: Set<Int> = emptySet()
    private var standaloneUrls: Set<String> = emptySet()
    private var standaloneTargetKeys: Set<String> = emptySet()

    /**
     * Select controls that have proved to change the shape of the explore page.
     * The key includes title/action/options so equally named controls from unrelated sources do
     * not normally share learned state.
     */
    private val structuralSelectKeys = mutableSetOf<String>()

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

    /** Produces the exact native-control list that the modern UI should render. */
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
        // A dynamic source commonly rebuilds ExploreKind after a select action. Comparing the old
        // and new snapshots lets us learn whether that select controls page structure or merely
        // changes URL parameter values. No business labels are required.
        learnStructuralSelectRelationships(sourceOrderSnapshot, kinds)
        updateSourceSnapshot(kinds)

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

    private fun updateSourceSnapshot(kinds: List<ExploreKind>) {
        val snapshot = kinds.toList()
        sourceOrderSnapshot = snapshot

        val identity = IdentityHashMap<ExploreKind, Int>(snapshot.size.coerceAtLeast(1))
        val byUrl = linkedMapOf<String, Int>()
        val byTitle = linkedMapOf<String, Int>()
        val selectByTitle = linkedMapOf<String, Int>()

        snapshot.forEachIndexed { index, kind ->
            identity[kind] = index
            kind.url?.takeIf { it.isNotBlank() }?.let { byUrl.putIfAbsent(it, index) }
            val cleaned = cleanTitle(kind.title)
            if (cleaned.isNotBlank()) {
                byTitle.putIfAbsent(cleaned, index)
                if (kind.type == ExploreKind.Type.select) {
                    selectByTitle.putIfAbsent(cleaned, index)
                }
            }
        }

        // URL items are explore categories regardless of the source's visual width hint.
        // Treating full-width URL rows as standalone actions made the modern layout render each
        // category on its own row and open the legacy secondary page instead of refreshing the
        // current waterfall/list. Keep standalone caches empty so every URL category remains in
        // the selector hierarchy; native text/button/toggle actions are handled separately.
        val standaloneIndexes = emptySet<Int>()

        sourceIndexByIdentity = identity
        firstIndexByUrl = byUrl
        firstIndexByCleanTitle = byTitle
        firstSelectIndexByCleanTitle = selectByTitle
        standaloneSourceIndexes = standaloneIndexes
        standaloneUrls = emptySet()
        standaloneTargetKeys = emptySet()
    }

    /**
     * Return the selected value of the nearest proven structural select before a dynamic row.
     * Independent selects (status/sort/word-count/etc.) are deliberately ignored.
     */
    fun structuralParentSelectionBefore(sourceIndex: Int): String? {
        if (sourceIndex < 0) return null
        val candidate = sourceOrderSnapshot
            .withIndex()
            .asSequence()
            .filter { (index, kind) ->
                index < sourceIndex &&
                    kind.type == ExploreKind.Type.select &&
                    selectIdentity(kind) in structuralSelectKeys
            }
            .maxByOrNull { it.index }
            ?.value
            ?: return null
        return cleanTitle(candidate.default.orEmpty()).takeIf { it.isNotBlank() }
    }

    internal fun resetStructureLearning() {
        sourceOrderSnapshot = emptyList()
        sourceIndexByIdentity = IdentityHashMap()
        firstIndexByUrl = emptyMap()
        firstIndexByCleanTitle = emptyMap()
        firstSelectIndexByCleanTitle = emptyMap()
        standaloneSourceIndexes = emptySet()
        standaloneUrls = emptySet()
        standaloneTargetKeys = emptySet()
        structuralSelectKeys.clear()
    }

    private fun learnStructuralSelectRelationships(
        previous: List<ExploreKind>,
        current: List<ExploreKind>,
    ) {
        if (previous.isEmpty() || current.isEmpty()) return

        val previousSelects = previous
            .filter { it.type == ExploreKind.Type.select }
            .associateBy(::selectIdentity)
        if (previousSelects.isEmpty()) return

        val changedSelectKeys = current
            .asSequence()
            .filter { it.type == ExploreKind.Type.select }
            .mapNotNull { currentSelect ->
                val key = selectIdentity(currentSelect)
                val old = previousSelects[key] ?: return@mapNotNull null
                val oldValue = old.default.orEmpty()
                val newValue = currentSelect.default.orEmpty()
                key.takeIf { oldValue != newValue }
            }
            .toList()
        if (changedSelectKeys.isEmpty()) return

        if (exploreStructureSignature(previous) != exploreStructureSignature(current)) {
            structuralSelectKeys += changedSelectKeys
        }
    }

    /**
     * Structure deliberately ignores select defaults and URL parameter values. A status/sort
     * change therefore keeps the same signature, while channel/category/group changes that add,
     * remove or rename rows produce a different signature.
     */
    private fun exploreStructureSignature(kinds: List<ExploreKind>): List<String> =
        kinds.map { kind ->
            when (kind.type) {
                ExploreKind.Type.url ->
                    "url|${cleanTitle(kind.title)}|${urlFamilySignature(kind.url.orEmpty())}"
                ExploreKind.Type.select ->
                    "select|${cleanTitle(kind.title)}|${kind.chars.orEmpty().filterNotNull().joinToString("\u001F") { cleanTitle(it) }}"
                else -> "${kind.type}|${cleanTitle(kind.title)}"
            }
        }

    private fun selectIdentity(kind: ExploreKind): String = buildString {
        append(cleanTitle(kind.title))
        append('\u001F')
        append(kind.action.orEmpty())
        append('\u001F')
        kind.chars.orEmpty().filterNotNull().forEach { value ->
            append(cleanTitle(value))
            append('\u001E')
        }
    }

    /** Exact original position for a native control. Identity wins over structural equality. */
    fun sourceIndexOf(kind: ExploreKind): Int =
        sourceIndexByIdentity[kind] ?: sourceOrderSnapshot.indexOf(kind)

    /** Original position of a source-native select row. */
    fun sourceIndexOfSelect(title: String): Int {
        val normalized = cleanTitle(title)
        if (normalized.isBlank()) return -1
        return firstSelectIndexByCleanTitle[normalized] ?: -1
    }

    /** URL entries stay inside the modern selector hierarchy. */
    fun isStandaloneUrlEntry(kind: ExploreKind): Boolean = false

    /** Compare URL structure without depending on parameter values. */
    private fun urlFamilySignature(url: String): String {
        val normalized = url.substringBefore('#')
        val base = normalized.substringBefore('?').trim()
        if (base.isBlank()) return ""
        val query = normalized.substringAfter('?', "")
        if (query.isBlank()) return base
        val keys = query.split('&')
            .asSequence()
            .map { it.substringBefore('=').trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .joinToString("&")
        return if (keys.isBlank()) base else "$base?$keys"
    }

    /** Standalone URL entries in their exact source declaration order. */
    fun standaloneUrlEntries(): List<ExploreKind> = emptyList()

    /** URL targets are categories in modern layout, never standalone secondary-page entries. */
    fun isStandaloneUrlTarget(title: String, url: String?): Boolean = false

    /** Original position represented by a dynamic category/tree target. */
    fun sourceIndexOfTarget(title: String, url: String?): Int {
        if (!url.isNullOrBlank()) {
            firstIndexByUrl[url]?.let { return it }
        }
        val normalized = cleanTitle(title)
        if (normalized.isBlank()) return -1
        return firstIndexByCleanTitle[normalized] ?: -1
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

        // 2. Explicit search action: some sources hide the real search implementation in helpers.
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
