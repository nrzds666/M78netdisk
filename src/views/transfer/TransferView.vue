<template>
  <div class="transfer-page">
    <el-card shadow="never" class="transfer-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="上传中" name="uploading">
          <template v-if="uploadStore.queue.length > 0">
            <!-- toolbar -->
            <div class="toolbar">
              <span class="toolbar-hint">
                正在上传 {{ uploadingCount }} 个，等待 {{ pendingCount }} 个
                <template v-if="pausedCount">，已暂停 {{ pausedCount }} 个</template>
              </span>
              <span style="flex:1"></span>
              <el-button size="small" :disabled="uploadingCount === 0" @click="batchPause">全部暂停</el-button>
              <el-button size="small" type="success" plain :disabled="pausedCount === 0" @click="batchResume">全部继续</el-button>
              <el-button size="small" type="danger" plain :disabled="uploadStore.queue.length === 0" @click="batchCancel">全部取消</el-button>
            </div>

            <el-table :data="uploadStore.queue" stripe row-key="id">
              <el-table-column label="文件名" min-width="280" show-overflow-tooltip>
                <template #default="{ row }">
                  <div class="name-cell" @mouseenter="hoverRowId = row.id" @mouseleave="hoverRowId = null">
                    <span class="item-name" :class="{ 'is-folder': row.isFolder }">
                      {{ row.isFolder ? '📁 ' : '' }}{{ row.name }}
                    </span>
                    <span class="hover-actions" v-show="hoverRowId === row.id && row.status !== 'done'" @click.stop>
                      <template v-if="row.status === 'uploading'">
                        <el-button link size="small" @click="pauseItem(row)">⏸暂停</el-button>
                        <el-button link size="small" type="danger" @click="cancelItem(row)">✕取消</el-button>
                      </template>
                      <template v-else-if="row.status === 'paused'">
                        <el-button link size="small" @click="resumeItem(row)">▶继续</el-button>
                        <el-button link size="small" type="danger" @click="cancelItem(row)">✕取消</el-button>
                      </template>
                      <template v-else-if="row.status === 'pending'">
                        <el-button link size="small" type="danger" @click="cancelItem(row)">✕取消</el-button>
                      </template>
                      <template v-else-if="row.status === 'failed'">
                        <el-button link size="small" @click="uploadStore.removeItem(row.id)">删除</el-button>
                      </template>
                    </span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="大小" width="180" align="right">
                <template #default="{ row }">
                  <template v-if="row.isFolder">
                    {{ row.completedFiles || 0 }}/{{ row.fileCount || 0 }}项 · {{ formatSize(row.size) }}
                  </template>
                  <template v-else>
                    {{ formatUploadedSize(row) }}/{{ formatSize(row.size) }}
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="260">
                <template #default="{ row }">
                  <template v-if="row.status === 'uploading'">
                    <div class="progress-label">{{ row.displaySpeed || row.speed || '' }}</div>
                    <el-progress :percentage="Math.round(row.displayProgress != null ? row.displayProgress : row.progress)" :stroke-width="6" :show-text="false" />
                    <div class="progress-pct">{{ Math.round(row.displayProgress != null ? row.displayProgress : row.progress) }}%</div>
                  </template>
                  <template v-else-if="row.status === 'paused'">
                    <div class="progress-label">已暂停</div>
                    <el-progress :percentage="Math.round(row.displayProgress != null ? row.displayProgress : row.progress)" :stroke-width="6" :show-text="false" color="#e6a23c" />
                    <div class="progress-pct">{{ Math.round(row.displayProgress != null ? row.displayProgress : row.progress) }}%</div>
                  </template>
                  <template v-else-if="row.status === 'pending'">
                    <div class="progress-label">等待中</div>
                    <el-progress :percentage="Math.round(row.displayProgress != null ? row.displayProgress : row.progress)" :stroke-width="6" :show-text="false" color="#c0c4cc" />
                    <div class="progress-pct">{{ Math.round(row.displayProgress != null ? row.displayProgress : row.progress) }}%</div>
                  </template>
                  <template v-else-if="row.status === 'failed'">
                    <div class="progress-label" style="color:#f56c6c">{{ row.error || '失败' }}</div>
                    <el-progress :percentage="Math.round(row.displayProgress != null ? row.displayProgress : row.progress)" :stroke-width="6" :show-text="false" color="#f56c6c" />
                    <div class="progress-pct">{{ Math.round(row.displayProgress != null ? row.displayProgress : row.progress) }}%</div>
                  </template>
                  <template v-else-if="row.status === 'done'">
                    <div class="progress-label" style="color:#67c23a">已完成</div>
                    <el-progress :percentage="100" :stroke-width="6" :show-text="false" color="#67c23a" />
                    <div class="progress-pct">100%</div>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="暂无上传任务" />
        </el-tab-pane>

        <el-tab-pane label="已完成" name="completed">
          <template v-if="recentUploads.length > 0">
            <el-table :data="recentUploads" stripe>
              <el-table-column label="文件名" min-width="250" show-overflow-tooltip>
                <template #default="{ row }">{{ row.name }}</template>
              </el-table-column>
              <el-table-column label="大小" width="100" align="right">
                <template #default="{ row }">{{ formatSize(row.size) }}</template>
              </el-table-column>
              <el-table-column label="上传时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt || row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default><el-tag type="success" size="small">已完成</el-tag></template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="暂无上传记录" />
        </el-tab-pane>

        <el-tab-pane label="下载" name="download">
          <template v-if="recentDownloads.length > 0">
            <el-table :data="recentDownloads" stripe>
              <el-table-column label="文件名" min-width="250" show-overflow-tooltip>
                <template #default="{ row }">{{ row.name }}</template>
              </el-table-column>
              <el-table-column label="大小" width="100" align="right">
                <template #default="{ row }">{{ formatSize(row.size) }}</template>
              </el-table-column>
              <el-table-column label="下载时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt || row.updatedAt) }}</template>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUploadStore } from '@/stores/upload'
import { recentItems, recentSaves, getCompletedChunks, uploadPause, uploadCancel, chunkedUpload, estimateTotalChunks, upload as uploadApi } from '@/api/file'
import { getAllUploadFiles, updateTaskId } from '@/utils/indexeddb'

const uploadStore = useUploadStore()
const activeTab = ref('uploading')
const hoverRowId = ref(null)
const recentUploads = ref([])
const recentDownloads = ref([])

const uploadingCount = computed(() => uploadStore.uploadingItems.length)
const pendingCount = computed(() => uploadStore.pendingItems.length)
const pausedCount = computed(() => uploadStore.queue.filter(i => i.status === 'paused').length)

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function formatUploadedSize(item) {
  if (!item.size) return '0'
  const pct = (item.displayProgress != null ? item.displayProgress : item.progress) / 100
  return formatSize(Math.round(item.size * pct))
}

function formatDateTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const pad = n => String(n).padStart(2, '0')
  return d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
}

async function pauseItem(item) {
  // Step 1: 本地中止 HTTP 请求（同步，不会失败）
  if (item.abortController) item.abortController.abort()

  // Step 2: 通知后端（best-effort，失败不影响本地暂停）
  let backendPaused = false
  if (item.taskId) {
    try {
      await uploadPause(item.taskId)
      backendPaused = true
    } catch {
      // 后端通知失败，但本地已中止，仍然标记暂停
      console.warn('后端暂停通知失败: taskId=' + item.taskId)
    }
  }

  // Step 3: 无论后端是否成功，本地状态必须更新
  item.status = 'paused'
  item.displaySpeed = ''
  uploadStore.stopSmoothTimers(item.id)
  ElMessage.success(backendPaused ? '已暂停' : '已暂停（后台同步中）')
}

async function resumeItem(item) {
  // 恢复前先从服务器拉取已完成的分片列表，以便断点续传复用已有 taskId
  if (item.taskId) {
    try {
      const cr = await getCompletedChunks(item.taskId)
      item.skipChunks = cr.data || []
      // 将已上传分片个数转为百分比（需用与 chunkedUpload 相同的 totalChunks 估算公式）
      const count = item.skipChunks.length
      const estTotal = estimateTotalChunks(item.size)
      item.initialProgress = count > 0 ? Math.round((count / estTotal) * 100) : 0
      item.progress = item.initialProgress
    } catch {
      // 查询失败（任务已过期/被取消）：清空旧引用，降级为新建上传任务
      item.taskId = null
      item.skipChunks = []
      item.initialProgress = 0
    }
  }
  item.status = 'pending'
  item.uploadPhase = 'preparing'
  item.displayProgress = item.progress
  tryStartProcessing()
}

async function cancelItem(item) {
  try { await ElMessageBox.confirm('确定取消 ' + item.name + '？', '确认取消', { type: 'warning' }) } catch { return }
  // 先中止正在进行的 HTTP 请求，避免后续分片继续发送浪费带宽
  if (item.abortController) item.abortController.abort()
  // 通知后端清理已上传的分片和任务记录
  if (item.taskId) {
    uploadCancel(item.taskId).catch(() => { /* 后端清理失败不阻塞前端删除 */ })
  }
  uploadStore.removeItem(item.id)
}

async function batchPause() {
  // 显式复制避免迭代过程中响应式数组变化导致遗漏
  const items = [...uploadStore.uploadingItems]
  for (const item of items) await pauseItem(item)
}

async function batchResume() {
  const pausedItems = uploadStore.queue.filter(i => i.status === 'paused')
  for (const item of pausedItems) await resumeItem(item)
  if (pausedItems.length > 0) {
    ElMessage.success(`已恢复 ${pausedItems.length} 个上传任务`)
  }
}

async function batchCancel() {
  try { await ElMessageBox.confirm('确定取消所有上传任务？', '确认批量取消', { type: 'warning' }) } catch { return }
  for (const item of [...uploadStore.queue]) {
    // 先中止所有正在进行的 HTTP 请求
    if (item.abortController) item.abortController.abort()
    // 通知后端清理已上传的分片和任务记录
    if (item.taskId) {
      uploadCancel(item.taskId).catch(() => { /* 后端清理失败不阻塞前端删除 */ })
    }
    uploadStore.removeItem(item.id)
  }
}

async function loadUploadList() {
  try { const res = await recentItems(7); recentUploads.value = res.data || [] } catch { recentUploads.value = [] }
}

async function loadDownloadList() {
  try { const res = await recentSaves(7); recentDownloads.value = res.data || [] } catch { recentDownloads.value = [] }
}

function tryStartProcessing() {
  if (!uploadStore.beginProcessing()) return
  whileProcess()
}

async function whileProcess() {
  let item
  while ((item = uploadStore.startNext())) {
    const f = item.file; const p = item.parentId
    try {
      if (item.skipChunks?.length) {
        item.progress = item.initialProgress || 0; item.displayProgress = item.initialProgress || 0; item.uploadPhase = 'preparing'
        const ac = new AbortController(); item.abortController = ac
        await chunkedUpload(f, p, (pct) => uploadStore.updateProgressBytes(item.id, pct, Math.round((item.size||0)*(pct-item.progress)/100)),
          item.name, item.taskId, item.skipChunks, (tid) => { item.taskId=tid; updateTaskId(item.id,tid) }, ac.signal,
          ({ batchDuration, batchBytes, totalPercent }) => uploadStore.startSmoothProgress(item.id, batchDuration, totalPercent, batchDuration>0?batchBytes/(batchDuration/1000):0))
        uploadStore.setMergingPhase(item.id); uploadStore.completeMerge(item.id)
      } else if (f.size > 10*1024*1024) {
        item.progress = 1; item.displayProgress = 0; item.uploadPhase = 'preparing'
        const ac = new AbortController(); item.abortController = ac
        await chunkedUpload(f, p, (pct) => { item.progress=pct }, undefined, undefined, undefined, (tid) => { item.taskId=tid; updateTaskId(item.id,tid) }, ac.signal,
          ({ batchDuration, batchBytes, totalPercent }) => uploadStore.startSmoothProgress(item.id, batchDuration, totalPercent, batchDuration>0?batchBytes/(batchDuration/1000):0))
        uploadStore.setMergingPhase(item.id); uploadStore.completeMerge(item.id)
      } else {
        const ac = new AbortController(); item.abortController = ac
        await uploadApi(f, p||0, (pct) => uploadStore.updateProgress(item.id, pct), undefined, undefined, ac.signal)
      }
      uploadStore.markDone(item.id)
    } catch (e) {
      if (e.message === 'paused') continue
      uploadStore.markFailed(item.id, e.message || '上传失败')
    }
  }
  uploadStore.endProcessing()
}

onMounted(async () => {
  const existingIds = new Set(uploadStore.queue.map(i => i.id))
  try {
    const cached = await getAllUploadFiles()
    for (const c of cached) {
      if (existingIds.has(c.id)) continue
      const item = uploadStore.addFiles([c.file], c.parentId, true)[0]
      item.id = c.id; item.taskId = c.taskId
      if (c.taskId) {
        try {
          const cr = await getCompletedChunks(c.taskId)
          item.skipChunks = cr.data || []
          // 将已上传分片个数转为百分比，与 resumeItem 保持一致
          const count = item.skipChunks.length
          const estTotal = estimateTotalChunks(item.size)
          const pct = count > 0 ? Math.round((count / estTotal) * 100) : 0
          item.initialProgress = pct; item.progress = pct; item.displayProgress = pct
        } catch {
          // 查询失败（任务已过期/被取消）：清空 taskId，下次恢复时走新建上传
          item.taskId = null
          item.skipChunks = []
          item.initialProgress = 0
          item.progress = 0
          item.displayProgress = 0
        }
      }
      item.status = 'paused'; item.uploadPhase = 'preparing'
    }
  } catch { /* ignore */ }
  tryStartProcessing()
  loadUploadList()
  loadDownloadList()
  uploadStore.startCleanup()
})

onUnmounted(() => { uploadStore.stopCleanup() })
</script>

<style scoped>
.transfer-page { min-height: 400px; }
.transfer-card { border-radius: 8px; min-height: 300px; }
.toolbar { display: flex; align-items: center; margin-bottom: 10px; gap: 8px; }
.toolbar-hint { font-size: 13px; color: #909399; }
.name-cell { display: flex; align-items: center; }
.item-name { font-size: 13px; }
.item-name.is-folder { font-weight: 600; }
.hover-actions { margin-left: 10px; white-space: nowrap; display: inline-flex; gap: 2px; }
.progress-label { font-size: 12px; color: #606266; margin-bottom: 2px; min-height: 18px; }
.progress-pct { font-size: 11px; color: #909399; margin-top: 1px; }
</style>
