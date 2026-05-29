<template>
  <div class="file-page">
    <!-- Filters -->
    <el-card shadow="never" class="filter-bar">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input v-model="searchQuery" placeholder="搜索文件名..." clearable :prefix-icon="Search" @input="onSearch" />
        </el-col>
        <el-col :span="5">
          <el-select v-model="typeFilter" placeholder="文件类型" clearable style="width:100%" @change="loadFiles">
            <el-option label="全部" value="" />
            <el-option label="图片" value="image" />
            <el-option label="视频" value="video" />
            <el-option label="音频" value="audio" />
            <el-option label="文档" value="document" />
            <el-option label="压缩包" value="archive" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-col>
        <el-col :span="5">
          <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width:100%" @change="loadFiles" />
        </el-col>
        <el-col :span="4" style="text-align:right">
          <el-button-group>
            <el-button :type="viewMode === 'list' ? 'primary' : ''" :icon="List" @click="viewMode = 'list'" />
            <el-button :type="viewMode === 'grid' ? 'primary' : ''" :icon="Grid" @click="viewMode = 'grid'" />
          </el-button-group>
        </el-col>
        <el-col :span="4" style="text-align:right">
          <el-button type="danger" :disabled="selectedIds.length === 0" @click="handleBatchDelete" size="default">
            删除选中 ({{ selectedIds.length }})
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- File Table -->
    <el-card shadow="never" class="file-table-card" v-loading="loading">
      <el-table
        :data="files"
        style="width:100%"
        @row-dblclick="handleRowDoubleClick"
        @selection-change="handleSelectionChange"
        :highlight-current-row="false"
        stripe
      >
        <el-table-column type="selection" width="40" />
        <el-table-column label="文件名" min-width="300">
          <template #default="{ row }">
            <div class="file-name-cell" @mouseenter="row._hover = true" @mouseleave="row._hover = false">
              <el-icon :size="22" :color="row.isDirectory ? '#e6a23c' : getFileIconColor(row.mimeType)" style="margin-right:8px;flex-shrink:0">
                <Folder v-if="row.isDirectory" />
                <Picture v-else-if="row.mimeType?.startsWith('image')" />
                <VideoCamera v-else-if="row.mimeType?.startsWith('video')" />
                <Headset v-else-if="row.mimeType?.startsWith('audio')" />
                <Reading v-else-if="row.mimeType?.includes('pdf') || row.mimeType?.includes('document')" />
                <Document v-else />
              </el-icon>
              <span class="file-name-text">{{ row.name }}</span>
              <!-- Hover actions -->
              <span class="hover-actions" v-if="row._hover && !row.isDirectory">
                <el-button text size="small" :icon="Download" @click.stop="handleDownload(row)" />
                <el-button text size="small" :icon="Delete" type="danger" @click.stop="handleDelete(row)" />
                <el-button text size="small" :icon="Share" @click.stop="handleShare(row)" />
              </span>
              <span class="hover-actions" v-if="row._hover && row.isDirectory">
                <el-button text size="small" :icon="Delete" type="danger" @click.stop="handleDelete(row)" />
                <el-button text size="small" :icon="Share" @click.stop="handleShare(row)" />
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100" align="right">
          <template #default="{ row }">
            <span v-if="!row.isDirectory">{{ formatSize(row.size) }}</span>
            <span v-else style="color:#909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ row.isDirectory ? '文件夹' : getFileType(row.mimeType) }}
          </template>
        </el-table-column>
        <el-table-column label="修改时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadFiles"
          small
        />
      </div>
    </el-card>

    <!-- Floating Action Button -->
    <div class="fab-container">
      <el-popover placement="top-end" :visible="showMenu" :width="180" trigger="click">
        <template #reference>
          <el-button type="primary" circle :size="fabExpanded ? 'default' : 'large'" class="fab-btn" @click="showMenu = !showMenu">
            <el-icon :size="22"><Plus /></el-icon>
          </el-button>
        </template>
        <div class="fab-menu">
          <el-button class="fab-menu-item" @click="uploadFile">
            <el-icon><Upload /></el-icon> 上传文件
          </el-button>
          <el-button class="fab-menu-item" @click="uploadFolder">
            <el-icon><FolderAdd /></el-icon> 上传文件夹
          </el-button>
          <el-button class="fab-menu-item" @click="createFolder">
            <el-icon><FolderOpened /></el-icon> 新建文件夹
          </el-button>
        </div>
      </el-popover>
    </div>

    <!-- Upload Dialog -->
    <el-dialog v-model="uploadDialogVisible" title="上传文件" width="400px">
      <el-upload
        drag
        :auto-upload="false"
        :multiple="true"
        ref="uploadRef"
        :show-file-list="true"
      >
        <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmUpload">开始上传</el-button>
      </template>
    </el-dialog>

    <!-- Create Folder Dialog -->
    <el-dialog v-model="folderDialogVisible" title="新建文件夹" width="380px">
      <el-form>
        <el-form-item label="文件夹名称">
          <el-input v-model="newFolderName" placeholder="请输入名称" @keyup.enter="confirmCreateFolder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreateFolder">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useFileStore } from '@/stores/file'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, List, Grid, Folder, FolderOpened, FolderAdd,
  Document, Picture, VideoCamera, Headset, Reading,
  Download, Delete, Share, Plus, UploadFilled
} from '@element-plus/icons-vue'
import { listItems, moveToTrash, upload as uploadApi } from '@/api/file'

const router = useRouter()
const route = useRoute()
const fileStore = useFileStore()

const files = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const searchQuery = ref('')
const typeFilter = ref('')
const dateRange = ref(null)
const viewMode = ref('list')
const selectedIds = ref([])
const showMenu = ref(false)
const fabExpanded = ref(false)
const uploadDialogVisible = ref(false)
const uploadRef = ref(null)
const folderDialogVisible = ref(false)
const newFolderName = ref('')

const currentFolderId = computed(() => fileStore.currentFolderId)

function getFileIconColor(mimeType) {
  if (!mimeType) return '#909399'
  if (mimeType.startsWith('image')) return '#67c23a'
  if (mimeType.startsWith('video')) return '#e6a23c'
  if (mimeType.startsWith('audio')) return '#409eff'
  if (mimeType.includes('pdf')) return '#f56c6c'
  return '#909399'
}

function getFileType(mimeType) {
  if (!mimeType) return '未知'
  if (mimeType.startsWith('image')) return '图片'
  if (mimeType.startsWith('video')) return '视频'
  if (mimeType.startsWith('audio')) return '音频'
  if (mimeType.includes('pdf')) return 'PDF'
  if (mimeType.includes('zip') || mimeType.includes('rar') || mimeType.includes('7z') || mimeType.includes('tar')) return '压缩包'
  if (mimeType.includes('word') || mimeType.includes('document')) return '文档'
  if (mimeType.includes('sheet') || mimeType.includes('excel')) return '表格'
  return mimeType.split('/').pop() || '未知'
}

function formatSize(bytes) {
  if (!bytes) return '-'
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

function handleRowDoubleClick(row) {
  if (row.isDirectory) {
    fileStore.enterFolder(row.id, row.name)
    router.push(`/files/folder/${row.id}`)
    loadFiles()
  } else {
    window.open(`/api/files/preview/${row.id}`, '_blank')
  }
}

function handleDownload(row) {
  window.open(`/api/files/download/${row.id}`, '_blank')
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定将「${row.name}」删除到回收站？`, '提示')
    await moveToTrash([row.id])
    ElMessage.success('已删除到回收站')
    loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function handleShare(row) {
  ElMessage.info('分享功能开发中')
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await ElMessageBox.confirm(`确定将选中的 ${selectedIds.value.length} 个文件删除到回收站？`, '提示')
    await moveToTrash(selectedIds.value)
    ElMessage.success('已删除到回收站')
    loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

function onSearch() {
  page.value = 1
  loadFiles()
}

async function loadFiles() {
  loading.value = true
  try {
    const res = await listItems(currentFolderId.value, page.value, pageSize.value)
    files.value = (res.data?.records || []).map(f => ({ ...f, _hover: false }))
    total.value = res.data?.total || 0
  } catch (e) {
    files.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function uploadFile() {
  showMenu.value = false
  uploadDialogVisible.value = true
}

function uploadFolder() {
  showMenu.value = false
  ElMessage.info('上传文件夹功能开发中')
}

function createFolder() {
  showMenu.value = false
  newFolderName.value = ''
  folderDialogVisible.value = true
}

function confirmCreateFolder() {
  if (!newFolderName.value) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  ElMessage.success('文件夹创建成功（接口待对接）')
  folderDialogVisible.value = false
  loadFiles()
}

function confirmUpload() {
  ElMessage.success('上传任务已添加（接口待对接）')
  uploadDialogVisible.value = false
}

onMounted(loadFiles)
</script>

<style scoped>
.file-page {
  position: relative;
  min-height: 400px;
}

.filter-bar {
  margin-bottom: 16px;
  border-radius: 8px;
}

.file-table-card {
  border-radius: 8px;
  min-height: 300px;
}

.file-name-cell {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.file-name-text {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hover-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
  margin-left: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}

.fab-container {
  position: fixed;
  bottom: 32px;
  right: 32px;
  z-index: 100;
}

.fab-btn {
  width: 56px;
  height: 56px;
  font-size: 24px;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
}

.fab-menu {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fab-menu-item {
  justify-content: flex-start;
  width: 100%;
}
</style>
