# Explore Module（发现页增强）

本目录只维护现代/瀑布流发现页相对上游的**布局、组织与展示差异**。书源协议、JavaScript 执行、InfoMap、搜索/发现请求、登录跳转、原生发现控件样式等行为优先复用 `app` 中的共享实现，避免复制上游逻辑或维护第二套 UI。

## 边界原则

1. `source.exploreKinds()` 是书源行为的事实来源；增强层不得按中文名称重新解释书源协议。
2. `text / button / toggle / select / url` 保留上游 `ExploreKind` 的原始 type、action、chars、default、style 与顺序。
3. 原生发现控件使用上游 `ExploreKindMultiTypeItem` 和 `calculateExploreKindRows()`；增强层只决定这些控件出现的位置，不自行重做卡片样式或 flex/span 规则。
4. 只有书源原始 JSON 明确提供 `children` 时才把它作为显式树；无显式树时，只允许根据纯展示 Header 和原始顺序形成 SECTION。
5. 不使用“男频 / 女频 / 完结 / 连载 / 推荐 / 热门 / 榜单”等名称猜测频道、状态或排行榜。
6. 动态分类默认使用通用 TagBar；RankButtons 等特殊外观只能由显式 `DiscoverySuite` widget 配置决定。
7. 动态控件值写入 `InfoMap` 后，action 统一通过共享 `ExploreKindUiUseCase` 执行；增强 ViewModel 不直接创建登录 JS bridge。
8. `ExploreRepository` 只负责上游数据访问，不返回或依赖 enhance 自己的展示模型。
9. 发现页原生搜索控件必须在最终 `exploreKinds()` 运行时结果上识别并从现代布局隐藏；右上角搜索优先过滤当前已加载书籍，并在书源存在标准 `searchUrl` 时继续远程搜索并合并去重结果。
10. 新布局能直接复用上游/项目现有组件、字体、颜色、卡片、菜单、BottomSheet、设置项和书籍列表样式时必须优先复用；只有现代布局特有的排列行为才留在 enhance。

## 当前主要组件

- `builder/ModernExploreClassificationEngine.kt`：识别显式 TREE、结构化 SECTION 与 FLAT，不猜业务语义。
- `builder/ModernExploreControlExtractor.kt`：从最终运行时 `ExploreKind` 中识别 select、原生控件及搜索组合。
- `builder/ModernExploreMatrixFactorizer.kt`：对结构明确的二维组合做保守拆分。
- `builder/ExploreKindStructure.kt`：现代布局自己的结构辅助函数，不修改核心 `ExploreKind`。
- `model/ExploreNode.kt` / `ExploreMode.kt`：现代分类引擎仍在使用的最小展示结构模型。
- `model/DiscoverySuiteConfig.kt` / `DiscoverySuiteStore.kt`：瀑布流 widget 配置与持久化。
- `vm/ExploreViewModelEnhance.kt`：维护现代布局选择、搜索、分页和展示状态；书源动作调用共享运行时。
- `screen/DiscoverySuiteScreen.kt`：现代/瀑布流内容编排；书籍项、加载状态和原生控件优先复用 app 组件。
- `screen/AdaptiveExploreControlRows.kt`：仅负责放置原生控件，行划分直接复用上游 `calculateExploreKindRows()`。
- `ui/ModernDiscoveryFilterBar.kt`：只保留“单排动态容纳、溢出展开、选中项前置”这一现代布局专属行为；显示样式复用项目 `TextCard` / `AppText` / `LegadoTheme`。

## 与上游同步

同步 HapeLee/TeamLegado 时优先保留上游 `app` 层文件。若发现 enhance 开始直接实现 login、cookie、WebView、书源 JS runtime、搜索/发现网络请求，或重新实现已有卡片/菜单/设置项，应优先把调用改回共享/上游入口，而不是在本模块复制实现。

允许长期保留在 enhance 的内容主要是：现代分类结构识别、分类折叠/展开方式、路径组织、滚动状态、DiscoverySuite widget 配置以及现代布局专属结果组织。
