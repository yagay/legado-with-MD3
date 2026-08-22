# Explore Module（发现页增强）

本目录只维护现代/瀑布流发现页相对上游的**布局、结构识别与展示状态差异**。书源协议、`ExploreKind` 渲染、JavaScript 执行、InfoMap、登录跳转、书籍列表组件、设置组件和通用窗口组件优先直接复用 app 中的上游实现。

## 边界原则

1. `source.exploreKinds()` 是书源行为的事实来源；增强层不得按中文名称重新解释书源协议。
2. `text / button / toggle / select / url` 保留上游 `ExploreKind` 的原始 type、action、chars、default、style 与顺序。
3. 只有书源原始 JSON 明确提供 `children` 时才把它作为显式树；无显式树时，只允许根据纯展示 Header、原始顺序和可验证的矩阵结构形成展示层级。
4. 不使用“男频 / 女频 / 完结 / 连载 / 推荐 / 热门 / 榜单”等名称猜测频道、状态或排行榜。
5. 动态控件值写入 `InfoMap` 后，action 统一通过共享 `ExploreKindUiUseCase` 执行；增强 ViewModel 不直接创建登录 JS bridge。
6. 原生控制项继续使用上游 `ExploreKindMultiTypeItem` 和 `calculateExploreKindRows()` 渲染与排版。
7. 书籍列表继续使用上游搜索/发现列表、网格、横向列表和 `LoadMoreFooter` 等组件；增强层只决定组合顺序和显示方式。
8. 通用 BottomSheet、普通下拉菜单、普通菜单项保持上游默认行为；现代书源选择器只通过可选参数启用固定搜索、快速滚动和长按等额外能力。
9. 发现页原生搜索控件必须在最终 `exploreKinds()` 运行时结果上识别并从现代布局隐藏；右上角搜索先过滤当前已加载书籍，在书源存在标准 `searchUrl` 时再继续远程搜索并合并去重结果。

## 当前核心组件

- `builder/ModernExploreClassificationEngine.kt`：识别显式 TREE、结构化 SECTION 与 FLAT，不猜业务语义。
- `builder/ModernExploreMatrixFactorizer.kt`：只在标题模式和 URL 参数共同证明为完整矩阵时恢复选择维度。
- `builder/ModernExploreControlExtractor.kt`：从最终运行时 `ExploreKind` 中识别 select、原生控件和搜索组合。
- `builder/ExploreKindStructure.kt`：现代布局局部的 URL/结构辅助，不修改上游 `ExploreKind`。
- `model/DiscoverySuiteConfig.kt` / `DiscoverySuiteStore.kt`：现代布局配置与持久化。
- `vm/ExploreViewModelEnhance.kt`：维护现代布局选择、搜索、分页和展示状态；业务动作仍调用共享运行时。
- `screen/DiscoverySuiteScreen.kt`：现代/瀑布流 Compose 组合入口。
- `ui/ModernDiscoveryFilterBar.kt`：现代分类的单排、展开和选中项前置交互。

## 与上游同步

同步 HapeLee 上游时，优先直接采用上游 `app` 层实现。只有上游组件缺少现代布局必须的接口时，才增加默认关闭的最小可选参数；不得复制 login、cookie、WebView、书源 JS runtime、通用列表或通用设置组件。

允许长期保留在 enhance 的内容主要是：分类结构识别、分类展开方式、路径显示、DiscoverySuite widget 配置、现代布局滚动/选择状态以及现代布局专属搜索结果组织。
