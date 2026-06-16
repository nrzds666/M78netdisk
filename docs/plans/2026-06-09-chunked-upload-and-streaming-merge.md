# 分片上传 + 流式合并实施方案

## 架构：客户端分片，服务端流式合并

文件 > 10MB → 客户端 `File.slice()` 切为 5MB 分片 → 逐个 HTTP 上传 → 服务端存储 → 合并

## 后端改动

### #① 修复 `mergeChunks()` 流式化 (TDD)

- **文件**: `FileServiceImpl.java:864-887`
- **当前**: `ByteArrayOutputStream` → `baos.toByteArray()` → `store(byte[])` (OOM)
- **改成**: 自定义 lazy InputStream 逐个读取分片流 → `store(InputStream)` (O(1)内存)

### #② 新增分片上传端点 (TDD)

- **控制器**: `FileController.java` → 新增 `POST /api/files/upload/{taskId}/chunk/{index}`
- **服务**: `FileServiceImpl.java` → 新增 `uploadChunk(Long ownerId, Long taskId, Integer index, MultipartFile file)`
  - 校验任务存在
  - 存储分片到 `{task.storagePrefix}/chunk_{index}`
  - 写入 `upload_chunks` 表
  - 递增 `receivedChunks`
- **适配 confirmChunk**: 新增端点已包含存储+记录，confirmChunk 保留作幂等兼容

## 前端改动

### #③ 分片上传核心逻辑

- **新增**: `src/api/file.js` → `chunkUpload(file, parentId, onProgress)` 函数
  - 文件 > 10MB 走此路径
  - `initUpload` → 循环切片上传 → `completeUpload`
  - 进度 = `(已发送分片 / 总分片) * 100`

### #④ 整合 FileListView.vue

- `confirmUpload()`: 文件 > 10MB 走 `chunkUpload`，< 10MB 走原有 `uploadApi`
- `handleFolderSelected()`: 同上
- 整合 `uploadStore` 进度

## 验证

- [ ] `mvn test` 全量通过 (后端)
- [ ] 前端 npm 可运行
