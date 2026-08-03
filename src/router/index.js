import { createRouter, createWebHistory } from 'vue-router'
import axios from 'axios'
import { getToken, clearAuth } from '@/utils/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue')
  },
  // Share access — no auth required, standalone layout
  {
    path: '/share/:token',
    name: 'ShareAccess',
    component: () => import('@/views/share/ShareAccessView.vue')
  },
  {
    path: '/share/:token/folder/:folderId',
    name: 'ShareAccessFolder',
    component: () => import('@/views/share/ShareAccessView.vue')
  },
  // Album share — no auth required, standalone layout
  {
    path: '/album-share/:token',
    name: 'AlbumShareAccess',
    component: () => import('@/views/album/AlbumShareAccessView.vue')
  },
  {
    path: '/',
    component: () => import('@/views/layout/MainLayout.vue'),
    redirect: '/files',
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/home/HomeView.vue')
      },
      {
        path: 'files',
        name: 'Files',
        component: () => import('@/views/file/FileListView.vue')
      },
      {
        path: 'files/folder/:id',
        name: 'FilesFolder',
        component: () => import('@/views/file/FileListView.vue')
      },
      {
        path: 'trash',
        name: 'Trash',
        component: () => import('@/views/trash/TrashView.vue')
      },
      {
        path: 'transfer',
        name: 'Transfer',
        component: () => import('@/views/transfer/TransferView.vue')
      },
      {
        path: 'shares',
        name: 'MyShares',
        component: () => import('@/views/share/MySharesView.vue')
      },
      {
        path: 'albums',
        name: 'Albums',
        component: () => import('@/views/album/AlbumView.vue')
      },
      {
        path: 'albums/:id',
        name: 'AlbumDetail',
        component: () => import('@/views/album/AlbumDetailView.vue')
      },
      {
        path: 'vault',
        name: 'Vault',
        component: () => import('@/views/vault/VaultView.vue')
      },
      {
        path: 'admin',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/AdminDashboardView.vue')
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/AdminUsersView.vue')
      },
      {
        path: 'admin/nodes',
        name: 'AdminNodes',
        component: () => import('@/views/admin/AdminNodesView.vue')
      },
      {
        path: 'admin/logs',
        name: 'AdminLogs',
        component: () => import('@/views/admin/AdminLogsView.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

let tokenValidated = false

router.beforeEach(async (to, from, next) => {
  // 公开访问页面不校验
  if (to.name === 'ShareAccess' || to.name === 'ShareAccessFolder' || to.name === 'AlbumShareAccess') {
    next()
    return
  }
  // 登录页直接放行
  if (to.name === 'Login') {
    next()
    return
  }

  const token = getToken()
  if (!token) {
    next({ name: 'Login' })
    return
  }

  // 首次加载时主动向后端验 token，失败直接跳登录
  if (!tokenValidated) {
    tokenValidated = true
    try {
      await axios.get((import.meta.env.VITE_APP_BASE_API || '') + '/api/files/list?page=1&size=1', {
        headers: { Authorization: `Bearer ${token}` }
      })
    } catch {
      clearAuth()
      next({ name: 'Login' })
      return
    }
  }

  next()
})

export default router
