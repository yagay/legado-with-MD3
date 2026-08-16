# Legado Enhance (核心增强模块)

本仓库作为 `legado-with-MD3` 的核心子模块，旨在以**非侵入性**的方式为 Legado 提供功能增强。其设计哲学是“逻辑解耦、资源隔离”，确保主项目能够随时平滑合并上游更新。

## 核心架构设计

### 1. 资源隔离 (Resources Isolation)
所有自定义字符串均存放在 `res/values*/strings_custom.xml` 中。
*   **规范**: 新增字符串 ID 建议以功能模块名开头（如 `explore_...`），避免与上游 `strings.xml` 冲突。
*   **引用**: 在 Kotlin 中通过 `io.legado.app.R.string.xxx` 正常引用。

### 2. 逻辑注入 (Dependency Injection)
增强功能通过 Koin 进行管理。
*   **入口**: `EnhanceModule.kt` 定义了所有增强型的单例（Repositories）和 ViewModel。
*   **Hook 点**: 主项目在 `App.kt` 的 `startKoin` 中加载 `enhanceModule`。

### 3. 构建自动化 (Build Hooks)
*   **`setting-search.gradle`**: 一个自定义的 Groovy 脚本，Hook 在 `preBuild` 阶段。它负责扫描整个 `app` 模块的 Compose 代码，自动维护设置项搜索索引。

## 功能模块说明

| 模块名 | 路径 | 核心描述 |
| :--- | :--- | :--- |
| **发现页套件** | `explore/` | 实现瀑布流布局、多级类目自动解析及筛选树构建。 |
| **书源能力检测** | `source/` | 动态识别书评、段评和其他评论能力，不修改 `BookSource` 数据模型，也不写入虚拟书源分组。 |
| **设置搜索** | `settingssearch/` | 提供全局设置项搜索、精准滚动定位及背景高亮动画。 |
| **WebDAV 增强** | `webdav/` | 提供书籍实体（EPUB/TXT）的增量云同步逻辑。 |
| **UI 委托** | `ui/` | 承载“我的”页面逻辑扩展及通用的滚动控制。 |
| **持久化模型** | `model/` | `CustomSettings.kt` 统一定义所有自定义功能的开关和参数。 |

### 发现页 UI 边界
现代布局的书源选择弹窗只负责展示：弹窗固定宽度，超长书源名单行滚动；搜索框通过弹窗的固定 Header 插槽独立于滚动书源列表，列表自动从搜索框下方占用剩余空间。默认书源定位只按列表索引滚动，不依赖搜索框高度或固定 dp 偏移，因此以后调整搜索框高度、padding、字体或主题时不需要同步修改列表定位逻辑。书源动作、`ExploreKind` 协议和 JS 行为继续复用主项目共享实现。

## 开发规范与维护

### 修改已有功能
1.  **代码修改**: 直接在 `java/` 目录下操作。
2.  **提交指令**:
    ```bash
    cd modules/legado-enhance
    git add .
    git commit -m "feat: your description"
    git push origin beta
    ```

### 新增增强模块
1.  在 `enhance/` 下创建新包。
2.  在 `EnhanceModule.kt` 中注册相关的单例或 ViewModel。
3.  在子目录中添加 `README.md` 描述其业务逻辑，方便后续维护。
