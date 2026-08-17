package io.legado.app.enhance.explore.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.enhance.explore.model.DiscoverySuiteConfig
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetType
import io.legado.app.ui.main.explore.ExploreIntent
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem

@Composable
fun DiscoveryConfigSheet(
    show: Boolean,
    state: ExploreViewModel.ExploreUiState,
    onIntent: (ExploreIntent) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    AppModalBottomSheet(
        title = "瀑布流布局设置",
        show = true,
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            val suite = state.enhance.selectedSuite
            if (suite != null) {
                // 1. Book List Display Style
                val bookWidget = suite.widgets.find {
                    it.type == DiscoverySuiteWidgetType.WaterfallBooks.type ||
                    it.type == DiscoverySuiteWidgetType.BookList.type ||
                    it.type == DiscoverySuiteWidgetType.HorizontalBooks.type
                }
                if (bookWidget != null) {
                    DropdownListSettingItem(
                        title = "书籍展示方式",
                        description = "主推书籍列表的呈现形态",
                        selectedValue = bookWidget.displayStyle.toString(),
                        displayEntries = arrayOf("横向滚动", "垂直列表", "网格瀑布"),
                        entryValues = arrayOf("0", "1", "2"),
                        onValueChange = { value ->
                            onIntent(ExploreIntent.UpdateDiscoverySettings { config ->
                                config.copy(
                                    suites = config.suites.map { s ->
                                        if (s.id == suite.id) {
                                            s.copy(widgets = s.widgets.map { w ->
                                                if (w.id == bookWidget.id) w.copy(displayStyle = value.toInt()) else w
                                            })
                                        } else s
                                    }
                                )
                            })
                        }
                    )

                    if (bookWidget.displayStyle == 2) {
                        SliderSettingItem(
                            title = "网格列数",
                            value = bookWidget.gridCount.toFloat(),
                            defaultValue = 3f,
                            valueRange = 2f..5f,
                            steps = 3,
                            onValuePreviewChange = { value ->
                                onIntent(ExploreIntent.PreviewDiscoverySettings { config ->
                                    config.copy(
                                        suites = config.suites.map { s ->
                                            if (s.id == suite.id) {
                                                s.copy(widgets = s.widgets.map { w ->
                                                    if (w.id == bookWidget.id) w.copy(gridCount = value.toInt()) else w
                                                })
                                            } else s
                                        }
                                    )
                                })
                            },
                            onValueChange = { value ->
                                onIntent(ExploreIntent.UpdateDiscoverySettings { config ->
                                    config.copy(
                                        suites = config.suites.map { s ->
                                            if (s.id == suite.id) {
                                                s.copy(widgets = s.widgets.map { w ->
                                                    if (w.id == bookWidget.id) w.copy(gridCount = value.toInt()) else w
                                                })
                                            } else s
                                        }
                                    )
                                })
                            }
                        )
                    }

                    if (bookWidget.displayStyle == 1) {
                        SliderSettingItem(
                            title = "封面高度",
                            value = bookWidget.coverHeight.toFloat(),
                            defaultValue = 110f,
                            valueRange = 80f..200f,
                            onValuePreviewChange = { value ->
                                onIntent(ExploreIntent.PreviewDiscoverySettings { config ->
                                    config.copy(
                                        suites = config.suites.map { s ->
                                            if (s.id == suite.id) {
                                                s.copy(widgets = s.widgets.map { w ->
                                                    if (w.id == bookWidget.id) w.copy(coverHeight = value.toInt()) else w
                                                })
                                            } else s
                                        }
                                    )
                                })
                            },
                            onValueChange = { value ->
                                onIntent(ExploreIntent.UpdateDiscoverySettings { config ->
                                    config.copy(
                                        suites = config.suites.map { s ->
                                            if (s.id == suite.id) {
                                                s.copy(widgets = s.widgets.map { w ->
                                                    if (w.id == bookWidget.id) w.copy(coverHeight = value.toInt()) else w
                                                })
                                            } else s
                                        }
                                    )
                                })
                            }
                        )
                    }
                }

                // 2. Global Toggles
                val tagBarWidget = suite.widgets.find { it.type == DiscoverySuiteWidgetType.TagBar.type }
                if (tagBarWidget != null) {
                    SwitchSettingItem(
                        title = "动态类目栏",
                        description = "根据书源规则自动生成筛选栏",
                        checked = tagBarWidget.isDynamic,
                        onCheckedChange = { value ->
                            onIntent(ExploreIntent.UpdateDiscoverySettings { config ->
                                config.copy(
                                    suites = config.suites.map { s ->
                                        if (s.id == suite.id) {
                                            s.copy(widgets = s.widgets.map { w ->
                                                if (w.id == tagBarWidget.id) w.copy(isDynamic = value) else w
                                            })
                                        } else s
                                    }
                                )
                            })
                        }
                    )
                }

                Text(
                    text = "高级逻辑请在前台 [设置 -> 发现页套件] 修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}
