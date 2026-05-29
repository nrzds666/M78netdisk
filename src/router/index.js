import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue')
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
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.name !== 'Login' && !token) {
    next({ name: 'Login' })
  } else {
    next()
  }
})

export default router
