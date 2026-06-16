<template>
  <div class="file-page">
    <!-- Filters -->
    <el-card shadow="never" class="filter-bar">
      <el-row :gutter="16" align="middle">
        <el-col :span="1" v-if="currentFolderId">
          <el-button :icon="ArrowLeft" text @click="goToParent" title="返回上一层" />
        </el-col>
        <el-col :span="currentFolderId ? 5 : 6">
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

    <!-- File Table / Grid -->
    <el-card shadow="never" class="file-table-card" v-loading="loading">
      <!-- Table View -->
      <template v-if="viewMode === 'list'">
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
      </template>

      <!-- Grid View -->
      <template v-else>
        <div class="grid-container" v-if="files.length > 0">
          <div
            v-for="item in files"
            :key="item.id"
            class="grid-item"
            :class="{ 'grid-item-selected': selectedIds.includes(item.id) }"
            @click="toggleGridSelection(item)"
            @dblclick="handleRowDoubleClick(item)"
          >
            <div class="grid-icon">
              <el-icon :size="40" :color="item.isDirectory ? '#e6a23c' : getFileIconColor(item.mimeType)">
                <Folder v-if="item.isDirectory" />
                <Picture v-else-if="item.mimeType?.startsWith('image')" />
                <VideoCamera v-else-if="item.mimeType?.startsWith('video')" />
                <Headset v-else-if="item.mimeType?.startsWith('audio')" />
                <Document v-else />
              </el-icon>
            </div>
            <div class="grid-name" :title="item.name">{{ item.name }}</div>
            <div class="grid-info">{{ item.isDirectory ? '文件夹' : formatSize(item.size) }}</div>
          </div>
        </div>
        <el-empty v-else description="暂无文件" />
      </template>

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
      <el-popover placement="top-end" :visible="showMenu" :width="220" trigger="click">
        <template #reference>
          <el-button type="primary" circle :size="fabExpanded ? 'default' : 'large'" class="fab-btn" @click="showMenu = !showMenu">
            <el-icon :size="22"><Plus /></el-icon>
          </el-button>
        </template>
        <div class="fab-menu">
          <el-button class="fab-menu-item" @click="uploadFile">
            <img :src="uploadIcon" class="fab-custom-icon" /> 上传文件
          </el-button>
          <el-button class="fab-menu-item" @click="uploadFolder">
            <template #icon><el-icon><FolderAdd /></el-icon></template>
            上传文件夹
          </el-button>
          <el-button class="fab-menu-item" @click="openCreateFolderDialog">
            <template #icon><el-icon><FolderOpened /></el-icon></template>
            新建文件夹
          </el-button>
        </div>
      </el-popover>
    </div>

    <!-- Upload Dialog -->
    <el-dialog v-model="uploadDialogVisible" title="上传文件" width="400px" @close="cancelUpload">
      <el-upload
        drag
        :auto-upload="false"
        :multiple="true"
        ref="uploadRef"
        :show-file-list="true"
        @change="handleFileSelect"
        @remove="handleFileRemove"
      >
        <el-icon class="el-icon--upload" :size="48"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
      </el-upload>
      <template #footer>
        <el-button @click="cancelUpload">取消</el-button>
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

    <!-- Preview Dialog -->
    <el-dialog
      v-model="showPreviewDialog"
      :title="previewItem?.name || '预览'"
      width="80%"
      :close-on-click-modal="false"
      top="5vh"
      destroy-on-close
    >
      <div v-loading="previewLoading" class="preview-body">
        <!-- Image -->
        <div v-if="previewType === 'image'" class="preview-center">
          <el-image :src="previewUrl" fit="contain" style="max-width:100%;max-height:70vh" :preview-src-list="[previewUrl]" />
        </div>

        <!-- PDF / Office (converted to PDF by backend) -->
        <iframe v-else-if="previewType === 'pdf' || previewType === 'office'"
          :src="previewUrl" style="width:100%;height:70vh;border:none;border-radius:4px" />

        <!-- Video -->
        <div v-else-if="previewType === 'video'" class="preview-center">
          <video :src="previewUrl" controls style="max-width:100%;max-height:70vh" />
        </div>

        <!-- Audio -->
        <div v-else-if="previewType === 'audio'" class="preview-center">
          <audio :src="previewUrl" controls style="width:80%" />
        </div>

        <!-- Other / fallback -->
        <el-empty v-else description="该文件类型不支持在线预览">
          <template #extra>
            <el-button type="primary" @click="handleDownload(previewItem)">下载文件</el-button>
          </template>
        </el-empty>
      </div>
      <template #footer>
        <el-button type="primary" @click="handleDownload(previewItem)" :disabled="!previewItem">下载</el-button>
        <el-button @click="showPreviewDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Create Share Dialog -->
    <el-dialog
      v-model="showShareDialog"
      :title="'分享: ' + (shareTarget?.name || '')"
      width="420px"
      :close-on-click-modal="false"
      @close="resetShareDialog"
    >
      <template v-if="!shareResult">
        <el-form label-width="90px">
          <el-form-item label="提取码">
            <el-input
              v-model="shareForm.password"
              placeholder="可选，留空则无需提取码"
              maxlength="32"
              show-password
            />
          </el-form-item>
          <el-form-item label="过期时间">
            <el-select v-model="shareForm.expireType" style="width:100%">
              <el-option label="一天" value="ONE_DAY" />
              <el-option label="七天" value="ONE_WEEK" />
              <el-option label="一个月" value="ONE_MONTH" />
              <el-option label="永久" value="PERMANENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="权限">
            <el-select v-model="shareForm.permission" style="width:100%">
              <el-option label="仅查看" value="view" />
              <el-option label="可下载" value="download" />
              <el-option label="可编辑" value="edit" />
            </el-select>
          </el-form-item>
          <el-form-item label="下载次数上限">
            <el-input-number
              v-model="shareForm.maxDownloads"
              :min="1"
              :max="99999"
              :step="10"
              placeholder="不限"
              style="width:100%"
              :clearable="true"
            />
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <div class="share-result">
          <el-alert type="success" :closable="false" show-icon>
            <template #title>分享创建成功</template>
          </el-alert>
          <div class="share-result-field">
            <label>分享链接：</label>
            <div class="share-result-value">
              <span class="share-result-text">{{ shareUrl }}</span>
              <el-button text size="small" @click="copyText(shareUrl)" type="primary">复制链接</el-button>
            </div>
          </div>
          <div class="share-result-field" v-if="shareResult.hasPassword">
            <label>提取码：</label>
            <div class="share-result-value">
              <span class="share-result-text">{{ shareResultPassword }}</span>
              <el-button text size="small" @click="copyText(shareResultPassword)" type="primary">复制提取码</el-button>
            </div>
          </div>
        </div>
      </template>
      <template #footer>
        <template v-if="!shareResult">
          <el-button @click="showShareDialog = false">取消</el-button>
          <el-button type="primary" :loading="shareCreating" @click="confirmShare">创建分享</el-button>
        </template>
        <template v-else>
          <el-button @click="showShareDialog = false">关闭</el-button>
        </template>
      </template>
    </el-dialog>
    <!-- Hidden folder upload input -->
    <input
      ref="folderInputRef"
      type="file"
      webkitdirectory
      multiple
      style="display:none"
      @change="handleFolderSelected"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useFileStore } from '@/stores/file'
import { useUploadStore } from '@/stores/upload'
import { getToken } from '@/utils/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, List, Grid, Folder, FolderOpened, FolderAdd,
  Document, Picture, VideoCamera, Headset, Reading,
  Download, Delete, Share, Plus, ArrowLeft
} from '@element-plus/icons-vue'
import { listItems, createFolder, moveToTrash, upload as uploadApi, download as downloadApi, chunkedUpload } from '@/api/file'
import { createShare } from '@/api/share'
import uploadIcon from '@/assets/upload-icon.png'
import { saveUploadFile, removeUploadFile, updateTaskId } from '@/utils/indexeddb'

const router = useRouter()
const route = useRoute()
const fileStore = useFileStore()
const uploadStore = useUploadStore()

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
const selectedFiles = ref([])
const folderInputRef = ref(null)
const uploadFolderCount = ref(0)
const folderUploadTotal = ref(0)

const currentFolderId = computed(() => fileStore.currentFolderId)

// ─── Preview Dialog ───
const showPreviewDialog = ref(false)
const previewItem = ref(null)
const previewUrl = ref('')
const previewType = ref('')
const previewLoading = ref(false)

// ─── Create Share Dialog ───
const showShareDialog = ref(false)
const shareTarget = ref(null)
const shareCreating = ref(false)
const shareForm = ref({
  password: '',
  expireType: 'ONE_WEEK',
  permission: 'download',
  maxDownloads: null
})
const shareResult = ref(null)

const shareUrl = computed(() => {
  if (!shareResult.value?.shareToken) return ''
  return `${window.location.origin}/share/${shareResult.value.shareToken}`
})
const shareResultPassword = computed(() => {
  return shareForm.value.password || ''
})

function getPreviewType(mimeType) {
  if (!mimeType) return 'other'
  if (mimeType.startsWith('image/')) return 'image'
  if (mimeType.startsWith('video/')) return 'video'
  if (mimeType.startsWith('audio/')) return 'audio'
  if (mimeType === 'application/pdf') return 'pdf'
  // Office types - backend will convert to PDF
  if (mimeType.includes('word') || mimeType.includes('document') ||
      mimeType.includes('sheet') || mimeType.includes('excel') ||
      mimeType.includes('powerpoint') || mimeType.includes('presentation')) return 'office'
  return 'other'
}

function openPreview(item) {
  previewItem.value = item
  previewType.value = getPreviewType(item.mimeType)
  previewUrl.value = ''
  showPreviewDialog.value = true
  previewLoading.value = true
  // 用 setTimeout 让 dialog 渲染后再加载 iframe/img
  setTimeout(() => {
    previewUrl.value = `/api/files/preview/${item.id}?token=${getToken()}`
    previewLoading.value = false
  }, 100)
}

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

function formatDate(d) {
  if (!d) return ''
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function handleSelectionChange(selection) {
  selectedIds.value = selection.map(s => s.id)
}

function toggleGridSelection(item) {
  const idx = selectedIds.value.indexOf(item.id)
  if (idx === -1) {
    selectedIds.value.push(item.id)
  } else {
    selectedIds.value.splice(idx, 1)
  }
}

function goToParent() {
  fileStore.goUp()
  const parentId = fileStore.currentFolderId
  if (parentId) {
    router.push(`/files/folder/${parentId}`)
  } else {
    router.push('/files')
  }
  loadFiles()
}

function handleRowDoubleClick(row) {
  if (row.isDirectory) {
    fileStore.enterFolder(row.id, row.name)
    router.push(`/files/folder/${row.id}`)
    loadFiles()
  } else {
    openPreview(row)
  }
}

async function handleDownload(row) {
  try {
    const blob = await downloadApi(row.id)
    // Create a temporary download link
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败：' + (e.response?.data?.msg || e.message || '文件不存在'))
  }
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
  shareTarget.value = row
  shareForm.value = {
    password: '',
    expireType: 'ONE_WEEK',
    permission: 'download',
    maxDownloads: null
  }
  shareResult.value = null
  showShareDialog.value = true
}

async function confirmShare() {
  if (!shareTarget.value) return
  shareCreating.value = true
  try {
    const data = {
      itemId: shareTarget.value.id,
      expireType: shareForm.value.expireType,
      permission: shareForm.value.permission
    }
    if (shareForm.value.password) {
      data.password = shareForm.value.password
    }
    if (shareForm.value.maxDownloads) {
      data.maxDownloads = shareForm.value.maxDownloads
    }
    const res = await createShare(data)
    shareResult.value = res.data
    // Save password to sessionStorage for "copy password" in MyShares
    if (shareForm.value.password && res.data?.id) {
      try {
        sessionStorage.setItem(`share_pwd_${res.data.id}`, shareForm.value.password)
      } catch { /* ignore */ }
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '创建分享失败')
  } finally {
    shareCreating.value = false
  }
}

function resetShareDialog() {
  shareForm.value = {
    password: '',
    expireType: 'ONE_WEEK',
    permission: 'download',
    maxDownloads: null
  }
  shareResult.value = null
  shareTarget.value = null
}

function copyText(text) {
  if (!text) return
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    // Fallback for older browsers
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制到剪贴板')
  })
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
    const filters = {}
    if (searchQuery.value) filters.query = searchQuery.value
    if (typeFilter.value) filters.type = typeFilter.value
    if (dateRange.value) {
      filters.dateFrom = formatDate(dateRange.value[0])
      filters.dateTo = formatDate(dateRange.value[1])
    }
    const res = await listItems(currentFolderId.value, page.value, pageSize.value, filters)
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
  folderInputRef.value?.click()
}

async function handleFolderSelected(e) {
  const files = e.target.files
  if (!files || files.length === 0) return

  // Build a set of directory paths, then a map of file -> relative path
  const fileEntries = []
  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    // webkitRelativePath: "folder/sub/file.txt"
    const relativePath = file.webkitRelativePath
    if (!relativePath) continue
    fileEntries.push({ file, relativePath })
  }

  if (fileEntries.length === 0) {
    ElMessage.warning('未检测到文件')
    return
  }

  // Extract folder name (first component of relativePath)
  const folderName = fileEntries[0].relativePath.split('/')[0]
  const totalSize = fileEntries.reduce((sum, e) => sum + e.file.size, 0)

  ElMessage.info(`正在上传文件夹 "${folderName}" (${fileEntries.length} 个文件)...`)
  const targetParentId = currentFolderId.value

  // Add folder as single item in upload queue
  const folderItem = uploadStore.addFolder(folderName, fileEntries.length, totalSize)

  // 在服务端创建根文件夹
  let rootFolderId = targetParentId
  try {
    const folderRes = await createFolder(folderName, targetParentId)
    if (folderRes.data?.id) {
      rootFolderId = folderRes.data.id
    }
  } catch (e) {
    // 根文件夹可能已存在，查找它
    try {
      const checkRes = await listItems(targetParentId, 1, 100)
      const existing = (checkRes.data?.records || []).find(
        f => f.isDirectory && f.name === folderName
      )
      if (existing?.id) {
        rootFolderId = existing.id
      }
    } catch { /* ignore */ }
  }

  // Track created folder IDs to avoid duplicate creations: key "parentId/name"
  const folderCache = new Map()
  // 把根文件夹加入缓存，后续子目录都在根文件夹下创建
  folderCache.set(`${targetParentId || 'root'}/${folderName}`, rootFolderId)
  let success = 0
  let fail = 0
  folderItem.failedFiles = []

  // Process files one by one, creating folders as needed
  for (let i = 0; i < fileEntries.length; i++) {
    const entry = fileEntries[i]
    const parts = entry.relativePath.split('/')
    parts.shift() // remove root folder name
    const fileName = parts.pop()

    let parentId = rootFolderId

    // Step A: create subdirectory hierarchy
    for (let j = 0; j < parts.length; j++) {
      const dirName = parts[j]
      const cacheKey = `${parentId || 'root'}/${dirName}`
      const cachedId = folderCache.get(cacheKey)
      if (cachedId) {
        parentId = cachedId
        continue
      }
      try {
        const res = await createFolder(dirName, parentId)
        const newId = res.data?.id
        if (newId) {
          folderCache.set(cacheKey, newId)
          parentId = newId
        }
      } catch (e) {
        // Folder likely already exists, find it
        try {
          const res = await listItems(parentId, 1, 100)
          const existing = (res.data?.records || []).find(
            f => f.isDirectory && f.name === dirName
          )
          if (existing?.id) {
            folderCache.set(cacheKey, existing.id)
            parentId = existing.id
            continue
          }
        } catch { /* ignore */ }
      }
    }

    // Step B: check if file already exists in target directory
    try {
      const checkRes = await listItems(parentId, 1, 500)
      const existingFile = (checkRes.data?.records || []).find(
        f => !f.isDirectory && f.name === fileName && f.size === entry.file.size
      )
      if (existingFile) {
        // File already exists (same name + same size), skip
        success++
        folderItem.completedFiles = success + fail
        folderItem.progress = Math.round((folderItem.completedFiles / folderItem.fileCount) * 100)
        uploadStore.markDone(folderItem.id)
        // Actually mark as done only after all files processed
        continue
      }
    } catch { /* proceed with upload */ }

    // Step C: upload the file
    try {
      const CHUNK_THRESHOLD = 10 * 1024 * 1024
      if (entry.file.size > CHUNK_THRESHOLD) {
        await chunkedUpload(entry.file, parentId, (percent) => {
          // per-file progress contribution to folder progress
        }, fileName)
      } else {
        await uploadApi(entry.file, parentId, undefined, undefined, fileName)
      }
      success++
    } catch (e) {
      fail++
      const msg = e.response?.data?.msg || e.message || ''
      let errType = '服务器错误'
      if (msg.includes('空间不足') || msg.includes('exceed')) errType = '空间不足'
      else if (msg.includes('Network Error') || msg.includes('timeout')) errType = '网络问题'
      folderItem.failedFiles.push(entry.relativePath + ' (' + errType + ')')
    }

    // Update folder progress
    folderItem.completedFiles = success + fail
    folderItem.progress = Math.round((folderItem.completedFiles / folderItem.fileCount) * 100)
  }

  // Reset input
  e.target.value = ''

  // Mark folder item done/failed
  if (fail === 0) {
    uploadStore.markDone(folderItem.id)
    ElMessage.success(`文件夹 "${folderName}" 上传完成，${success} 个文件成功`)
  } else {
    uploadStore.markFailed(folderItem.id, `${fail} 个文件上传失败`)
    ElMessage.warning(`文件夹 "${folderName}" 上传完成：${success} 成功，${fail} 失败`)
  }

  loadFiles()
}

function openCreateFolderDialog() {
  showMenu.value = false
  newFolderName.value = ''
  folderDialogVisible.value = true
}

async function confirmCreateFolder() {
  if (!newFolderName.value) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  try {
    await createFolder(newFolderName.value, currentFolderId.value)
    ElMessage.success('文件夹创建成功')
    folderDialogVisible.value = false
    newFolderName.value = ''
    loadFiles()
  } catch (e) {
    const msg = e.response?.data?.msg || e.message || '创建文件夹失败'
    ElMessage.error(msg)
  }
}

async function confirmUpload() {
  const files = selectedFiles.value
  if (files.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }

  // 将文件加入上传队列（status=pending），异步存 IndexedDB
  const items = uploadStore.addFiles(files, currentFolderId.value)
  uploadDialogVisible.value = false
  selectedFiles.value = []
  uploadRef.value?.clearFiles()

  // 启动排队上传
  if (!uploadStore.beginProcessing()) return // 已有在处理中的排队
  let successCount = 0
  let failCount = 0
  let item
  while ((item = uploadStore.startNext())) {
    // 找到对应的 file（队列项和 files 数组下标一致）
    const idx = items.indexOf(item)
    const file = idx >= 0 && idx < files.length ? files[idx] : item.file
    try {
      const CHUNK_THRESHOLD = 10 * 1024 * 1024
      if (file.size > CHUNK_THRESHOLD) {
        item.progress = item.initialProgress || 1
        let targetProgress = item.progress
        let stepTimer = null

        function startStepper() {
          if (stepTimer) return
          stepTimer = setInterval(() => {
            const gap = targetProgress - item.progress
            if (gap <= 0) {
              clearInterval(stepTimer)
              stepTimer = null
              return
            }
            const step = Math.min(3, gap)
            item.progress += step
          }, 400)
        }

        const abortController = new AbortController()
        item.abortController = abortController
        try {
          await chunkedUpload(file, currentFolderId.value, (percent) => {
            targetProgress = percent
            startStepper()
          }, undefined, item.taskId || undefined, item.skipChunks?.length ? item.skipChunks : undefined, (taskId) => {
            item.taskId = taskId
            updateTaskId(item.id, taskId)
          }, abortController.signal)
        } finally {
          if (stepTimer) {
            clearInterval(stepTimer)
            stepTimer = null
          }
        }
      } else {
        await uploadApi(file, currentFolderId.value, (percent) => {
          uploadStore.updateProgress(item.id, percent)
        })
      }
      uploadStore.markDone(item.id)
      successCount++
    } catch (e) {
      const msg = e.response?.data?.msg || e.message || ''
      if (msg.includes('paused')) {
        // 暂停：不删 IndexedDB，不标记失败，取下一个
        continue
      }
      let errorType = '服务器错误'
      if (msg.includes('空间不足') || msg.includes('exceed')) {
        errorType = '空间不足'
      } else if (msg.includes('Network Error') || msg.includes('timeout') || msg.includes('网络')) {
        errorType = '网络问题'
      }
      uploadStore.markFailed(item.id, errorType)
      failCount++
    }
  }
  uploadStore.endProcessing()
  loadFiles()
  if (failCount === 0) {
    ElMessage.success(`成功上传 ${successCount} 个文件`)
  } else {
    ElMessage.warning(`上传完成：${successCount} 成功，${failCount} 失败`)
  }
}

function handleFileSelect(uploadFile) {
  if (uploadFile.raw) {
    selectedFiles.value.push(uploadFile.raw)
  }
}

function handleFileRemove(uploadFile) {
  if (uploadFile.raw) {
    const idx = selectedFiles.value.indexOf(uploadFile.raw)
    if (idx !== -1) selectedFiles.value.splice(idx, 1)
  }
}

function cancelUpload() {
  uploadDialogVisible.value = false
  selectedFiles.value = []
  uploadRef.value?.clearFiles()
}

onMounted(loadFiles)

watch(() => route.params.id, () => {
  const folderId = route.params.id
  if (folderId && folderId !== String(currentFolderId.value)) {
    fileStore.enterFolder(Number(folderId), '')
    loadFiles()
  } else if (!folderId) {
    fileStore.navigateTo(-1)
    loadFiles()
  }
})
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

/* ─── Grid View ─── */
.grid-container {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px;
}

.grid-item {
  width: 120px;
  padding: 12px 8px;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  transition: background 0.2s;
  border: 2px solid transparent;
}

.grid-item:hover {
  background: #f0f5ff;
}

.grid-item-selected {
  background: #e6f0ff;
  border-color: #409eff;
}

.grid-icon {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.grid-name {
  font-size: 13px;
  color: #303133;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
}

.grid-info {
  font-size: 11px;
  color: #909399;
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
  gap: 4px;
}

.fab-menu-item {
  justify-content: flex-start;
  width: 100%;
  text-align: left;
}

.fab-menu-item .el-icon {
  margin-right: 6px;
}

.fab-custom-icon {
  width: 16px;
  height: 16px;
  margin-right: 6px;
  vertical-align: middle;
}

/* ─── Preview Dialog ─── */
.preview-body {
  min-height: 200px;
  max-height: 70vh;
  overflow: auto;
}

.preview-center {
  display: flex;
  justify-content: center;
  align-items: flex-start;
}
</style>
