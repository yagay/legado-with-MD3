# WebDAV Enhance Module (WebDAV 书籍实体云同步)

本模块将 Legado 的 WebDAV 功能从单纯的“数据库备份”扩展到了“书籍内容同步（云盘体验）”。

## 关键功能实现

### 1. 增量导出逻辑 (`ExportBookService` 配合)
在上传前执行远程校验：
```kotlin
val remoteFile = runCatching { 
    WebDav(url, auth).getWebDavFile() 
}.getOrNull()
```
*   **大小比对**: 获取远程文件的 `getcontentlength` 属性。
*   **决策**: 如果 `remoteSize == localSize`，则跳过上传并记录为 `skippedCount`。
*   **健壮性**: 使用 `runCatching` 确保文件不存在时（404 异常）不会中断导出任务，而是正常进入上传流程。

### 2. URL 安全性处理
`WebDavEnhance.getHttpUrl` 负责将书名（可能含空格、特殊符号、Emoji）进行规范化的编码处理，确保 `PROPFIND` 请求在不同的 WebDAV 服务器（如坚果云、Nextcloud）上都能准确找到文件。

### 3. 批量静默导入
*   **扫描**: 遍历 WebDAV 上的 `books/` 目录。
*   **排重**: 根据书名和作者对比本地数据库。
*   **导入**: 自动下载缺失的文件并调用本地书籍导入逻辑。

## 维护建议

*   **统计信息**: 导出结果通过 `NotificationId.ExportBook` 发送。若需修改统计文案，请同步修改 `ExportBookService.kt`。
*   **连接超时**: 对于大文件同步，WebDAV Client 已在 `WebDav.kt` 中通过 `callTimeout(0)` 设为永不超时，修改时需谨慎。
