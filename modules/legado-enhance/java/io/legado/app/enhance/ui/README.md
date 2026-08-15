# UI Delegation & Animation (UI 增强委托与动画)

本包承载了增强功能与主项目 Compose 界面之间的交互逻辑，主要采用“委托”模式实现。

## 主要功能

### 1. 业务逻辑委托 (`MyViewModelEnhance.kt`)
*   **解耦设计**: “我的”页面（MyFragment/MyViewModel）的复杂增强逻辑（如初始化搜索索引、处理 WebDAV 一键导入导出指令）被封装在 `MyViewModelEnhance` 中。
*   **状态管理**: 通过 `EnhanceState` 统一管理增强功能的 UI 状态，不污染主项目的核心 ViewModel。

### 2. 交互增强动画 (`SettingScrollEnhance.kt`)
*   **`LaunchSettingScrollEffect`**: 这是一个 Compose 组件。当传入 `scrollInfo`（包含目标索引）时，会自动触发滚动。
*   **精准度**: 支持 `scrollOffset` 计算，确保目标项滚动到屏幕顶部的黄金位置，而不仅仅是进入可视区域。

## 开发建议

*   **Compose 侵入**: 在主项目的 Screen 中，尽量只添加一行 `LaunchSettingScrollEffect(...)`。
*   **性能**: 避免在 `MyViewModelEnhance` 中执行耗时的磁盘 IO，所有操作应通过对应的 Repository 切换到 `Dispatchers.IO`。
