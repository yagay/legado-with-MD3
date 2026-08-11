package io.legado.app.enhance.ui

import android.content.Context
import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.enhance.settingssearch.GeneratedSettingCatalog
import io.legado.app.enhance.settingssearch.GeneratedSettingPage
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainRouteAiChat
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.ui.main.my.SettingAction
import io.legado.app.ui.main.my.SettingSearchNode
import io.legado.app.ui.main.my.SettingSearchResult
import io.legado.app.ui.replace.ReplaceRuleActivity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class MyViewModelEnhance(private val context: Context) {

    val settingRegistry: List<SettingSearchNode> by lazy {
        listOf(
            searchNode(
                R.string.book_source_manage,
                R.string.book_source_manage_desc,
                SettingAction.Event(PrefClickEvent.OpenBookSourceManage),
            ),
            searchNode(
                R.string.replace_purify,
                R.string.replace_purify_desc,
                SettingAction.Activity(ReplaceRuleActivity::class.java),
            ),
            searchNode(
                R.string.txt_toc_rule,
                action = SettingAction.Activity(TxtTocRuleActivity::class.java),
            ),
            searchNode(
                R.string.dict_rule,
                action = SettingAction.Activity(DictRuleActivity::class.java),
            ),
            searchNode(
                R.string.highlight_tag_config,
                action = SettingAction.Event(PrefClickEvent.OpenHighlightTagRule),
            ),
            searchNode(R.string.ai_chat, action = SettingAction.Navigate(MainRouteAiChat)),
        ) + GeneratedSettingCatalog.pages.map(::settingPage) + listOf(
            searchNode(
                R.string.bookmark,
                action = SettingAction.Activity(AllBookmarkActivity::class.java),
            ),
            searchNode(
                R.string.read_record,
                action = SettingAction.Event(PrefClickEvent.OpenReadRecord),
            ),
            searchNode(
                R.string.cache_management,
                action = SettingAction.Event(PrefClickEvent.OpenBookCacheManage),
            ),
            searchNode(
                R.string.file_manage,
                action = SettingAction.Activity(FileManageActivity::class.java),
            ),
            searchNode(R.string.about, action = SettingAction.Event(PrefClickEvent.OpenAbout)),
        )
    }

    private fun searchNode(
        @StringRes titleRes: Int,
        @StringRes descriptionRes: Int? = null,
        action: SettingAction? = null,
        children: List<SettingSearchNode> = emptyList(),
    ) = SettingSearchNode(
        title = context.getString(titleRes),
        description = descriptionRes?.let(context::getString),
        action = action,
        children = children,
    )

    private fun settingPage(page: GeneratedSettingPage): SettingSearchNode = SettingSearchNode(
        title = context.getString(page.titleRes),
        action = SettingAction.Navigate(page.destination.route()),
        children = page.items.map { spec ->
            SettingSearchNode(
                title = context.getString(spec.titleRes),
                description = spec.descriptionRes?.let(context::getString),
            )
        },
    )

    private fun SettingSearchNode.toSearchResults(
        parentAction: SettingAction? = null,
        parentPath: List<String> = emptyList(),
    ): List<SettingSearchResult> {
        val targetAction = action ?: parentAction
        val path = parentPath + title
        val finalAction = if (targetAction is SettingAction.Navigate && parentPath.isNotEmpty()) {
            val destination = GeneratedSettingCatalog.pages
                .firstOrNull { page ->
                    page.destination.route()::class == targetAction.route::class
                }
                ?.destination
            SettingAction.Navigate(destination?.route(title) ?: targetAction.route)
        } else {
            targetAction
        }

        val self = finalAction?.let { resultAction ->
            listOf(
                SettingSearchResult(
                    title = title,
                    matchTitle = title,
                    description = if (parentPath.isEmpty()) description else path.joinToString(" > "),
                    matchDescription = description,
                    action = resultAction,
                ),
            )
        }.orEmpty()

        return self + children.flatMap { child -> child.toSearchResults(targetAction, path) }
    }

    fun performSearch(query: String): ImmutableList<SettingSearchResult> {
        if (query.isBlank()) return persistentListOf()
        val normalizedQuery = query.trim()
        return settingRegistry
            .flatMap { node -> node.toSearchResults() }
            .filter { result ->
                result.matchTitle.contains(normalizedQuery, ignoreCase = true) ||
                    result.matchDescription?.contains(normalizedQuery, ignoreCase = true) == true
            }
            .distinctBy { result ->
                Triple(result.matchTitle, result.matchDescription, result.action)
            }
            .toImmutableList()
    }
}
