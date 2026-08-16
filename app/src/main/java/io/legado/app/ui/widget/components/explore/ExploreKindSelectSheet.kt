package io.legado.app.ui.widget.components.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.SearchBar
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreKindSelectSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    sourceUrl: String?,
    onSelected: (List<ExploreKind>) -> Unit,
    multiple: Boolean = false,
    initialSelectedTitles: List<String> = emptyList(),
    repository: ExploreRepository = koinInject(),
    useCase: ExploreKindUiUseCase = koinInject()
) {
    var kinds by remember { mutableStateOf<List<ExploreKind>>(emptyList()) }
    var selectedTitles by remember(initialSelectedTitles, show) {
        mutableStateOf(initialSelectedTitles.toSet())
    }
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    LaunchedEffect(show, sourceUrl) {
        if (show && !sourceUrl.isNullOrBlank()) {
            kinds = repository.getSourceExploreKinds(sourceUrl)
        }
    }

    val filteredKinds = remember(query, kinds) {
        if (query.isBlank()) kinds
        else kinds.filter { kind ->
            kind.title.contains(query, ignoreCase = true) ||
                    (kind.url?.contains(query, ignoreCase = true) == true)
        }
    }
    val kindRows = remember(filteredKinds) {
        calculateExploreKindRows(filteredKinds, 6)
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        endAction = {
            if (multiple && selectedTitles.isNotEmpty()) {
                MediumTonalButton(
                    onClick = {
                        val selectedKinds = kinds.filter { it.title in selectedTitles }
                        onSelected(selectedKinds)
                        onDismissRequest()
                    },
                    icon = Icons.Default.Check,
                    contentDescription = stringResource(R.string.confirm)
                )
            }
        }
    ) {
        Column {
            SearchBar(
                query = query,
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.select_or_search_category),
                autoFocus = false
            )

            LazyColumn(
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(kindRows) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (kind, span) ->
                            ExploreKindMultiTypeItem(
                                kind = kind,
                                sourceUrl = sourceUrl,
                                onOpenUrl = {
                                    if (multiple) {
                                        selectedTitles = if (kind.title in selectedTitles) {
                                            selectedTitles - kind.title
                                        } else {
                                            selectedTitles + kind.title
                                        }
                                    } else {
                                        onSelected(listOf(kind))
                                        onDismissRequest()
                                    }
                                },
                                modifier = Modifier.weight(span.toFloat()),
                                isMiuix = isMiuix,
                                useCase = useCase,
                                activity = activity,
                                onRefreshKinds = {
                                    if (!sourceUrl.isNullOrBlank()) {
                                        kinds = emptyList()
                                    }
                                }
                            )
                        }

                        val totalSpan = rowItems.sumOf { it.second }
                        if (totalSpan < 6) {
                            Spacer(modifier = Modifier.weight((6 - totalSpan).toFloat()))
                        }
                    }
                }
            }
        }
    }
}
