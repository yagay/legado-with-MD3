# Explore Module (发现页增强逻辑)

本模块实现了“发现页套件 (DiscoverySuite)”，将传统扁平的书源发现页提升为具有瀑布流布局和多维筛选能力的现代电商级界面。

## 核心业务流

### 1. 筛选树构建 (`ExploreTreeBuilder`)
*   **输入**: 书源定义的扁平 `exploreKinds` 字符串。
*   **解析**: 通过递归识别 `::` 或其他层级分隔符，将分类转化为 `ExploreTree` 结构。
*   **缓存**: 结果缓存于 `ExploreCache` 中，确保在频繁切换书源时秒开。

### 2. 类目自动解析 (`rebuildSelectors`)
*   逻辑位于 `ExploreViewModelEnhance.kt`。
*   它能根据筛选树的深度，动态生成 1~3 级的 UI 过滤器。
*   **示例**: 若某源分类为 `男生::玄幻::周榜`，套件会自动生成 `[男生] [玄幻] [周榜]` 三行 FilterChips。

### 3. 瀑布流引擎 (`ExploreLayoutEngine`)
*   支持单列/多列自适应切换。
*   **状态保存**: 使用 `DiscoverySuiteStore`（基于 DataStore 或数据库）保存用户对特定书源的布局偏好（如列数、是否折叠头图）。

## 维护手册

*   **新增布局模式**: 在 `ExploreMode.kt` 中添加枚举，并在 `DiscoverySuiteScreen` 中实现对应的 Compose 分支。
*   **解析规则优化**: 若新版书源使用了特殊的分类语法，修改 `ExploreModeDetector.kt` 的正则匹配逻辑。
