# Explore Module（发现页增强）

本目录只维护现代/瀑布流发现页相对上游的**布局与展示差异**。书源协议、JavaScript 执行、InfoMap、搜索/发现请求、登录跳转等业务行为继续复用 app 中的共享实现，避免复制上游逻辑。

## 边界原则

1. `source.exploreKinds()` 是书源行为的事实来源；增强层不得按中文名称重新解释书源协议。
2. `text / button / toggle / select / url` 保留上游 `ExploreKind` 的原始 type、action、chars、default、style 与顺序。
3. 只有书源原始 JSON 明确提供 `children` 时才把它作为显式树；无显式树时，只允许根据纯展示 Header 和原始顺序形成 SECTION。
4. 不使用“男频 / 女频 / 完结 / 连载 / 推荐 / 热门 / 榜单”等名称猜测频道、状态或排行榜。
5. 动态分类默认使用通用 TagBar；RankButtons 等特殊外观只能由显式 `DiscoverySuite` widget 配置决定。
6. 动态控件值写入 `InfoMap` 后，action 统一通过共享 `ExploreKindUiUseCase` 执行；增强 ViewModel 不直接创建登录 JS bridge。
7. `ExploreRepository` 只负责上游数据访问，不返回或依赖 enhance 自己的树模型。

## 主要组件

- `builder/ModernExploreClassificationEngine.kt`：仅识别显式 TREE、结构化 SECTION 与 FLAT，不猜语义。
- `builder/ExploreKindStructure.kt`：现代布局自己的结构辅助函数，避免把布局辅助方法塞进核心 `ExploreKind`。
- `builder/ExploreTreeBuilder.kt` / `ExploreFilterBuilder.kt`：把已确定的展示结构转换为 enhance UI 模型。
- `vm/ExploreViewModelEnhance.kt`：维护现代布局选择、搜索、分页和展示状态；书源动作调用共享运行时。
- `screen/DiscoverySuiteScreen.kt`：现代/瀑布流 Compose UI。

## 与上游同步

同步 HapeLee/TeamLegado 时优先保留上游 `app` 层文件。若发现 enhance 开始直接实现 login、cookie、WebView、书源 JS runtime、搜索或发现网络请求，应优先把调用改回共享/上游入口，而不是在本模块复制实现。

允许长期保留在 enhance 的内容主要是：瀑布流卡片、分类展开方式、路径显示、滚动状态、书源弹窗布局、DiscoverySuite widget 配置以及现代布局专属搜索结果组织。
