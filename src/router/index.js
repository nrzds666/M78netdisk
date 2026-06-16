import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '@/utils/auth'

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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  // Allow public share access without auth
  if (to.name === 'ShareAccess' || to.name === 'ShareAccessFolder' || to.name === 'AlbumShareAccess') {
    next()
    return
  }
  if (to.name !== 'Login' && !getToken()) {
    next({ name: 'Login' })
  } else {
    next()
  }
})

export default router
