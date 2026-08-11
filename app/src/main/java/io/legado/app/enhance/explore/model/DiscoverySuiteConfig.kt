package io.legado.app.enhance.explore.model

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.MD5Utils

@Keep
data class DiscoverySuiteConfig(
    @SerializedName("suites")
    val suites: List<DiscoverySuite> = emptyList(),
    @SerializedName("lastSelectedTargets")
    val lastSelectedTargets: Map<String, String> = emptyMap(), // Key: sourceUrl_widgetId
    @SerializedName("categoryUsage")
    val categoryUsage: Map<String, Long> = emptyMap() // Key: sourceUrl_categoryTitle
)

@Keep
data class DiscoverySuite(
    @SerializedName("id")
    val id: String = MD5Utils.md5Encode(System.currentTimeMillis().toString()),
    @SerializedName("name")
    val name: String = "",
    @SerializedName("alias")
    val alias: String? = null,
    @SerializedName("defaultSourceUrl")
    val defaultSourceUrl: String? = null,
    @SerializedName("opacityMultiplier")
    val opacityMultiplier: Float = 1.0f,
    @SerializedName("order")
    val order: Int = 0,
    @SerializedName("widgets")
    val widgets: List<DiscoverySuiteWidget> = emptyList()
) {
    val displayName: String get() = alias ?: name
}

@Keep
data class DiscoverySuiteWidget(
    @SerializedName("id")
    val id: String = MD5Utils.md5Encode(System.currentTimeMillis().toString()),
    @SerializedName("type")
    val type: String = DiscoverySuiteWidgetType.BookList.type,
    @SerializedName("title")
    val title: String = "",
    @SerializedName("targets")
    val targets: List<DiscoverySuiteWidgetTarget> = emptyList(),
    @SerializedName("sourceUrls")
    val sourceUrls: List<String> = emptyList(),
    @SerializedName("tagUrls")
    val tagUrls: List<String> = emptyList(),
    @SerializedName("displayLimit")
    val displayLimit: Int = 12,
    @SerializedName("displayStyle")
    val displayStyle: Int = 0, // 0: Horizontal, 1: Vertical List, 2: Grid
    @SerializedName("gridCount")
    val gridCount: Int = 3,
    @SerializedName("coverHeight")
    val coverHeight: Int = 110, // Default height for vertical list
    @SerializedName("isDynamic")
    val isDynamic: Boolean = false,
    @SerializedName("order")
    val order: Int = 0
)

@Keep
data class DiscoverySuiteWidgetTarget(
    @SerializedName("sourceUrl")
    val sourceUrl: String = "",
    @SerializedName("tagUrl")
    val tagUrl: String = "",
    @SerializedName("title")
    val title: String = ""
)

enum class DiscoverySuiteWidgetType(val type: String) {
    RandomBooks("random_books"),
    TagBar("tag_bar"),
    RankButtons("rank_buttons"),
    BookList("book_list"),
    HorizontalBooks("horizontal_books"),
    RankedList("ranked_list"),
    WaterfallBooks("waterfall_books");

    companion object {
        fun from(type: String?) = entries.find { it.type == type } ?: BookList
    }
}

fun DiscoverySuiteConfig.sanitize(): DiscoverySuiteConfig {
    return copy(
        suites = suites.map { suite ->
            suite.copy(
                widgets = suite.widgets
                    .distinctBy { it.id }
                    .sortedBy { it.order }
                    .let { list ->
                        // Move waterfall to bottom as in Rimchars
                        val waterfall = list.filter { it.type == DiscoverySuiteWidgetType.WaterfallBooks.type }
                        val others = list.filter { it.type != DiscoverySuiteWidgetType.WaterfallBooks.type }
                        others + waterfall
                    }
            )
        }.sortedBy { it.order }
    )
}
