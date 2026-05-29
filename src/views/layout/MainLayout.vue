<template>
  <el-container class="main-layout">
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="sidebar">
      <div class="sidebar-header">
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
      </el-menu>

      <div class="sidebar-footer" v-if="!isCollapsed">
        <el-divider style="margin: 8px 0" />
        <div class="user-info">
          <el-avatar :size="32" icon="UserFilled" />
          <span class="username">{{ userStore.username || '用户' }}</span>
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
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'
import {
  HomeFilled, FolderOpened, Delete, Upload, Share,
  Expand, Fold, UserFilled, SwitchButton
} from '@element-plus/icons-vue'
import BreadCrumb from '@/components/BreadCrumb.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapsed = ref(false)

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
