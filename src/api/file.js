import request from './request'

/**
 * List files/folders in a directory
 * @param {string|null} parentId - parent folder ID, null for root
 * @param {number} page - page number (1-based)
 * @param {number} size - page size
 * @param {object} filters - optional { query, type, dateFrom, dateTo }
 * @returns {Promise}
 */
export function listItems(parentId, page = 1, size = 20, filters = {}) {
  const params = { page, size }
  if (parentId) {
    params.parentId = parentId
  }
  if (filters.query) params.query = filters.query
  if (filters.type) params.type = filters.type
  if (filters.dateFrom) params.dateFrom = filters.dateFrom
  if (filters.dateTo) params.dateTo = filters.dateTo
  return request.get('/files/list', { params })
}

/**
 * Create a new folder
 * @param {string} name - folder name
 * @param {string|null} parentId - parent folder ID, null for root
 * @returns {Promise}
 */
export function createFolder(name, parentId) {
  return request.post('/files/folder', { name, parentId })
}

/**
 * Rename a file or folder
 * @param {string} itemId - file/folder ID
 * @param {string} newName - new name
 * @returns {Promise}
 */
export function rename(itemId, newName) {
  return request.put('/files/rename', { itemId, newName })
}

/**
 * Move items to trash
 * @param {string[]} ids - array of file/folder IDs
 * @returns {Promise}
 */
export function moveToTrash(ids) {
  return request.delete('/files/trash', { params: { ids } })
}

/**
 * Restore items from trash
 * @param {string[]} ids - array of file/folder IDs
 * @returns {Promise}
 */
export function restore(ids) {
  return request.post('/files/restore', null, { params: { ids } })
}

/**
 * Permanently delete items
 * @param {string[]} ids - array of file/folder IDs
 * @returns {Promise}
 */
export function permanentDelete(ids) {
  return request.delete('/files/permanent', { params: { ids } })
}

/**
 * List trash items
 * @param {number} page - page number (1-based)
 * @param {number} size - page size
 * @returns {Promise}
 */
export function listTrash(page = 1, size = 20) {
  return request.get('/files/trash', { params: { page, size } })
}

/**
 * Upload a file
 * @param {File} file - the file to upload
 * @param {string|null} parentId - target folder ID, null for root
 * @param {function} onProgress - optional progress callback (percent 0-100)
 * @param {number} timeout - optional timeout in ms (default 600000 = 10min)
 * @param {string} fileName - optional filename override (strips path separators)
 * @param {AbortSignal} signal - optional: abort signal for pause
 * @returns {Promise}
 */
export function upload(file, parentId, onProgress, timeout, fileName, signal) {
  const formData = new FormData()
  // 传入 fileName 时覆写 multipart 的 Content-Disposition filename，避免路径分隔符
  if (fileName) {
    formData.append('file', file, fileName)
  } else {
    formData.append('file', file)
  }
  if (parentId) {
    formData.append('parentId', parentId)
  }
  const config = {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: timeout || 600000,
    signal
  }
  if (onProgress) {
    config.onUploadProgress = (progressEvent) => {
      if (progressEvent.total > 0) {
        // 上限 99%——真正的 100% 由 markDone 在响应到达后设置
        const percent = Math.min(99, Math.round((progressEvent.loaded / progressEvent.total) * 100))
        onProgress(percent)
      }
    }
  }
  return request.post('/files/upload', formData, config)
}

/**
 * 初始化分片上传
 * @param {{ fileName, fileSize, mimeType, parentId, chunkSize }} data
 * @returns {Promise}
 */
export function uploadInit(data) {
  return request.post('/files/upload/init', data)
}

/**
 * 上传单个分片
 * @param {number} taskId
 * @param {number} index - chunk index
 * @param {Blob} chunk - chunk data
 * @returns {Promise}
 */
export function uploadChunk(taskId, index, chunk, signal) {
  const formData = new FormData()
  formData.append('file', chunk)
  const config = {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 600000
  }
  if (signal) config.signal = signal
  return request.post(`/files/upload/${taskId}/chunk/${index}`, formData, config)
}

/**
 * 完成分片上传（合并分片）
 * @param {number} taskId
 * @returns {Promise}
 */
export function uploadComplete(taskId) {
  return request.post('/files/upload/complete', null, { params: { taskId }, timeout: 600000 })
}

/**
 * 取消分片上传
 * @param {number} taskId
 * @returns {Promise}
 */
export function uploadPause(taskId) {
  return request.post(`/files/upload/${taskId}/pause`)
}

export function uploadCancel(taskId) {
  return request.post('/files/upload/cancel', null, { params: { taskId } })
}

/**
 * 获取上传状态（轮询用）
 * @param {number} taskId
 * @returns {Promise}
 */
export function uploadStatus(taskId) {
  return request.get('/files/upload/status', { params: { taskId } })
}

/**
 * 列出未完成的上传任务
 * @returns {Promise}
 */
export function listUnfinishedTasks() {
  return request.get('/files/upload/tasks')
}

/**
 * 获取已完成的分片索引列表
 * @param {number} taskId
 * @returns {Promise}
 */
export function getCompletedChunks(taskId) {
  return request.get(`/files/upload/tasks/${taskId}/chunks`)
}

/**
 * 删除上传任务及其所有分片（存储 + DB）
 * @param {number} taskId
 * @returns {Promise}
 */
export function deleteUploadTask(taskId) {
  return request.delete(`/files/upload/tasks/${taskId}`)
}

/**
 * 根据文件大小估算总分片数，与 chunkedUpload 内部公式完全一致。
 * 用于在未调用 chunkedUpload 时（如 resumeItem / onMounted）将已上传分片个数转为百分比。
 * @param {number} fileSize - 文件字节数
 * @returns {number} 总分片数
 */
export function estimateTotalChunks(fileSize) {
  const TARGET_CHUNK_SIZE = 5 * 1024 * 1024
  const MIN_CHUNKS = 20
  const MAX_CHUNKS = 500
  return Math.max(MIN_CHUNKS, Math.min(MAX_CHUNKS, Math.ceil(fileSize / TARGET_CHUNK_SIZE)))
}

/**
 * 分片上传文件（> 10MB 走此路径），支持断点续传和异步合并轮询
 * <p>
 * 客户端用 File.slice() 切为固定分片，逐个 HTTP 上传，
 * 服务端存储后异步合并。进度 = (已发送分片 / 总分片) * 100，真实反映网络传输。
 *
 * @param {File} file
 * @param {number|null} parentId
 * @param {function} onProgress - overall progress 0-100 (chunk upload phase)
 * @param {string} fileName - optional filename override
 * @param {number} resumeTaskId - optional: resume an existing task instead of init new
 * @param {number[]} skipChunks - optional: chunk indices to skip (already uploaded)
 * @param {function} onTaskInit - optional: called with (taskId) after task init/resume
 * @param {AbortSignal} signal - optional: abort signal for pause
 * @param {function} onBatchComplete - optional: called after each batch with { batchIndex, batchCount, batchDuration, batchBytes, totalPercent }
 * @returns {Promise}
 */
export async function chunkedUpload(file, parentId, onProgress, fileName, resumeTaskId, skipChunks, onTaskInit, signal, onBatchComplete) {
  // 动态分片：目标每片 5MB，最少 20 片，最多 500 片
  // 与后端 InitUploadDTO 默认值 (5MB) 对齐，确保 resume 时 chunk 边界一致
  const totalChunks = estimateTotalChunks(file.size)
  const chunkSize = Math.ceil(file.size / totalChunks)
  const CONCURRENCY = 3
  const name = fileName || file.name
  const skipSet = new Set(skipChunks || [])

  // Step 1: Init or resume
  let taskId
  if (resumeTaskId) {
    taskId = resumeTaskId
  } else {
    const initRes = await uploadInit({
      fileName: name,
      fileSize: file.size,
      mimeType: file.type || 'application/octet-stream',
      parentId: parentId || 0,
      chunkSize: chunkSize
    })
    taskId = initRes.data.taskId
  }

  let uploadedChunks = skipSet.size

  // 通知调用方 taskId（用于双进度条过滤）
  if (onTaskInit) onTaskInit(taskId)

  // 收集需要上传的分片索引
  const pending = []
  for (let i = 0; i < totalChunks; i++) {
    if (!skipSet.has(i)) pending.push(i)
  }

  // 计算总批次数（用于 onBatchComplete）
  const totalBatches = Math.ceil(pending.length / CONCURRENCY)
  let batchIndex = 0

  try {
    // Step 2: Upload chunks concurrently in batches
    while (pending.length > 0) {
      if (signal?.aborted) throw new Error('paused')
      const batch = pending.splice(0, CONCURRENCY)
      batchIndex++

      // 记录当前批次开始时间
      const batchStartTime = Date.now()

      // 计算当前批次的总字节数
      let batchBytes = 0
      const batchPromises = batch.map(async (idx) => {
        const start = idx * chunkSize
        const end = Math.min(start + chunkSize, file.size)
        batchBytes += (end - start)
        const chunkBlob = file.slice(start, end)
        await uploadChunk(taskId, idx, chunkBlob, signal)
      })

      await Promise.all(batchPromises)

      // 计算当前批次耗时
      const batchDuration = Date.now() - batchStartTime

      uploadedChunks += batch.length
      const totalPercent = Math.min(99, Math.round((uploadedChunks / totalChunks) * 100))

      if (onProgress) {
        onProgress(totalPercent)
      }

      // 批次完成回调：提供耗时、字节数、百分比等信息
      if (onBatchComplete) {
        onBatchComplete({
          batchIndex,
          batchCount: totalBatches,
          batchDuration,    // 毫秒
          batchBytes,       // 字节数
          totalPercent      // 总体百分比 0-99
        })
      }
    }

    // Step 3: Complete → async merge, poll until done
    await uploadComplete(taskId)

    // Poll status until merge completes
    for (let retry = 0; retry < 150; retry++) {
      if (signal?.aborted) throw new Error('paused')
      await new Promise(r => setTimeout(r, 2000))
      // 合并中持续更新进度，不让进度条停住
      if (onProgress) onProgress(99)
      const statusRes = await uploadStatus(taskId)
      const status = statusRes.data.status
      if (status === 'completed') {
        return statusRes
      }
      if (status === 'failed') {
        throw new Error('文件合并失败')
      }
      // merging → continue polling
    }
    throw new Error('文件合并超时，请稍后刷新查看')
  } catch (e) {
    // 信号中断（暂停）统一转为 paused 错误，避免被标记为失败
    if (e.code === 'ERR_CANCELED' || e.name === 'CanceledError' || signal?.aborted) {
      throw new Error('paused')
    }
    throw e
  }
}

/**
 * Download a file
 * @param {string} id - file ID
 * @returns {Promise} - returns a blob response
 */
export function download(id) {
  return request.get(`/files/download/${id}`, { responseType: 'blob' })
}

/**
 * Preview a file (get URL or metadata)
 * @param {string} id - file ID
 * @returns {Promise}
 */
export function preview(id) {
  return request.get(`/files/preview/${id}`)
}

/**
 * Move items to a different folder
 * @param {number[]} itemIds - array of file/folder IDs
 * @param {number|null} targetParentId - target folder ID, null for root
 * @returns {Promise}
 */
export function moveItems(itemIds, targetParentId) {
  return request.put('/files/move', { itemIds, targetParentId })
}

/**
 * Batch download multiple files/folders as a ZIP
 * @param {number[]} itemIds - array of file/folder IDs
 */
export function batchDownloadUrl(itemIds) {
  const base = import.meta.env.VITE_APP_BASE_API || ''
  const token = localStorage.getItem('m78_token')
  const ids = itemIds.join(',')
  return `${base}/files/download/batch?ids=${ids}&token=${encodeURIComponent(token)}`
}

/**
 * List recently accessed items
 * @param {number} days - look back days
 * @returns {Promise}
 */
export function recentItems(days = 7) {
  return request.get('/files/recent', { params: { days } })
}

/**
 * List recently saved items
 * @param {number} days - look back days
 * @returns {Promise}
 */
export function recentSaves(days = 7) {
  return request.get('/files/recent-saves', { params: { days } })
}
