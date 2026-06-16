<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item>
      <span style="cursor:pointer" @click="goHome">
        <el-icon><HomeFilled /></el-icon>
      </span>
    </el-breadcrumb-item>
    <el-breadcrumb-item
      v-for="(item, index) in pathStack"
      :key="index"
    >
      <span style="cursor:pointer" @click="goTo(index)">{{ item.name }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useFileStore } from '@/stores/file'
import { HomeFilled } from '@element-plus/icons-vue'

defineOptions({ name: 'BreadCrumb' })

const router = useRouter()
const route = useRoute()
const fileStore = useFileStore()

const pathStack = computed(() => {
  if (route.path === '/' || route.path.startsWith('/home')) return []
  return fileStore.currentPath
})

function goHome() {
  fileStore.navigateTo(-1)
  router.push('/files')
}

function goTo(index) {
  fileStore.navigateTo(index)
  const folderId = fileStore.currentFolderId
  if (folderId) {
    router.push(`/files/folder/${folderId}`)
  } else {
    router.push('/files')
  }
}
</script>
