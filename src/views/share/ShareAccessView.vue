<template>
  <div class="share-access-page">
    <!-- Top bar -->
    <div class="share-topbar">
      <div class="share-topbar-inner">
        <a class="share-logo" href="/">M78 网盘</a>
        <span class="share-title" v-if="shareInfo">
          {{ shareInfo.isDirectory ? '分享文件夹: ' : '分享文件: ' }}{{ shareInfo.fileName }}
        </span>
        <div class="share-owner-info" v-if="shareInfo?.ownerName">
          <el-avatar v-if="shareInfo.ownerAvatar" :size="24" :src="shareInfo.ownerAvatar" />
          <el-avatar v-else :size="24" style="background:#409eff">{{ shareInfo.ownerName.charAt(0) }}</el-avatar>
          <span class="share-owner-name">{{ shareInfo.ownerName }}</span>
        </div>
      </div>
    </div>

    <div class="share-body">
      <!-- Password Gate -->
      <div v-if="!accessGranted" class="share-password-gate">
        <el-card shadow="never" class="gate-card">
          <template v-if="shareInfo && shareInfo.hasPassword">
            <h3>此分享需要提取码</h3>
            <p class="gate-desc">文件：{{ shareInfo.fileName }}</p>
            <el-input
              v-model="passwordInput"
              placeholder="请输入提取码"
              show-password
              style="width:280px"
              @keyup.enter="handleAccess"
            />
            <div style="margin-top:12px">
              <el-button type="primary" :loading="accessLoading" @click="handleAccess">
                访问分享
              </el-button>
            </div>
            <p v-if="accessError" class="gate-error">{{ accessError }}</p>
          </template>
          <template v-else>
            <div v-loading="accessLoading" class="gate-loading">
              <p>正在验证分享链接...</p>
            </div>
          </template>
        </el-card>
      </div>

      <!-- Access Granted: File List -->
      <template v-else>
        <el-card shadow="never" class="share-files-card">
          <!-- Breadcrumb / Navigation -->
          <div class="share-nav">
            <el-button
              v-if="currentParentId"
              text
              :icon="ArrowLeft"
              @click="goToParent"
            >
              返回上级
            </el-button>
            <span class="share-nav-path" v-if="breadcrumb.length > 0">
              <span v-for="(cr, idx) in breadcrumb" :key="idx">
                <span v-if="idx > 0" class="nav-sep"> / </span>
                <span class="nav-crumb">{{ cr.name }}</span>
              </span>
            </span>
          </div>

          <!-- Save Selected -->
          <div class="share-toolbar" v-if="selectedItemIds.length > 0">
            <span class="selected-count">已选 {{ selectedItemIds.length }} 项</span>
            <el-button type="primary" size="small" :loading="saving" @click="handleSaveSelected">
              保存到网盘
            </el-button>
          </div>

          <!-- Files Table -->
          <el-table
            :data="items"
            style="width:100%"
            stripe
            v-loading="itemsLoading"
            @row-dblclick="handleRowDblClick"
            @selection-change="handleSelectionChange"
          >
            <el-table-column type="selection" width="40" />
            <el-table-column label="文件名" min-width="300">
              <template #default="{ row }">
                <el-icon :size="20" :color="row.isDirectory ? '#e6a23c' : getIconColor(row.mimeType)">
                  <Folder v-if="row.isDirectory" />
                  <Document v-else />
                </el-icon>
                <span class="file-name-text">{{ row.name }}</span>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="100" align="right">
              <template #default="{ row }">
                <span v-if="!row.isDirectory">{{ formatSize(row.size) }}</span>
                <span v-else style="color:#909399">-</span>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                {{ row.isDirectory ? '文件夹' : getTypeLabel(row.mimeType) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="!row.isDirectory && canDownload"
                  text
                  size="small"
                  @click="handleDownload(row)"
                >
                  下载
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="!itemsLoading && items.length === 0" description="文件夹为空" />

          <div class="pagination-wrap" v-if="totalItems > pageSize">
            <el-pagination
              v-model:current-page="page"
              :page-size="pageSize"
              :total="totalItems"
              layout="prev, pager, next"
              @current-change="loadItems"
              small
            />
          </div>
        </el-card>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Folder, Document, ArrowLeft } from '@element-plus/icons-vue'
import { accessShare, listShareItems, downloadShareFile, saveShareFiles } from '@/api/share'
import { getToken } from '@/utils/auth'

const route = useRoute()
const router = useRouter()

const token = computed(() => route.params.token)

// ─── Access State ───
const shareInfo = ref(null)
const accessGranted = ref(false)
const accessLoading = ref(true)
const accessError = ref('')
const passwordInput = ref('')

// ─── Browse State ───
const items = ref([])
const itemsLoading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)
const currentParentId = ref(null)
const breadcrumb = ref([])
const selectedItemIds = ref([])

const canDownload = computed(() => {
  return shareInfo.value?.permission === 'download' || shareInfo.value?.permission === 'edit'
})

// ─── Access Share ───
async function handleAccess() {
  accessLoading.value = true
  accessError.value = ''
  try {
    const res = await accessShare(token.value, passwordInput.value || undefined)
    shareInfo.value = res.data
    if (res.data.accessGranted) {
      accessGranted.value = true
      loadItems()
    }
  } catch (e) {
    accessError.value = e.response?.data?.msg || e.message || '分享链接无效或已过期'
  } finally {
    accessLoading.value = false
  }
}

// ─── Browse Items ───
async function loadItems() {
  itemsLoading.value = true
  try {
    const res = await listShareItems(
      token.value,
      passwordInput.value || undefined,
      currentParentId.value,
      page.value,
      pageSize.value
    )
    items.value = res.data?.records || []
    totalItems.value = res.data?.total || 0
  } catch (e) {
    items.value = []
    totalItems.value = 0
    ElMessage.error('加载文件列表失败')
  } finally {
    itemsLoading.value = false
  }
}

function handleRowDblClick(row) {
  if (row.isDirectory) {
    breadcrumb.value.push({ id: row.id, name: row.name })
    currentParentId.value = row.id
    page.value = 1
    loadItems()
  } else {
    handleDownload(row)
  }
}

function goToParent() {
  if (breadcrumb.value.length > 0) {
    breadcrumb.value.pop()
    currentParentId.value = breadcrumb.value.length > 0
      ? breadcrumb.value[breadcrumb.value.length - 1].id
      : null
    page.value = 1
    loadItems()
  }
}

function handleSelectionChange(selection) {
  selectedItemIds.value = selection.filter(s => !s.isDirectory).map(s => s.id)
}

// ─── Download ───
const saving = ref(false)

async function handleDownload(row) {
  try {
    const password = passwordInput.value || undefined
    const blob = await downloadShareFile(token.value, password, row.id)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败：' + (e.response?.data?.msg || e.message))
  }
}

async function handleSaveSelected() {
  if (selectedItemIds.value.length === 0) {
    ElMessage.warning('请选择要保存的文件')
    return
  }
  if (!getToken()) {
    ElMessage.warning('请先登录后再保存到网盘')
    return
  }
  saving.value = true
  try {
    const res = await saveShareFiles(
      token.value,
      passwordInput.value || undefined,
      selectedItemIds.value
    )
    ElMessage.success(`已保存 ${res.data?.length || selectedItemIds.value.length} 个文件到网盘根目录`)
    selectedItemIds.value = []
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ─── Auto-access on mount (for links without password) ───
onMounted(async () => {
  if (token.value) {
    await handleAccess()
  }
})

// ─── Helpers ───
function formatSize(bytes) {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function getIconColor(mimeType) {
  if (!mimeType) return '#909399'
  if (mimeType.startsWith('image')) return '#67c23a'
  if (mimeType.startsWith('video')) return '#e6a23c'
  if (mimeType.startsWith('audio')) return '#409eff'
  return '#909399'
}

function getTypeLabel(mimeType) {
  if (!mimeType) return '文件'
  if (mimeType.startsWith('image')) return '图片'
  if (mimeType.startsWith('video')) return '视频'
  if (mimeType.startsWith('audio')) return '音频'
  return mimeType.split('/').pop() || '文件'
}
</script>

<style scoped>
.share-access-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.share-topbar {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 12px 24px;
}

.share-topbar-inner {
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 16px;
}

.share-logo {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
  text-decoration: none;
}

.share-title {
  font-size: 14px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share-owner-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  flex-shrink: 0;
}

.share-owner-name {
  font-size: 13px;
  color: #909399;
}

.share-body {
  max-width: 1000px;
  margin: 24px auto;
  padding: 0 16px;
}

/* Password Gate */
.share-password-gate {
  display: flex;
  justify-content: center;
  margin-top: 80px;
}

.gate-card {
  width: 420px;
  padding: 32px;
  text-align: center;
  border-radius: 12px;
}

.gate-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.gate-desc {
  color: #909399;
  font-size: 14px;
  margin-bottom: 20px;
}

.gate-error {
  color: #f56c6c;
  font-size: 13px;
  margin-top: 8px;
}

.gate-loading {
  padding: 40px 0;
}

/* File list */
.share-files-card {
  border-radius: 8px;
  min-height: 300px;
}

.share-nav {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  gap: 8px;
}

.share-nav-path {
  font-size: 14px;
  color: #606266;
}

.nav-sep {
  color: #c0c4cc;
  margin: 0 2px;
}

.nav-crumb {
  color: #303133;
}

.share-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 0;
}

.selected-count {
  font-size: 13px;
  color: #409eff;
}

.file-name-text {
  margin-left: 6px;
  font-size: 14px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}
</style>
