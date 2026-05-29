<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item :to="{ path: '/' }">
      <el-icon><HomeFilled /></el-icon>
    </el-breadcrumb-item>
    <el-breadcrumb-item
      v-for="(item, index) in pathStack"
      :key="index"
      :to="item.path ? { path: item.path } : undefined"
    >
      {{ item.name }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useFileStore } from '@/stores/file'
import { HomeFilled } from '@element-plus/icons-vue'

defineOptions({ name: 'BreadCrumb' })

const route = useRoute()
const fileStore = useFileStore()

const pathStack = computed(() => {
  if (route.path === '/' || route.path.startsWith('/home')) return []
  return fileStore.currentPath
})
</script>
