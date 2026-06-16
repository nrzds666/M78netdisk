# M78 NetDisk 分片上传 · 暂停 · 续传完整逻辑

## 一、分片上传

### 前端：FileListView.vue → confirmUpload()

```
用户选文件 → 点击"开始上传"
  ↓
addFiles(files) → 创建 queue 条目: { taskId:null, abortController:null, status:'uploading', file:File对象, ... }
  ↓
for 每个文件:
  if size > 10MB → chunkedUpload(file, parentId, onProgress, ..., abortController.signal)
  else           → uploadApi() 单请求上传
```

### 前端：api/file.js → chunkedUpload()

```
Step 1: Init
  if resumeTaskId:
    taskId = resumeTaskId          ← 续传：复用已有 task
  else:
    POST /api/files/upload/init    ← 新上传：创建 task
      → backend initUpload():
          1. 检查空间/重名
          2. Insert upload_tasks (status='pending')
          3. 如果是 OSS: initiateMultipartUpload → 拿到 uploadId
        返回 UploadTaskVO { taskId, fileName, ... }

Step 2: 逐分片上传
  for i = 0..totalChunks-1:
    if skipSet.has(i): continue               ← 续传时跳过已有分片
    if signal?.aborted: throw Error('paused') ← 暂停检测
    chunkBlob = file.slice(start, end)
    POST /api/files/upload/{taskId}/chunk/{i}
      → backend uploadChunk():
          1. 查 task → 校验 ownder、chunkIndex 合法性
          2. if status == 'pending' → 改为 'uploading'
          3. 刷新 expiresAt (updateById 或 LambdaUpdateWrapper)
          4. if uploadId != null (OSS):
               storageService.uploadPart(mergedKey, uploadId, chunkIndex+1, stream)
               → 返回 "partNumber:etag"
               Insert upload_chunks (taskId, chunkIndex, partNumber, etag, ...)
               incrementReceivedChunks(taskId)
             else (Local):
               storageService.store(key, stream)
               Insert upload_chunks (taskId, chunkIndex, etag="", storageKey)
               incrementReceivedChunks(taskId)
    uploadedChunks++

Step 3: Complete
  POST /api/files/upload/complete?taskId=X
    → backend completeUpload():
        1. 查 task → 校验
        2. if receivedChunks < totalChunks: throw "分片尚未全部上传完成"
        3. if status == 'expired'/'completed': throw
        4. if uploadId != null (OSS):
             status = 'merging'
             查 upload_chunks ORDER BY partNumber
             storageService.completeMultipartUpload(mergedKey, uploadId, partETags)
             Insert Item + ItemVersion
             扣减配额
             status = 'completed'
           else (Local):
             status = 'merging'
             uploadMergeService.performMerge()  ← 异步流式合并
        返回 UploadTaskVO

  轮询合并状态 (仅 OSS 的 complete 和 Local 的 performMerge 需要等待):
    for retry 0..150 (每次等 2s):
      GET /api/files/upload/status?taskId=X
        → 查 task.status: 'completed' | 'failed' | 'merging'
```

---

## 二、暂停

### 前端：TransferView.vue → pauseItem()

```
用户点击暂停按钮
  ↓
1. item.abortController.abort()                     ← 立即中断前端上传
   → chunkedUpload 的 signal.aborted = true
   → 如果在 for 循环头部: if (signal?.aborted) throw Error('paused')
   → 如果在 await uploadChunk() 中: axios 请求被取消 → catch 转 Error('paused')
   → confirmUpload 中的 catch: msg.includes('paused') → continue
   → 条目维持在 queue 中，status 不变（由步骤3设置）

2. await uploadPause(item.taskId)                    ← 通知后端
   POST /api/files/upload/{taskId}/pause
     → backend pauseUpload(): UPDATE upload_tasks SET status='paused' WHERE id=taskId

3. item.status = 'paused'                            ← 前端标记暂停

4. loadUnfinishedTasks()                             ← 刷新"未完成的上传"列表
   GET /api/files/upload/tasks
     → backend listUnfinishedTasks(): 查 status IN ('pending','uploading','merging','paused')
   unfinishTasks.value = 返回结果中排除 queue 中 status==='uploading' 的条目
```

### 暂停后的状态

```
uploadStore.queue:   [{ id: X, taskId: Y, status: 'paused', file: File对象, ... }]
upload_tasks DB:     status='paused', receivedChunks=N
unfinishedTasks UI:  显示在"未完成的上传"区域
```

---

## 三、续传

### 前端：TransferView.vue → resumeTask(task)

**Path A — 同会话续传（有 File 对象）：**

```
const stored = uploadStore.queue.find(
  i => i.taskId === task.taskId       ← 匹配后端 taskId
    && i.status === 'paused'          ← 暂停状态
    && i.file                         ← File 对象还在
)

if stored:
  stored.status = 'uploading'
  新建 AbortController
  GET /api/files/upload/tasks/{taskId}/chunks
    → backend getCompletedChunks(): 查 upload_chunks WHERE taskId → 返回 [0, 1, ...]
  skipChunks = chunksRes.data || []

  chunkedUpload(
    stored.file,          ← 复用 File 对象
    task.parentId,        ← 注意：UploadTaskVO 没有 parentId，此处为 undefined
    onProgress,
    task.fileName,        ← 文件名
    task.taskId,          ← resumeTaskId → 跳过 init
    skipChunks,           ← 跳过已有分片
    onTaskInit,
    signal
  )
  ↓
  chunkedUpload 内部:
  taskId = resumeTaskId（复用）
  for i: skipSet.has(i) → continue（跳过已有分片）
          否则 → uploadChunk(taskId, i, chunkBlob, signal)
  uploadComplete(taskId)
  ↓
  markDone
  ElMessage.success('文件续传完成')
```

**Path B — 跨会话续传（无 File 对象）：**

```
// 没有 File 对象，必须让用户重新选择文件
创建隐藏的 <input type="file">
用户选择文件后:
  uploadStore.addFiles([file])[0]   ← 新建队列条目
  getCompletedChunks(taskId) → skipChunks
  chunkedUpload(file, ..., task.taskId, skipChunks, ...)
```

---

## 四、后端方法明细

### uploadChunk() — FileServiceImpl.java:510

```
@Transactional
uploadChunk(ownerId, taskId, chunkIndex, file):
  1. uploadTaskMapper.selectById(taskId)
  2. if task == null → throw
  3. if !task.ownerId.equals(ownerId) → throw
  4. if chunkIndex < 0 || >= totalChunks → throw
  5. if status == 'pending' → status='uploading', updateById 或 LambdaUpdateWrapper(仅status)
  6. 刷新 expiresAt（updateById 或 LambdaUpdateWrapper(仅expiresAt)）
  7. if uploadId != null (OSS):
       partEtag = storageService.uploadPart(mergedKey, uploadId, chunkIndex+1, stream)
       → 返回 "partNumber:etag"
       try:
         insert upload_chunks (taskId, chunkIndex, partNumber, size, etag, storageKey)
         incrementReceivedChunks(taskId)    ← UPDATE SET received_chunks = received_chunks + 1
       catch DuplicateKeyException:
         skip（幂等）
     else (Local):
       store 到本地
       try:
         insert upload_chunks
         incrementReceivedChunks
       catch DuplicateKeyException:
         skip
```

### completeUpload() — FileServiceImpl.java:634

```
@Transactional
completeUpload(ownerId, taskId):
  1. selectById → 校验
  2. if status in ('expired','completed') → throw
  3. if receivedChunks < totalChunks → throw "分片尚未全部上传完成"
  4. 重名检查
  5. if uploadId != null (OSS):
       status = 'merging'
       chunks = selectList WHERE taskId ORDER BY partNumber ASC
       partETags = chunks.stream().map(UploadChunk::getEtag)
       completeMultipartUpload(mergedKey, uploadId, partETags)
       → OSS 内部按 partNumber 排序合并
       Insert Item + ItemVersion
       扣减配额
       status = 'completed'
     else (Local):
       status = 'merging'
       uploadMergeService.performMerge(ownerId, taskId)  ← 异步
```

---

## 五、已发现的问题

| # | 环节 | 问题 | 修复 |
|---|------|------|------|
| 1 | 暂停 item | `abort()` 在 `uploadPause()` 之后 → 暂停慢 | 已修：交换顺序 |
| 2 | uploadChunk | `updateById` 覆盖整行 → `receivedChunks` 被旧值回写 | 已修：改为 `LambdaUpdateWrapper` 精准更新 |
| 3 | 历史数据 | 修复前产生的 task 存在 receivedChunks 不准 | 已修：手动 UPDATE 修正 |
| 4 | 服务器 | 后端代码编译后没重启 → 修复未生效 | 已重启 |
