package io.legado.app.enhance.settingssearch

import android.content.Context

fun getSettingScrollInfo(
    context: Context,
    destination: SettingDestination,
    searchTitle: String?,
): SettingScrollInfo? {
    if (searchTitle.isNullOrBlank()) return null
    val page = GeneratedSettingCatalog.pages.firstOrNull { it.destination == destination } ?: return null
    
    val itemsInPage = page.items
    val targetItem = itemsInPage.firstOrNull { spec ->
        context.getString(spec.titleRes).equals(searchTitle, ignoreCase = true)
    } ?: return null
    
    val groupIndex = targetItem.groupIndex
    val itemIndexInGroup = itemsInPage.filter { it.groupIndex == groupIndex }
        .indexOf(targetItem)
        
    return SettingScrollInfo(groupIndex, itemIndexInGroup)
}

@Deprecated("Use getSettingScrollInfo", ReplaceWith("getSettingScrollInfo(context, destination, searchTitle)?.groupIndex"))
fun generatedSettingGroupIndex(
    context: Context,
    destination: SettingDestination,
    searchTitle: String?,
): Int? = getSettingScrollInfo(context, destination, searchTitle)?.groupIndex
