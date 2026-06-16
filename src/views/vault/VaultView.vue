<template>
  <div class="vault-page">
    <!-- State 1: Not Setup -->
    <el-card v-if="!status.setup" shadow="never" class="vault-card">
      <div class="vault-form-wrap">
        <el-icon :size="48" color="#409eff"><Lock /></el-icon>
        <h2 class="vault-title">设置保险箱密码</h2>
        <p class="vault-desc">首次使用，请设置一个密码来保护您的保险箱文件</p>
        <el-form
          ref="setupFormRef"
          :model="setupForm"
          :rules="setupRules"
          label-width="0"
          class="vault-form"
          @submit.prevent="handleSetup"
        >
          <el-form-item prop="loginPassword">
            <el-input
              v-model="setupForm.loginPassword"
              type="password"
              placeholder="请输入登录密码以验证身份"
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="setupForm.password"
              type="password"
              placeholder="请输入保险箱密码"
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="setupForm.confirmPassword"
              type="password"
              placeholder="请确认保险箱密码"
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="submitting"
              native-type="submit"
              style="width:100%"
            >
              设置密码
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- State 2: Locked (setup=true, unlocked=false) -->
    <el-card v-else-if="!status.unlocked" shadow="never" class="vault-card">
      <div class="vault-form-wrap">
        <el-icon :size="48" color="#e6a23c"><Lock /></el-icon>
        <h2 class="vault-title">保险箱已锁定</h2>
        <p class="vault-desc">请输入密码解锁保险箱</p>
        <el-form
          ref="unlockFormRef"
          :model="unlockForm"
          :rules="unlockRules"
          label-width="0"
          class="vault-form"
          @submit.prevent="handleUnlock"
        >
          <el-form-item prop="password">
            <el-input
              v-model="unlockForm.password"
              type="password"
              placeholder="请输入保险箱密码"
              show-password
              size="large"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              size="large"
              :loading="submitting"
              native-type="submit"
              style="width:100%"
            >
              解锁
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- State 3: Unlocked (setup=true, unlocked=true) -->
    <template v-else>
      <!-- Action Bar -->
      <el-card shadow="never" class="action-bar">
        <el-row :gutter="16" align="middle">
          <el-col :span="12">
            <span class="action-title">保险箱</span>
            <span class="action-subtitle" v-if="total > 0">（共 {{ total }} 个文件）</span>
          </el-col>
          <el-col :span="12" style="text-align:right">
            <el-button type="warning" @click="handleLock" :loading="submitting">
              <el-icon><Lock /></el-icon> 锁定
            </el-button>
            <el-button type="primary" @click="handleUpload">
              <el-icon><Upload /></el-icon> 上传文件
            </el-button>
          </el-col>
        </el-row>
      </el-card>

      <!-- File Table -->
      <el-card shadow="never" class="vault-table-card" v-loading="loading">
        <el-empty v-if="!loading && items.length === 0" description="保险箱暂无文件" />
        <template v-else>
          <el-table :data="items" style="width:100%" stripe>
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
            <el-table-column label="上传时间" width="170">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt || row.uploadedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  text
                  type="danger"
                  size="small"
                  @click="handleRemove(row)"
                >
                  移出保险箱
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
              @current-change="loadItems"
              small
            />
          </div>
        </template>
      </el-card>
    </template>

    <!-- Hidden file input for upload -->
    <input
      ref="fileInputRef"
      type="file"
      style="display:none"
      @change="onFileSelected"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Lock, Upload, Folder, Document, Picture, VideoCamera, Headset, Reading
} from '@element-plus/icons-vue'
import { getStatus, setup, unlock, lock, listVaultItems, uploadToVault, removeFromVault } from '@/api/vault'

// ─── Status ───
const status = reactive({
  setup: false,
  unlocked: false
})

// ─── Setup Form ───
const setupFormRef = ref(null)
const setupForm = reactive({
  loginPassword: '',
  password: '',
  confirmPassword: ''
})
const setupRules = {
  loginPassword: [
    { required: true, message: '请输入登录密码', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入保险箱密码', trigger: 'blur' },
    { min: 4, max: 32, message: '密码长度 4-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认保险箱密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== setupForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// ─── Unlock Form ───
const unlockFormRef = ref(null)
const unlockForm = reactive({
  password: ''
})
const unlockRules = {
  password: [
    { required: true, message: '请输入保险箱密码', trigger: 'blur' }
  ]
}

// ─── File List ───
const items = ref([])
const loading = ref(false)
const submitting = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const fileInputRef = ref(null)

// ─── Methods ───

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

async function loadStatus() {
  try {
    const res = await getStatus()
    status.setup = res.data?.enabled || false
    status.unlocked = res.data?.unlocked || false
  } catch (e) {
    // On error, default to unset state
    status.setup = false
    status.unlocked = false
  }
}

async function loadItems() {
  loading.value = true
  try {
    const res = await listVaultItems(null, page.value, pageSize.value)
    items.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function handleSetup() {
  const valid = await setupFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await setup(setupForm.loginPassword, setupForm.password, setupForm.confirmPassword)
    ElMessage.success('保险箱设置成功')
    status.setup = true
    status.unlocked = true
    setupForm.loginPassword = ''
    setupForm.password = ''
    setupForm.confirmPassword = ''
    loadItems()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '设置失败')
  } finally {
    submitting.value = false
  }
}

async function handleUnlock() {
  const valid = await unlockFormRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await unlock(unlockForm.password)
    ElMessage.success('保险箱已解锁')
    status.unlocked = true
    unlockForm.password = ''
    loadItems()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '解锁失败')
  } finally {
    submitting.value = false
  }
}

async function handleLock() {
  submitting.value = true
  try {
    await lock()
    ElMessage.success('保险箱已锁定')
    status.unlocked = false
    items.value = []
    total.value = 0
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '锁定失败')
  } finally {
    submitting.value = false
  }
}

function handleUpload() {
  fileInputRef.value?.click()
}

async function onFileSelected(e) {
  const file = e.target?.files?.[0]
  if (!file) return
  submitting.value = true
  try {
    await uploadToVault(file, null)
    ElMessage.success(`已上传「${file.name}」`)
    loadItems()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '上传失败')
  } finally {
    submitting.value = false
    // Reset file input so the same file can be selected again
    if (fileInputRef.value) {
      fileInputRef.value.value = ''
    }
  }
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm(
      `确定要将「${row.name}」移出保险箱？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    await removeFromVault(row.id || row.itemId)
    ElMessage.success(`已移出「${row.name}」`)
    loadItems()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.msg || e.message || '操作失败')
    }
  }
}

onMounted(async () => {
  await loadStatus()
  if (status.setup && status.unlocked) {
    loadItems()
  }
})
</script>

<style scoped>
.vault-page {
  min-height: 400px;
}

.vault-card {
  max-width: 420px;
  margin: 60px auto;
  border-radius: 12px;
  padding: 40px 32px;
}

.vault-form-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.vault-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 12px 0 4px;
}

.vault-desc {
  font-size: 14px;
  color: #909399;
  margin-bottom: 24px;
  text-align: center;
}

.vault-form {
  width: 100%;
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

.vault-table-card {
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
