<template>
  <div class="admin-dashboard">
    <h3 style="margin: 0 0 16px; color: #303133;">仪表盘</h3>
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="hover" class="stat-card">
          <div class="card-icon" :style="{ background: card.color }">
            <el-icon :size="28"><component :is="card.icon" /></el-icon>
          </div>
          <div class="card-content">
            <div class="card-value">{{ card.value }}</div>
            <div class="card-label">{{ card.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { User, Folder, Document, Coin } from '@element-plus/icons-vue'
import { getStatsOverview } from '@/api/admin'

const stats = ref({})

const cards = computed(() => [
  { label: '用户数', value: stats.value.totalUsers ?? '-', icon: User, color: '#ecf5ff' },
  { label: '文件数', value: stats.value.totalFiles ?? '-', icon: Document, color: '#f0f9eb' },
  { label: '文件夹数', value: stats.value.totalFolders ?? '-', icon: Folder, color: '#fdf6ec' },
  { label: '已用空间', value: formatBytes(stats.value.usedBytes), icon: Coin, color: '#fef0f0' }
])

function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
}

onMounted(async () => {
  try {
    const res = await getStatsOverview()
    stats.value = res.data
  } catch { /* silent */ }
})
</script>

<style scoped>
.stat-card {
  border-radius: 8px;
}
.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}
.card-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}
.card-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}
.card-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
</style>
