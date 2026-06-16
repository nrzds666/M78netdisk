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
 * @returns {Promise}
 */
export function upload(file, parentId, onProgress, timeout, fileName) {
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
    timeout: timeout || 600000
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
 * 分片上传文件（> 10MB 走此路径），支持断点续传和异步合并轮询
 * <p>
 * 客户端用 File.slice() 切为 5MB 分片，逐个 HTTP 上传，
 * 服务端存储后异步合并。进度 = (已发送分片 / 总分片) * 100，真实反映网络传输。
 *
 * @param {File} file
 * @param {number|null} parentId
 * @param {function} onProgress - overall progress 0-100 (chunk upload phase)
 * @param {string} fileName - optional filename override
 * @param {number} resumeTaskId - optional: resume an existing task instead of init new
 * @param {number[]} skipChunks - optional: chunk indices to skip (already uploaded)
 * @param {function} onTaskInit - optional: called with (taskId) after task init/resume
 * @returns {Promise}
 */
export async function chunkedUpload(file, parentId, onProgress, fileName, resumeTaskId, skipChunks, onTaskInit, signal) {
  const totalChunks = 100
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

  try {
    // Step 2: Upload chunks concurrently in batches
    while (pending.length > 0) {
      if (signal?.aborted) throw new Error('paused')
      const batch = pending.splice(0, CONCURRENCY)

      await Promise.all(batch.map(async (idx) => {
        const start = idx * chunkSize
        const end = Math.min(start + chunkSize, file.size)
        const chunkBlob = file.slice(start, end)
        await uploadChunk(taskId, idx, chunkBlob, signal)
      }))

      uploadedChunks += batch.length
      if (onProgress) {
        // 封顶 99%，100% 留给合并完成后由调用方设置
        onProgress(Math.min(99, Math.round((uploadedChunks / totalChunks) * 100)))
      }
    }

    // Step 3: Complete → async merge, poll until done
    await uploadComplete(taskId)

    // Poll status until merge completes
    for (let retry = 0; retry < 150; retry++) {
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
