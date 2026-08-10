package io.legado.app.data.entities.rule

/**
 * 发现分类
 */
data class ExploreKind(
    val title: String = "",
    val url: String? = null,
    val type: String = Type.url,
    val action: String? = null,
    val chars: Array<String?>? = null,
    val default: String? = null,
    var viewName: String? = null,
    val style: FlexChildStyle? = null,
    val children: List<ExploreKind>? = null
) {

    @Suppress("ConstPropertyName")
    object Type {
        const val url = "url"
        const val text = "text"
        const val button = "button"
        const val toggle = "toggle"
        const val select = "select"
    }

    /**
     * 只有“没有可执行目标”的项目才能作为逻辑分组标题。
     * layout_flexBasisPercent=1 只是布局宽度，不能当成层级标记。
     */
    fun isGroupHeader(): Boolean {
        return url.isNullOrBlank() && action.isNullOrBlank()
    }

    fun targetUrl(): String? = action?.takeIf { it.isNotBlank() }
        ?: url?.takeIf { it.isNotBlank() }

    fun hasChildren(): Boolean = !children.isNullOrEmpty()

    fun style(): FlexChildStyle {
        return style ?: FlexChildStyle.defaultStyle
    }

    override fun equals(other: Any?): Boolean {
        if (other is ExploreKind) {
            return other.title == title
                && other.type == type
                && other.url == url
                && other.action == action
                && other.default == default
        }
        return false
    }

    override fun hashCode(): Int {
        var result = title.hashCode() + type.hashCode()
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (action?.hashCode() ?: 0)
        result = 31 * result + (default?.hashCode() ?: 0)
        return result
    }

}
