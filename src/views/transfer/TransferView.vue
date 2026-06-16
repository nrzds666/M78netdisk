<template>
  <div class="transfer-page">
    <el-card shadow="never" class="transfer-card">
      <el-tabs v-model="activeTab">
        <!-- Tab 1: Uploading (实时) -->
        <el-tab-pane label="上传中" name="uploading">
          <!-- Unfinished tasks (resume) -->
          <template v-if="unfinishedTasks.length > 0">
            <div class="section-label">未完成的上传</div>
            <div
              v-for="task in unfinishedTasks"
              :key="'resume-' + task.taskId"
              class="upload-item resume-item"
            >
              <div class="upload-item-info">
                <span class="upload-item-name" :title="task.fileName">{{ task.fileName }}</span>
                <span class="upload-item-size">{{ formatSize(task.fileSize) }}</span>
              </div>
              <el-progress
                :percentage="task.totalChunks > 0 ? Math.round(task.receivedChunks / task.totalChunks * 100) : 0"
                :stroke-width="8"
                style="width:100%"
              />
              <div style="margin-top:4px;display:flex;justify-content:flex-end">
                <el-button size="small" type="primary" @click="resumeTask(task)">继续上传</el-button>
                <el-button size="small" type="danger" @click="deleteUnfinished(task)">删除</el-button>
              </div>
            </div>
            <el-divider />
          </template>

          <div v-if="uploadStore.uploadingItems.length > 0" class="upload-summary">
            正在上传 {{ uploadStore.uploadingItems.length }} 个文件...
          </div>

          <template v-if="uploadStore.uploadingItems.length > 0">
            <div
              v-for="item in uploadStore.uploadingItems"
              :key="item.id"
              class="upload-item"
            >
              <template v-if="item.isFolder">
                <div class="upload-item-info">
                  <span class="upload-item-name folder-icon" :title="item.name">📁 {{ item.name }}</span>
                  <span class="upload-item-speed" v-if="item.speed">{{ item.speed }}</span>
                  <span class="upload-item-size">{{ item.completedFiles }}/{{ item.fileCount }} · {{ formatSize(item.size) }}</span>
                </div>
                <el-progress
                  :percentage="item.progress"
                  :status="item.progress === 100 ? 'success' : undefined"
                  :stroke-width="8"
                  style="width:100%"
                  :class="{ 'progress-active': item.status === 'uploading' }"
                />
                <div style="margin-top:4px;display:flex;justify-content:flex-end">
                  <el-button size="small" @click="pauseItem(item)">暂停</el-button>
                </div>
              </template>
              <template v-else>
                <div class="upload-item-info">
                  <span class="upload-item-name" :title="item.name">{{ item.name }}</span>
                  <span class="upload-item-speed" v-if="item.speed">{{ item.speed }}</span>
                  <span class="upload-item-size">{{ formatSize(item.size) }}</span>
                </div>
                <el-progress
                  :percentage="item.progress"
                  :status="item.progress === 100 ? 'success' : undefined"
                  :stroke-width="8"
                  style="width:100%"
                  :class="{ 'progress-active': item.status === 'uploading' }"
                />
                <div style="margin-top:4px;display:flex;justify-content:flex-end">
                  <el-button size="small" @click="pauseItem(item)">暂停</el-button>
                </div>
              </template>
            </div>
          </template>

          <!-- Pending items -->
          <template v-if="uploadStore.pendingItems.length > 0">
            <el-divider v-if="uploadStore.uploadingItems.length > 0" />
            <div class="upload-summary" style="color:#909399">等待上传 {{ uploadStore.pendingItems.length }} 个文件</div>
            <div
              v-for="item in uploadStore.pendingItems"
              :key="item.id"
              class="upload-item"
            >
              <div class="upload-item-info">
                <span class="upload-item-name" :title="item.name">{{ item.name }}</span>
                <span class="upload-item-size">{{ formatSize(item.size) }}</span>
                <el-tag size="small" type="info">等待中</el-tag>
              </div>
              <div style="margin-top:4px;display:flex;justify-content:flex-end">
                <el-button size="small" type="danger" plain @click="cancelPending(item)">取消</el-button>
              </div>
            </div>
          </template>

          <!-- Failed items -->
          <template v-if="uploadStore.failedItems.length > 0">
            <el-divider />
            <div class="section-label">上传失败</div>
            <div
              v-for="item in uploadStore.failedItems"
              :key="item.id"
              class="upload-item upload-item-failed"
            >
              <div class="upload-item-info">
                <span class="upload-item-name">{{ item.name }}</span>
                <el-tag type="danger" size="small">{{ item.error || '失败' }}</el-tag>
              </div>
              <div v-if="item.isFolder && item.failedFiles?.length" class="failed-files-list">
                <div v-for="(ff, fi) in item.failedFiles.slice(0, 10)" :key="fi" class="failed-file-item">{{ ff }}</div>
                <div v-if="item.failedFiles.length > 10" class="failed-file-item">... 还有 {{ item.failedFiles.length - 10 }} 个文件</div>
              </div>
              <div style="margin-top:4px;display:flex;justify-content:flex-end">
                <el-button size="small" @click="uploadStore.removeItem(item.id)">删除</el-button>
              </div>
            </div>
          </template>

          <el-empty v-if="uploadStore.totalCount === 0 && unfinishedTasks.length === 0" description="暂无上传任务" />
        </el-tab-pane>

        <!-- Tab 2: Completed (已完成) -->
        <el-tab-pane label="已完成" name="completed">
          <template v-if="recentUploads.length > 0">
            <el-table :data="recentUploads" style="width:100%" stripe>
              <el-table-column label="文件名" min-width="250">
                <template #default="{ row }">
                  <span class="file-name-text">{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column label="大小" width="100" align="right">
                <template #default="{ row }">
                  {{ formatSize(row.size) }}
                </template>
              </el-table-column>
              <el-table-column label="上传时间" width="170">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt || row.updatedAt) }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default>
                  <el-tag type="success" size="small">已完成</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="暂无上传记录" />
        </el-tab-pane>

        <!-- Tab 3: Downloads -->
        <el-tab-pane label="下载" name="download">
          <template v-if="recentDownloads.length > 0">
            <el-table :data="recentDownloads" style="width:100%" stripe>
              <el-table-column label="文件名" min-width="250">
                <template #default="{ row }">
                  <span class="file-name-text">{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column label="大小" width="100" align="right">
                <template #default="{ row }">
                  {{ formatSize(row.size) }}
                </template>
              </el-table-column>
              <el-table-column label="下载时间" width="170">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt || row.updatedAt) }}
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="暂无下载记录" />
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUploadStore } from '@/stores/upload'
import { recentItems, recentSaves, listUnfinishedTasks as listTasks, getCompletedChunks, deleteUploadTask, uploadPause, chunkedUpload, upload as uploadApi } from '@/api/file'
import { getUploadFileByTaskId, getAllUploadFiles, updateTaskId } from '@/utils/indexeddb'

const uploadStore = useUploadStore()
const activeTab = ref('uploading')

const recentUploads = ref([])
const recentDownloads = ref([])
const unfinishedTasks = ref([])

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function formatDateTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function loadUnfinishedTasks() {
  try {
    const res = await listTasks()
    const allTasks = res.data || []
    const activeTaskIds = uploadStore.queue
      .filter(i => i.taskId != null && (i.status === 'uploading' || i.status === 'pending'))
      .map(i => i.taskId)
    unfinishedTasks.value = allTasks.filter(t => !activeTaskIds.includes(t.taskId))
  } catch {
    unfinishedTasks.value = []
  }
}

async function loadUploadList() {
  try {
    const res = await recentItems(7)
    recentUploads.value = res.data || []
  } catch {
    recentUploads.value = []
  }
}

async function loadDownloadList() {
  try {
    const res = await recentSaves(7)
    recentDownloads.value = res.data || []
  } catch {
    recentDownloads.value = []
  }
}

async function resumeTask(task) {
  // 同会话内暂停：uploadStore.queue 里还保留着 File 对象
  const stored = uploadStore.queue.find(i => i.taskId === task.taskId && i.status === 'paused' && i.file)
  if (stored) {
    window.dispatchEvent(new CustomEvent('resume-started', { detail: { taskId: task.taskId } }))
    const chunksRes = await getCompletedChunks(task.taskId)
    stored.skipChunks = chunksRes.data || []
    stored.initialProgress = stored.skipChunks.length
    // 改回 pending 排到队尾
    stored.status = 'pending'
    // 去重：移到数组末尾
    const idx = uploadStore.queue.indexOf(stored)
    if (idx !== -1) {
      uploadStore.queue.splice(idx, 1)
      uploadStore.queue.push(stored)
    }
    // 重启 processQueue
    tryStartProcessing()
    return
  }

  // IndexedDB 回退：刷新后队列为空，从 IndexedDB 恢复
  try {
    const cached = await getUploadFileByTaskId(task.taskId)
    if (cached) {
      window.dispatchEvent(new CustomEvent('resume-started', { detail: { taskId: task.taskId } }))
      const item = uploadStore.addFiles([cached.file], task.parentId)[0]
      item.taskId = task.taskId
      const chunksRes = await getCompletedChunks(task.taskId)
      item.skipChunks = chunksRes.data || []
      item.initialProgress = item.skipChunks.length
      tryStartProcessing()
      return
    }
  } catch { /* fall through to file picker */ }

  // 跨会话 + 无 IndexedDB：弹文件选择框
  const input = document.createElement('input')
  input.type = 'file'
  input.style.display = 'none'
  input.onchange = async () => {
    const file = input.files[0]
    if (!file) return
    window.dispatchEvent(new CustomEvent('resume-started', { detail: { taskId: task.taskId } }))
    const item = uploadStore.addFiles([file])[0]
    try {
      const chunksRes = await getCompletedChunks(task.taskId)
      const skipChunks = chunksRes.data || []
      await chunkedUpload(file, task.parentId, (percent) => {
        const bytesDelta = Math.round(task.fileSize * (percent - item.progress) / 100)
        uploadStore.updateProgressBytes(item.id, percent, bytesDelta)
      }, task.fileName, task.taskId, skipChunks, (taskId) => { item.taskId = taskId })
      uploadStore.markDone(item.id)
      ElMessage.success('续传完成')
    } catch (err) {
      uploadStore.markFailed(item.id, err.message || '上传失败')
      ElMessage.error('上传失败，请重试')
    }
    document.body.removeChild(input)
  }
  document.body.appendChild(input)
  input.click()
}

async function tryStartProcessing() {
  if (!uploadStore.beginProcessing()) return
  whileProcess()
}

async function whileProcess() {
  let item
  while ((item = uploadStore.startNext())) {
    const file = item.file
    const parentId = item.parentId
    try {
      if (item.skipChunks?.length) {
        // 续传：保留已有分片
        item.progress = item.initialProgress || 0
        const ac = new AbortController()
        item.abortController = ac
        await chunkedUpload(file, parentId, (percent) => {
          const bytesDelta = Math.round((item.size || 0) * (percent - item.progress) / 100)
          uploadStore.updateProgressBytes(item.id, percent, bytesDelta)
        }, item.name, item.taskId, item.skipChunks, (tid) => { item.taskId = tid; updateTaskId(item.id, tid) }, ac.signal)
      } else {
        const CHUNK_THRESHOLD = 10 * 1024 * 1024
        if (file.size > CHUNK_THRESHOLD) {
          item.progress = 1
          let targetProgress = 1
          let stepTimer = null
          function startStepper() {
            if (stepTimer) return
            stepTimer = setInterval(() => {
              const gap = targetProgress - item.progress
              if (gap <= 0) { clearInterval(stepTimer); stepTimer = null; return }
              item.progress += Math.min(3, gap)
            }, 400)
          }
          const ac = new AbortController()
          item.abortController = ac
          try {
            await chunkedUpload(file, parentId, (percent) => {
              targetProgress = percent
              startStepper()
            }, undefined, undefined, undefined, (taskId) => {
              item.taskId = taskId
              updateTaskId(item.id, taskId)
            }, ac.signal)
          } finally {
            if (stepTimer) { clearInterval(stepTimer); stepTimer = null }
          }
        } else {
          // 小文件
          await uploadItem({ file, parentId: parentId, onProgress: (p) => uploadStore.updateProgress(item.id, p) })
        }
      }
      uploadStore.markDone(item.id)
    } catch (e) {
      if (e.message === 'paused') continue
      uploadStore.markFailed(item.id, e.message || '上传失败')
    }
  }
  uploadStore.endProcessing()
}

async function uploadItem({ file, parentId, onProgress }) {
  return uploadApi(file, parentId || 0, onProgress)
}

async function cancelPending(item) {
  uploadStore.removeItem(item.id)
}

async function deleteUnfinished(task) {
  try {
    await deleteUploadTask(task.taskId)
    ElMessage.success('上传记录已删除')
  } catch {
    ElMessage.error('删除上传记录失败')
  }
  unfinishedTasks.value = unfinishedTasks.value.filter(t => t.taskId !== task.taskId)
}

async function pauseItem(item) {
  try {
    if (item.abortController) item.abortController.abort()
    if (item.taskId) await uploadPause(item.taskId)
    item.status = 'paused'
    loadUnfinishedTasks()
    ElMessage.success('上传已暂停')
  } catch (e) {
    ElMessage.error('暂停失败，请重试')
  }
}

onMounted(async () => {
  // 1. 从 IndexedDB 恢复所有文件，按 createdAt 排序
  const existingIds = new Set(uploadStore.queue.map(i => i.id))
  let tasks = []
  try { const r = await listTasks(); tasks = r.data || [] } catch { /* ignore */ }
  try {
    const cached = await getAllUploadFiles()
    const taskMap = new Map(tasks.map(t => [t.taskId, t]))

    for (const c of cached) {
      if (existingIds.has(c.id)) continue // 去重

      let backendTask = c.taskId ? taskMap.get(c.taskId) : null

      // taskId 不匹配 → 按文件名+大小兜底匹配
      if (!backendTask) {
        const m = tasks.find(t => t.fileName === c.name && t.fileSize === c.size && t.status === 'uploading')
        if (m) { c.taskId = m.taskId; await updateTaskId(c.id, m.taskId); backendTask = m }
      }

      // 脏数据：taskId 不在后端列表中 → 跳过
      if (c.taskId && !backendTask) continue

      if (backendTask && backendTask.status === 'paused') {
        // 暂停的任务不加入队列，由 loadUnfinishedTasks 显示
        continue
      }

      const item = uploadStore.addFiles([c.file], c.parentId, true)[0]
      item.id = c.id
      item.taskId = c.taskId
      if (backendTask) {
        const chunksRes = await getCompletedChunks(c.taskId)
        item.skipChunks = chunksRes.data || []
        item.initialProgress = item.skipChunks.length
        item.progress = item.initialProgress
      }
    }
  } catch { /* ignore */ }

  tryStartProcessing()

  // 2. 加载列表
  loadUnfinishedTasks()
  loadUploadList()
  loadDownloadList()
  window.addEventListener('resume-started', onResumeStarted)
  uploadStore.startCleanup()
})

onUnmounted(() => {
  window.removeEventListener('resume-started', onResumeStarted)
  uploadStore.stopCleanup()
})

function onResumeStarted(e) {
  const taskId = e.detail?.taskId
  if (taskId) {
    unfinishedTasks.value = unfinishedTasks.value.filter(t => t.taskId !== taskId)
  }
}
</script>

<style scoped>
.transfer-page { min-height: 400px; }
.transfer-card { border-radius: 8px; min-height: 300px; }
.upload-summary { font-size: 13px; color: #409eff; margin-bottom: 12px; }
.upload-item { padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.upload-item:last-child { border-bottom: none; }
.upload-item-info { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.upload-item-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.upload-item-name.folder-icon { font-weight: 600; }
.upload-item-speed { font-size: 12px; color: #67c23a; margin: 0 8px; flex-shrink: 0; font-weight: 500; }
.upload-item-size { font-size: 12px; color: #909399; margin-left: 8px; flex-shrink: 0; }
.upload-item-failed { background: #fef0f0; border-radius: 4px; padding: 8px; margin-top: 4px; }
.upload-item-error { font-size: 12px; color: #f56c6c; margin-top: 4px; }
.failed-files-list { font-size: 11px; color: #909399; margin-top: 4px; padding-left: 12px; max-height: 120px; overflow-y: auto; }
.failed-file-item { line-height: 1.6; }
.resume-item { background: #ecf5ff; border-radius: 4px; padding: 8px; margin-bottom: 4px; }
.section-label { font-size: 13px; color: #f56c6c; margin-bottom: 8px; font-weight: 600; }
.file-name-text { font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ─── 进度条动画 ─── */
:deep(.el-progress-bar__inner) { transition: width 0.4s ease !important; }
:deep(.progress-active .el-progress-bar__inner) {
  background-image: linear-gradient(90deg, transparent 0%, transparent 35%, rgba(255,255,255,0.75) 50%, transparent 65%, transparent 100%) !important;
  background-size: 300% 100% !important;
  background-repeat: no-repeat !important;
  animation: sweepFlash 2s ease-in-out infinite !important;
}
@keyframes sweepFlash {
  0%   { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
</style>
