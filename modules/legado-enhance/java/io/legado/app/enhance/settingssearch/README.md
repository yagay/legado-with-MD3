# Settings Search Module (设置项检索架构)

这是一个半自动化的检索系统，允许用户在“我的”页面快速搜索并跳转到深层嵌套的设置项。

## 技术实现原理

### 阶段 A: 构建期扫描 (Static Analysis)
`setting-search.gradle` 脚本使用正则表达式匹配所有 `*ConfigScreen.kt`。
*   **匹配目标**: 任何以 `SettingItem` 结尾的组件调用（如 `SwitchSettingItem`, `ListSettingItem`）。
*   **提取属性**: 提取其 `title`（资源 ID）和它在 `LazyColumn` 中的 `item` 块索引（即 `groupIndex`）。
*   **产物**: 自动生成 `GeneratedSettingCatalog.kt`，包含所有设置项的物理位置映射。

### 阶段 B: 运行时匹配 (`GeneratedSettingLocator`)
1.  **关键词匹配**: 用户输入搜索词，系统在 `GeneratedSettingCatalog` 中进行模糊匹配。
2.  **定位逻辑**: 返回 `SettingDestination`（目标页面枚举）和 `groupIndex`（分组位置）。

### 阶段 C: 滚动与高亮 (`SettingScrollEnhance`)
*   **自动滚动**: 跳转后，目标页面通过 `LaunchSettingScrollEffect` 监听 `searchKey`。
*   **偏移计算**: 调用 `listState.animateScrollToItem(groupIndex)`。
*   **视觉反馈**: 目标 `SettingItem` 根据 `highlightKey` 属性判断是否显示高亮背景动画。

## 开发注意事项

*   **硬编码警告**: 脚本要求 `title` 必须使用 `stringResource(R.string.xxx)`。若使用硬编码字符串，编译时会报错提示修改。
*   **分组定位**: `groupIndex` 依赖于 `LazyColumn` 中的 `item { ... }` 块顺序。确保设置项放在标准的 `item` 或 `items` 块中。
