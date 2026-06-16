import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { saveUploadFile, removeUploadFile } from '@/utils/indexeddb'

let nextId = 1
let cleanupTimer = null
let processing = false

export const useUploadStore = defineStore('upload', () => {
  const queue = ref([])

  const uploadingItems = computed(() => queue.value.filter(i => i.status === 'uploading'))
  const pendingItems = computed(() => queue.value.filter(i => i.status === 'pending'))
  const doneItems = computed(() => queue.value.filter(i => i.status === 'done'))
  const failedItems = computed(() => queue.value.filter(i => i.status === 'failed'))
  const activeCount = computed(() => uploadingItems.value.length)
  const totalCount = computed(() => queue.value.length)

  function addFiles(files, parentId = null, skipIndexedDB = false) {
    const items = []
    for (const file of files) {
      const item = {
        id: nextId++,
        name: file.name || file.webkitRelativePath || `file-${nextId}`,
        size: file.size || 0,
        progress: 0,
        speed: '',
        status: 'pending',
        file,
        lastBytes: 0,
        lastTime: Date.now(),
        isFolder: false,
        taskId: null,
        parentId,
        abortController: null,
        pulseTrigger: false,
        createdAt: Date.now(),
        failedFiles: [],
        skipChunks: [],
        initialProgress: 0
      }
      queue.value.push(item)
      items.push(item)
      // 异步存 IndexedDB，失败不影响上传
      if (!skipIndexedDB) {
        saveUploadFile(item.id, file, parentId).catch(() => {})
      }
    }
    return items
  }

  /**
   * Add a folder entry to the queue. Progress = completed files / total.
   */
  function addFolder(folderName, fileCount, totalSize) {
    const item = {
      id: nextId++,
      name: folderName,
      size: totalSize,
      fileCount,
      completedFiles: 0,
      progress: 0,
      speed: '',
      status: 'uploading',
      isFolder: true,
      children: []
    }
    queue.value.push(item)
    return item
  }

  /**
   * 取第一个 pending 项，改为 uploading
   */
  function startNext() {
    const item = queue.value.find(i => i.status === 'pending')
    if (!item) return null
    item.status = 'uploading'
    item.lastTime = Date.now()
    return item
  }

  /**
   * 获取互斥锁。true=可以开始，false=已有在处理
   */
  function beginProcessing() {
    if (processing) return false
    processing = true
    return true
  }

  /**
   * 释放互斥锁
   */
  function endProcessing() {
    processing = false
  }

  function updateProgress(id, progress) {
    const item = queue.value.find(i => i.id === id)
    if (item) {
      item.progress = Math.min(100, Math.max(0, progress))
      updateSpeed(item, 0)
    }
  }

  /**
   * Update progress with byte delta for speed calculation.
   */
  function updateProgressBytes(id, progress, bytesDelta) {
    const item = queue.value.find(i => i.id === id)
    if (item) {
      item.progress = Math.min(100, Math.max(0, progress))
      updateSpeed(item, bytesDelta)
    }
  }

  function updateSpeed(item, bytesDelta) {
    const now = Date.now()
    item.lastBytes = (item.lastBytes || 0) + bytesDelta
    const elapsed = (now - (item.lastTime || now)) / 1000
    if (elapsed >= 2 && item.lastBytes > 0) {
      item.speed = formatSpeed(item.lastBytes / elapsed)
      item.lastBytes = 0
      item.lastTime = now
    }
  }

  function formatSpeed(bytesPerSec) {
    if (bytesPerSec >= 1048576) return (bytesPerSec / 1048576).toFixed(1) + ' MB/s'
    if (bytesPerSec >= 1024) return (bytesPerSec / 1024).toFixed(0) + ' KB/s'
    return bytesPerSec.toFixed(0) + ' B/s'
  }

  function markDone(id) {
    const item = queue.value.find(i => i.id === id)
    if (item) {
      item.status = 'done'
      item.progress = 100
      item.speed = ''
      removeUploadFile(id).catch(() => {})
    }
  }

  function markFailed(id, error) {
    const item = queue.value.find(i => i.id === id)
    if (item) {
      item.status = 'failed'
      item.error = error
      item.speed = ''
      removeUploadFile(id).catch(() => {})
    }
  }

  function removeItem(id) {
    queue.value = queue.value.filter(i => i.id !== id)
    removeUploadFile(id).catch(() => {})
  }

  function clearCompleted() {
    queue.value = queue.value.filter(i => i.status === 'uploading' || i.status === 'pending' || i.status === 'failed' || i.status === 'paused')
  }

  function reset() {
    queue.value = []
  }

  function startCleanup() {
    if (cleanupTimer) return
    cleanupTimer = setInterval(() => {
      const now = Date.now()
      const cutoff = now - 24 * 60 * 60 * 1000
      queue.value = queue.value.filter(i => {
        if ((i.status === 'done' || i.status === 'failed' || i.status === 'paused') && i.createdAt < cutoff) {
          return false
        }
        return true
      })
    }, 30000)
  }

  function stopCleanup() {
    if (cleanupTimer) {
      clearInterval(cleanupTimer)
      cleanupTimer = null
    }
  }

  return {
    queue, uploadingItems, pendingItems, doneItems, failedItems,
    activeCount, totalCount,
    addFiles, addFolder, updateProgress, updateProgressBytes,
    startNext, beginProcessing, endProcessing,
    markDone, markFailed,
    removeItem, clearCompleted, reset,
    startCleanup, stopCleanup
  }
})
