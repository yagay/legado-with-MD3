package io.legado.app.enhance.explore.model

import android.content.Context
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.MD5Utils
import splitties.init.appCtx

object DiscoverySuiteStore {
    private const val PREF_NAME = "discovery_suite_config"
    private const val KEY_CONFIG = "config"
    private const val KEY_SELECTED_ID = "selected_id"

    private val sharedPrefs by lazy {
        appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    private var cachedConfig: DiscoverySuiteConfig? = null

    fun load(): DiscoverySuiteConfig {
        cachedConfig?.let { return it }
        return synchronized(this) {
            cachedConfig?.let { return@synchronized it }
            val json = sharedPrefs.getString(KEY_CONFIG, null)
            val config = if (json.isNullOrBlank()) {
                createDefaultConfig()
            } else {
                try {
                    GSON.fromJson(json, DiscoverySuiteConfig::class.java)
                        .sanitize()
                        .removeLegacyDefaultSlots()
                        .takeIf { it.suites.isNotEmpty() }
                        ?: createDefaultConfig()
                } catch (e: Exception) {
                    LogUtils.e("DiscoverySuiteStore", "Failed to parse config: ${e.message}")
                    createDefaultConfig()
                }
            }
            cachedConfig = config
            val migratedJson = GSON.toJson(config)
            if (json != migratedJson) {
                sharedPrefs.edit().putString(KEY_CONFIG, migratedJson).apply()
            }
            config
        }
    }

    fun save(config: DiscoverySuiteConfig) {
        val sanitized = config.sanitize().removeLegacyDefaultSlots()
        cachedConfig = sanitized
        val json = GSON.toJson(sanitized)
        sharedPrefs.edit().putString(KEY_CONFIG, json).apply()
    }

    fun getSelectedSuiteId(): String? {
        return sharedPrefs.getString(KEY_SELECTED_ID, null)
    }

    fun setSelectedSuiteId(id: String?) {
        sharedPrefs.edit().putString(KEY_SELECTED_ID, id).apply()
    }

    fun resetDefault(): DiscoverySuiteConfig {
        val config = createDefaultConfig()
        save(config)
        return config
    }

    private fun createDefaultConfig(): DiscoverySuiteConfig {
        val defaultSuite = newSuite("示例首页").copy(
            widgets = listOf(
                newWidget("推荐图书", DiscoverySuiteWidgetType.WaterfallBooks).copy(
                    displayStyle = 1 // Default to List style
                )
            )
        )
        return DiscoverySuiteConfig(suites = listOf(defaultSuite))
    }

    /**
     * Older builds always created one fixed "分类" TagBar and one fixed "榜单" RankButtons
     * widget. Modern discovery already renders source-defined dynamicSelectors, so those two
     * template slots duplicate and distort the source's real category structure.
     *
     * Match the exact historical defaults only; user-created widgets remain untouched.
     */
    private fun DiscoverySuiteConfig.removeLegacyDefaultSlots(): DiscoverySuiteConfig {
        val legacyCategoryTargets = listOf("玄幻", "修真", "都市", "穿越")
        val legacyRankTargets = listOf("推荐", "评分", "热门")

        return copy(
            suites = suites.map { suite ->
                suite.copy(
                    widgets = suite.widgets.filterNot { widget ->
                        val targetTitles = widget.targets.map { it.title }
                        val legacyCategory =
                            widget.type == DiscoverySuiteWidgetType.TagBar.type &&
                                widget.title == "分类" &&
                                widget.isDynamic &&
                                targetTitles == legacyCategoryTargets
                        val legacyRank =
                            widget.type == DiscoverySuiteWidgetType.RankButtons.type &&
                                widget.title == "榜单" &&
                                targetTitles == legacyRankTargets
                        legacyCategory || legacyRank
                    }
                )
            }
        )
    }

    fun newSuite(name: String): DiscoverySuite {
        return DiscoverySuite(
            id = MD5Utils.md5Encode(System.currentTimeMillis().toString() + name),
            name = name
        )
    }

    fun newWidget(title: String, type: DiscoverySuiteWidgetType): DiscoverySuiteWidget {
        val displayLimit = when (type) {
            DiscoverySuiteWidgetType.TagBar -> 30
            DiscoverySuiteWidgetType.RankButtons -> 9
            DiscoverySuiteWidgetType.RandomBooks -> 12
            DiscoverySuiteWidgetType.RankedList -> 4
            DiscoverySuiteWidgetType.WaterfallBooks -> 24
            else -> 12
        }
        return DiscoverySuiteWidget(
            id = MD5Utils.md5Encode(System.currentTimeMillis().toString() + title),
            type = type.type,
            title = title,
            isDynamic = type == DiscoverySuiteWidgetType.TagBar,
            displayLimit = displayLimit
        )
    }
}
