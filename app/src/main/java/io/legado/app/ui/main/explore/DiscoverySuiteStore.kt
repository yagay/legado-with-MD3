package io.legado.app.ui.main.explore

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

    fun load(): DiscoverySuiteConfig {
        val json = sharedPrefs.getString(KEY_CONFIG, null)
        LogUtils.d("DiscoverySuiteStore", "Loading config: $json")
        return if (json.isNullOrBlank()) {
            createDefaultConfig()
        } else {
            try {
                val config = GSON.fromJson(json, DiscoverySuiteConfig::class.java).sanitize()
                if (config.suites.isEmpty()) {
                    createDefaultConfig()
                } else {
                    config
                }
            } catch (e: Exception) {
                LogUtils.e("DiscoverySuiteStore", "Failed to parse config: ${e.message}")
                createDefaultConfig()
            }
        }
    }

    fun save(config: DiscoverySuiteConfig) {
        val json = GSON.toJson(config.sanitize())
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
                newWidget("分类", DiscoverySuiteWidgetType.TagBar).copy(
                    isDynamic = true,
                    targets = listOf(
                        DiscoverySuiteWidgetTarget(title = "玄幻"),
                        DiscoverySuiteWidgetTarget(title = "修真"),
                        DiscoverySuiteWidgetTarget(title = "都市"),
                        DiscoverySuiteWidgetTarget(title = "穿越")
                    )
                ),
                newWidget("榜单", DiscoverySuiteWidgetType.RankButtons).copy(
                    targets = listOf(
                        DiscoverySuiteWidgetTarget(title = "推荐"),
                        DiscoverySuiteWidgetTarget(title = "评分"),
                        DiscoverySuiteWidgetTarget(title = "热门")
                    )
                ),
                newWidget("推荐图书", DiscoverySuiteWidgetType.WaterfallBooks).copy(
                    displayStyle = 1 // Default to List style
                )
            )
        )
        return DiscoverySuiteConfig(suites = listOf(defaultSuite))
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
