import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { saveUploadFile, removeUploadFile } from '@/utils/indexeddb'

let nextId = 1
let cleanupTimer = null
let processing = false

// 每个 item 的平滑进度定时器和速率波动定时器（非响应式）
const progressTimers = new Map()  // itemId -> { progressTimer, speedTimer }

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
        displayProgress: 0,       // 界面实际显示的进度（平滑驱动）
        uploadPhase: 'preparing', // 'preparing' | 'uploading' | 'merging'
        batchSpeed: 0,            // 上一批次实际速率 (bytes/s)
        displaySpeed: '',         // 界面显示的速率字符串（含微波动）
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
      displayProgress: 0,
      uploadPhase: 'uploading',
      batchSpeed: 0,
      displaySpeed: '',
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
      // 小文件路径：直接同步 displayProgress
      if (!item.isFolder && item.size <= 10 * 1024 * 1024) {
        item.displayProgress = item.progress
      }
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

  /**
   * 停止指定 item 的平滑进度和速率波动定时器
   */
  function stopSmoothTimers(id) {
    const timers = progressTimers.get(id)
    if (timers) {
      if (timers.progressTimer) clearInterval(timers.progressTimer)
      if (timers.speedTimer) clearInterval(timers.speedTimer)
      progressTimers.delete(id)
    }
  }

  /**
   * 启动平滑进度条：在 batchDuration 时间内，将 displayProgress 从当前值平滑增长到 targetPercent。
   * 同时启动速率波动显示。
   *
   * @param {number} id - item id
   * @param {number} batchDuration - 上一批次上传耗时（毫秒）
   * @param {number} targetPercent - 目标百分比（0-99）
   * @param {number} batchSpeed - 上一批次实际速率（bytes/s）
   */
  function startSmoothProgress(id, batchDuration, targetPercent, batchSpeed) {
    const item = queue.value.find(i => i.id === id)
    if (!item) return

    // 更新阶段为 uploading（从 preparing 切换）
    if (item.uploadPhase === 'preparing') {
      item.uploadPhase = 'uploading'
    }

    // 记录批次速率
    item.batchSpeed = batchSpeed

    // 停掉之前的定时器
    stopSmoothTimers(id)

    const startValue = item.displayProgress
    const delta = targetPercent - startValue
    if (delta <= 0) return

    // 确保 batchDuration 至少 200ms，避免过快
    const duration = Math.max(batchDuration, 200)
    const stepInterval = 50  // 每 50ms 步进一次
    const totalSteps = Math.max(1, Math.floor(duration / stepInterval))
    const increment = delta / totalSteps
    let currentStep = 0

    const progressTimer = setInterval(() => {
      currentStep++
      if (currentStep >= totalSteps || item.displayProgress >= targetPercent) {
        item.displayProgress = Math.min(99, targetPercent)
        clearInterval(progressTimer)
        // 不停速率定时器，让它继续显示（但不再增长时速率归零）
        // 速率波动定时器会在下一个 startSmoothProgress 调用时被替换
        return
      }
      item.displayProgress = Math.min(99, startValue + increment * currentStep)
    }, stepInterval)

    // 速率波动定时器：每 1s 更新一次 displaySpeed，在 batchSpeed 基础上 ±5% 波动
    const speedTimer = setInterval(() => {
      // 如果进度已经到达目标（不再增长），显示 0
      if (item.displayProgress >= targetPercent && currentStep >= totalSteps) {
        item.displaySpeed = '0 KB/s'
        return
      }
      // 微波动：±5%
      const fluctuation = 1 + (Math.random() - 0.5) * 0.1
      item.displaySpeed = formatSpeed(batchSpeed * fluctuation)
    }, 1000)

    // 立即设置一次速率
    item.displaySpeed = formatSpeed(batchSpeed)

    progressTimers.set(id, { progressTimer, speedTimer })
  }

  /**
   * 所有分片上传完毕，进入合并阶段。进度到 99 后停止增长。
   */
  function setMergingPhase(id) {
    const item = queue.value.find(i => i.id === id)
    if (!item) return

    item.uploadPhase = 'merging'

    // 停掉旧的平滑定时器
    stopSmoothTimers(id)

    // 将 displayProgress 平滑增长到 99
    const startValue = item.displayProgress
    const delta = 99 - startValue
    if (delta <= 0) {
      item.displayProgress = 99
      item.displaySpeed = '0 KB/s'
      return
    }

    // 用 1.5 秒平滑增长到 99
    const stepInterval = 50
    const totalSteps = 30  // 1500ms / 50ms
    const increment = delta / totalSteps
    let currentStep = 0

    const progressTimer = setInterval(() => {
      currentStep++
      if (currentStep >= totalSteps) {
        item.displayProgress = 99
        clearInterval(progressTimer)
        return
      }
      item.displayProgress = Math.min(99, startValue + increment * currentStep)
    }, stepInterval)

    // 合并阶段速率显示为 0（传输已完成）
    item.displaySpeed = '0 KB/s'
    const speedTimer = setInterval(() => {
      item.displaySpeed = '0 KB/s'
    }, 1000)

    progressTimers.set(id, { progressTimer, speedTimer })
  }

  /**
   * 后端合并完成，进度设为 100。
   */
  function completeMerge(id) {
    const item = queue.value.find(i => i.id === id)
    if (!item) return

    stopSmoothTimers(id)
    item.displayProgress = 100
    item.displaySpeed = ''
    item.uploadPhase = 'done'
  }

  function markDone(id) {
    stopSmoothTimers(id)
    const item = queue.value.find(i => i.id === id)
    if (item) {
      item.status = 'done'
      item.progress = 100
      item.displayProgress = 100
      item.speed = ''
      item.displaySpeed = ''
      item.uploadPhase = 'done'
      removeUploadFile(id).catch(() => {})
    }
  }

  function markFailed(id, error) {
    stopSmoothTimers(id)
    const item = queue.value.find(i => i.id === id)
    if (item) {
      item.status = 'failed'
      item.error = error
      item.speed = ''
      item.displaySpeed = ''
      removeUploadFile(id).catch(() => {})
    }
  }

  function removeItem(id) {
    stopSmoothTimers(id)
    queue.value = queue.value.filter(i => i.id !== id)
    removeUploadFile(id).catch(() => {})
  }

  function clearDone() {
    queue.value.forEach(i => {
      if (i.status === 'done' || i.status === 'failed') {
        stopSmoothTimers(i.id)
      }
    })
    queue.value = queue.value.filter(i => i.status !== 'done')
  }

  function reset() {
    progressTimers.forEach((timers) => {
      if (timers.progressTimer) clearInterval(timers.progressTimer)
      if (timers.speedTimer) clearInterval(timers.speedTimer)
    })
    progressTimers.clear()
    queue.value = []
  }

  function startCleanup() {
    if (cleanupTimer) return
    cleanupTimer = setInterval(() => {
      const now = Date.now()
      const cutoff = now - 24 * 60 * 60 * 1000
      queue.value = queue.value.filter(i => {
        if ((i.status === 'done' || i.status === 'failed' || i.status === 'paused') && i.createdAt < cutoff) {
          stopSmoothTimers(i.id)
          removeUploadFile(i.id).catch(() => {})
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
    startSmoothProgress, setMergingPhase, completeMerge, stopSmoothTimers,
    markDone, markFailed,
    removeItem, clearDone, reset,
    startCleanup, stopCleanup
  }
})
