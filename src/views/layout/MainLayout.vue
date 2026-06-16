<template>
  <el-container class="main-layout">
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="sidebar-header" :class="{ 'is-collapsed': isCollapsed }">
        <div class="logo-mini" v-if="isCollapsed">
          <svg viewBox="0 0 48 48" width="32" height="32">
            <circle cx="24" cy="24" r="22" fill="none" stroke="#409eff" stroke-width="2.5"/>
            <path d="M14 28 L24 16 L34 28" fill="none" stroke="#409eff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
            <line x1="24" y1="16" x2="24" y2="34" stroke="#409eff" stroke-width="2.5" stroke-linecap="round"/>
          </svg>
        </div>
        <h2 class="logo-text" v-else>M78 网盘</h2>
        <el-button class="collapse-btn" :icon="isCollapsed ? Expand : Fold" text @click="isCollapsed = !isCollapsed" />
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapsed"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>
        <el-menu-item index="/files">
          <el-icon><FolderOpened /></el-icon>
          <template #title>文件</template>
        </el-menu-item>
        <el-menu-item index="/trash">
          <el-icon><Delete /></el-icon>
          <template #title>回收站</template>
        </el-menu-item>
        <el-menu-item index="/transfer">
          <el-icon><Upload /></el-icon>
          <template #title>传输</template>
        </el-menu-item>
        <el-menu-item index="/shares">
          <el-icon><Share /></el-icon>
          <template #title>我的分享</template>
        </el-menu-item>
        <el-menu-item index="/albums">
          <el-icon><Picture /></el-icon>
          <template #title>相册</template>
        </el-menu-item>
        <el-menu-item index="/vault">
          <el-icon><Lock /></el-icon>
          <template #title>保险箱</template>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer" v-if="!isCollapsed">
        <el-divider style="margin: 8px 0" />
        <div class="user-info">
          <el-avatar :size="32" :src="userStore.userInfo?.avatarUrl || undefined" icon="UserFilled" style="cursor:pointer" @click="showProfileDialog = true" />
          <span class="username" style="cursor:pointer" @click="showProfileDialog = true">{{ userStore.username || '用户' }}</span>
          <el-button text size="small" @click="handleLogout" title="退出登录">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </div>
    </el-aside>

    <el-container class="main-area">
      <el-header class="main-header" height="50px">
        <BreadCrumb />
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- Profile Dialog -->
  <el-dialog v-model="showProfileDialog" title="个人资料" width="420px" :close-on-click-modal="false">
    <el-form label-width="80px">
      <el-form-item label="头像">
        <div class="profile-avatar-col">
          <el-avatar :size="60" :src="previewAvatarUrl || profileForm.avatarUrl || undefined" icon="UserFilled" />
          <div class="avatar-actions">
            <el-button size="small" @click="openImagePicker">从已有图片选择</el-button>
            <el-button size="small" @click="triggerUpload">上传新图片</el-button>
            <input ref="fileInputEl" type="file" accept="image/*" style="display:none" @change="handleAvatarUpload" />
          </div>
        </div>
      </el-form-item>
      <el-form-item label="用户名">
        <el-input v-model="profileForm.username" placeholder="用户名" maxlength="32" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input :model-value="userStore.userInfo?.email || ''" disabled />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showPasswordDialog = true">修改密码</el-button>
      <el-button @click="showProfileDialog = false">取消</el-button>
      <el-button type="primary" :loading="savingProfile" @click="handleSaveProfile">保存</el-button>
    </template>
  </el-dialog>

  <!-- Password Dialog -->
  <el-dialog v-model="showPasswordDialog" title="修改密码" width="380px" :close-on-click-modal="false">
    <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="0">
      <el-form-item prop="oldPassword">
        <el-input v-model="pwdForm.oldPassword" type="password" placeholder="原密码" show-password />
      </el-form-item>
      <el-form-item prop="newPassword">
        <el-input v-model="pwdForm.newPassword" type="password" placeholder="新密码（至少6位）" show-password />
      </el-form-item>
      <el-form-item prop="confirmPassword">
        <el-input v-model="pwdForm.confirmPassword" type="password" placeholder="确认新密码" show-password />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showPasswordDialog = false">取消</el-button>
      <el-button type="primary" :loading="savingPassword" @click="handleSavePassword">确认修改</el-button>
    </template>
  </el-dialog>

  <!-- Image Picker Dialog -->
  <el-dialog v-model="showImagePicker" title="选择已有图片作为头像" width="500px" :close-on-click-modal="false">
    <div v-loading="imageLoading" class="image-grid">
      <div v-if="imageList.length === 0" class="empty-images">
        <el-empty description="暂无图片文件，请先上传" />
      </div>
      <div
        v-for="img in imageList"
        :key="img.id"
        class="image-grid-item"
        :class="{ 'image-selected': selectedImageId === img.id }"
        @click="selectImage(img)"
      >
        <img :src="`/api/files/preview/${img.id}?token=${getToken()}`" :alt="img.name" class="image-thumb" />
        <div class="image-name">{{ img.name }}</div>
      </div>
    </div>
    <template #footer>
      <el-button @click="showImagePicker = false">取消</el-button>
      <el-button type="primary" :disabled="!selectedImageId" @click="confirmImageSelection">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  HomeFilled, FolderOpened, Delete, Upload, Share,
  Picture, Lock,
  Expand, Fold, UserFilled, SwitchButton
} from '@element-plus/icons-vue'
import BreadCrumb from '@/components/BreadCrumb.vue'
import { updateProfile, updatePassword, updateAvatar, uploadAvatarTemp } from '@/api/user'
import { listItems } from '@/api/file'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)

// ─── Profile Dialog ───
const showProfileDialog = ref(false)
const savingProfile = ref(false)
const profileForm = reactive({ username: '', avatarUrl: '' })

// ─── Avatar Selection ───
const previewAvatarUrl = ref('')
const showImagePicker = ref(false)
const imageList = ref([])
const imageLoading = ref(false)
const selectedImageId = ref(null)
const selectedImageStorageKey = ref('')
const fileInputEl = ref(null)

watch(showProfileDialog, (val) => {
  if (val && userStore.userInfo) {
    profileForm.username = userStore.userInfo.username || ''
    profileForm.avatarUrl = userStore.userInfo.avatarUrl || ''
    previewAvatarUrl.value = ''
  }
})

// ─── Password Dialog ───
const showPasswordDialog = ref(false)
const savingPassword = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value && value === pwdForm.oldPassword) callback(new Error('新密码不能与当前密码相同'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur'
    }
  ]
}

const activeMenu = computed(() => {
  if (route.path.startsWith('/files')) return '/files'
  return route.path
})

function handleLogout() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示').then(() => {
    userStore.logout()
    router.push('/login')
  }).catch(() => {})
}

async function handleSaveProfile() {
  savingProfile.value = true
  try {
    const username = profileForm.username.trim()

    // 先更新用户名（如果变了）
    if (username && username !== userStore.userInfo?.username) {
      await updateProfile(username)
    }

    // 更新头像（如果选了新头像且与当前不同）
    const avatarToSave = previewAvatarUrl.value || profileForm.avatarUrl
    if (previewAvatarUrl.value) {
      if (pendingAvatarKey.value) {
        // 从已有图片选择：用 storageKey 构造 OSS URL
        const { updateAvatarByKey } = await import('@/api/user')
        await updateAvatarByKey(pendingAvatarKey.value)
      } else if (previewAvatarUrl.value !== (userStore.userInfo?.avatarUrl || '')) {
        // 上传新图片：直接用 URL
        await updateAvatar(avatarToSave)
      }
    }

    ElMessage.success('资料已更新')
    await userStore.fetchUserInfo()
    showProfileDialog.value = false
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '保存失败')
  } finally {
    savingProfile.value = false
  }
}

// ─── Avatar Functions ───
const pendingAvatarKey = ref('')  // 从已有图片选中的 storageKey，保存时才提交

function triggerUpload() {
  fileInputEl.value?.click()
}

async function handleAvatarUpload(e) {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadAvatarTemp(file)
    previewAvatarUrl.value = res.data
    pendingAvatarKey.value = ''   // 上传方式不走 storageKey
    ElMessage.success('头像上传成功，点击保存后生效')
  } catch (err) {
    ElMessage.error(err.response?.data?.msg || err.message || '头像上传失败')
  }
  e.target.value = ''
}

async function openImagePicker() {
  showImagePicker.value = true
  selectedImageId.value = null
  selectedImageStorageKey.value = ''
  imageLoading.value = true
  try {
    const res = await listItems(null, 1, 200, { type: 'image' })
    imageList.value = (res.data?.records || []).filter(f => !f.isDirectory)
  } catch {
    imageList.value = []
  } finally {
    imageLoading.value = false
  }
}

function selectImage(img) {
  selectedImageId.value = img.id
  selectedImageStorageKey.value = img.storageKey || ''
}

function confirmImageSelection() {
  if (!selectedImageStorageKey.value) {
    ElMessage.warning('无法获取该图片的存储信息')
    return
  }
  // 用预览 URL 展示，storageKey 暂存，保存时才提交
  previewAvatarUrl.value = `/api/files/preview/${selectedImageId.value}?token=${getToken()}`
  pendingAvatarKey.value = selectedImageStorageKey.value
  showImagePicker.value = false
  ElMessage.success('已选择新头像，点击保存后生效')
}

async function handleSavePassword() {
  const valid = await pwdFormRef.value.validate().catch(() => false)
  if (!valid) return
  savingPassword.value = true
  try {
    await updatePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码已修改，请重新登录')
    showPasswordDialog.value = false
    showProfileDialog.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    userStore.logout()
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '修改密码失败')
  } finally {
    savingPassword.value = false
  }
}

// 页面加载时校验 token 有效性并加载用户信息
onMounted(async () => {
  if (getToken() && !userStore.userInfo) {
    const ok = await userStore.fetchUserInfo()
    if (!ok) {
      userStore.logout()
      router.push('/login')
    }
  }
})
</script>

<style scoped>
.main-layout {
  height: 100vh;
}

.sidebar {
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  overflow: hidden;
}

.sidebar-header {
  height: 50px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid #e4e7ed;
  gap: 8px;
}

.sidebar-header.is-collapsed {
  padding: 0;
  justify-content: center;
  gap: 0;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: #409eff;
  flex: 1;
  white-space: nowrap;
}

.logo-mini {
  flex: 1;
  text-align: center;
}

.collapse-btn {
  flex-shrink: 0;
}

.sidebar-menu {
  flex: 1;
  border-right: none;
}

.sidebar-footer {
  padding: 8px 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.username {
  flex: 1;
  font-size: 14px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-avatar-col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.avatar-actions {
  display: flex;
  flex-direction: row;
  gap: 8px;
  justify-content: center;
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
}

.empty-images {
  width: 100%;
}

.image-grid-item {
  width: 100px;
  padding: 6px;
  border-radius: 6px;
  border: 2px solid transparent;
  cursor: pointer;
  text-align: center;
  transition: border-color 0.2s;
}

.image-grid-item:hover {
  border-color: #409eff;
}

.image-selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.image-thumb {
  width: 80px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
}

.image-name {
  font-size: 11px;
  color: #606266;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-area {
  display: flex;
  flex-direction: column;
}

.main-header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.main-content {
  background: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}
</style>
