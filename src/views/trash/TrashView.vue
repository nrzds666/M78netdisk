<template>
  <div class="trash-page">
    <!-- Batch Actions Bar -->
    <el-card shadow="never" class="action-bar">
      <el-row :gutter="16" align="middle">
        <el-col :span="12">
          <span class="action-title">回收站</span>
          <span class="action-subtitle" v-if="total > 0">（共 {{ total }} 个文件）</span>
        </el-col>
        <el-col :span="12" style="text-align:right">
          <el-button
            type="primary"
            :disabled="selectedIds.length === 0"
            @click="handleBatchRestore"
            size="default"
          >
            还原选中 ({{ selectedIds.length }})
          </el-button>
          <el-button
            type="danger"
            :disabled="selectedIds.length === 0"
            @click="handleBatchPermanentDelete"
            size="default"
          >
            彻底删除选中 ({{ selectedIds.length }})
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- Trash Table -->
    <el-card shadow="never" class="trash-table-card" v-loading="loading">
      <el-empty v-if="!loading && files.length === 0" description="回收站暂无文件" />
      <template v-else>
        <el-table
          :data="files"
          style="width:100%"
          @selection-change="handleSelectionChange"
          stripe
        >
          <el-table-column type="selection" width="40" />
          <el-table-column label="文件名" min-width="300">
            <template #default="{ row }">
              <div class="file-name-cell">
                <el-icon :size="22" :color="getFileIconColor(row.mimeType)" style="margin-right:8px;flex-shrink:0">
                  <Folder v-if="row.isDirectory" />
                  <Picture v-else-if="row.mimeType?.startsWith('image')" />
                  <VideoCamera v-else-if="row.mimeType?.startsWith('video')" />
                  <Headset v-else-if="row.mimeType?.startsWith('audio')" />
                  <Reading v-else-if="row.mimeType?.includes('pdf') || row.mimeType?.includes('document')" />
                  <Document v-else />
                </el-icon>
                <span class="file-name-text">{{ row.name }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="100" align="right">
            <template #default="{ row }">
              <span v-if="!row.isDirectory">{{ formatSize(row.size) }}</span>
              <span v-else style="color:#909399">-</span>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.deletedAt || row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="handleRestore(row)">
                还原
              </el-button>
              <el-button text type="danger" size="small" @click="handlePermanentDelete(row)">
                彻底删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap" v-if="total > pageSize">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="loadTrash"
            small
          />
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Folder, Document, Picture, VideoCamera, Headset, Reading
} from '@element-plus/icons-vue'
import { listTrash, restore, permanentDelete } from '@/api/file'

const files = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedIds = ref([])

function getFileIconColor(mimeType) {
  if (!mimeType) return '#909399'
  if (mimeType.startsWith('image')) return '#67c23a'
  if (mimeType.startsWith('video')) return '#e6a23c'
  if (mimeType.startsWith('audio')) return '#409eff'
  if (mimeType.includes('pdf')) return '#f56c6c'
  return '#909399'
}

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

function handleSelectionChange(selection) {
  selectedIds.value = selection.map(s => s.id)
}

async function loadTrash() {
  loading.value = true
  try {
    const res = await listTrash(page.value, pageSize.value)
    files.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    files.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function handleRestore(row) {
  try {
    await restore([row.id])
    ElMessage.success(`已还原「${row.name}」`)
    loadTrash()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '还原失败')
  }
}

async function handlePermanentDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要彻底删除「${row.name}」？删除后无法恢复。`,
      '警告',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await permanentDelete([row.id])
    ElMessage.success(`已彻底删除「${row.name}」`)
    loadTrash()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.msg || e.message || '删除失败')
    }
  }
}

async function handleBatchRestore() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定要还原选中的 ${selectedIds.value.length} 个文件？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    await restore(selectedIds.value)
    ElMessage.success(`已还原 ${selectedIds.value.length} 个文件`)
    selectedIds.value = []
    loadTrash()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.msg || e.message || '还原失败')
    }
  }
}

async function handleBatchPermanentDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(
      `确定要彻底删除选中的 ${selectedIds.value.length} 个文件？删除后无法恢复。`,
      '警告',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await permanentDelete(selectedIds.value)
    ElMessage.success(`已彻底删除 ${selectedIds.value.length} 个文件`)
    selectedIds.value = []
    loadTrash()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.msg || e.message || '删除失败')
    }
  }
}

onMounted(loadTrash)
</script>

<style scoped>
.trash-page {
  min-height: 400px;
}

.action-bar {
  margin-bottom: 16px;
  border-radius: 8px;
}

.action-title {
  font-size: 16px;
  font-weight: 600;
}

.action-subtitle {
  font-size: 14px;
  color: #909399;
  margin-left: 8px;
}

.trash-table-card {
  border-radius: 8px;
  min-height: 300px;
}

.file-name-cell {
  display: flex;
  align-items: center;
}

.file-name-text {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}
</style>
