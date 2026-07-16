<template>
  <div class="admin-logs">
    <div class="page-header">
      <h3 style="margin: 0; color: #303133;">操作日志</h3>
      <el-row :gutter="12">
        <el-col><el-input v-model="filters.action" placeholder="操作类型" clearable style="width: 140px" @change="loadLogs" /></el-col>
        <el-col><el-input-number v-model="filters.userId" placeholder="用户ID" :min="0" style="width: 130px" controls-position="right" @change="loadLogs" /></el-col>
        <el-col><el-date-picker v-model="dateRange" type="datetimerange" range-separator="至" start-placeholder="开始" end-placeholder="结束" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 340px" @change="onDateChange" /></el-col>
      </el-row>
    </div>

    <el-table :data="logs" v-loading="loading" stripe style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="userId" label="用户ID" width="80" />
      <el-table-column prop="action" label="操作" width="120" />
      <el-table-column prop="itemId" label="文件ID" width="80">
        <template #default="{ row }">{{ row.itemId || '-' }}</template>
      </el-table-column>
      <el-table-column prop="detail" label="详情" min-width="180" show-overflow-tooltip />
      <el-table-column prop="ipAddress" label="IP" width="140">
        <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="170" />
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @size-change="loadLogs"
      @current-change="loadLogs"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getLogs } from '@/api/admin'

const logs = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ action: '', userId: null })
const dateRange = ref(null)

function onDateChange(val) {
  filters.dateFrom = val?.[0] || null
  filters.dateTo = val?.[1] || null
  loadLogs()
}

async function loadLogs() {
  loading.value = true
  try {
    const params = {}
    if (filters.userId) params.userId = filters.userId
    if (filters.action) params.action = filters.action
    if (filters.dateFrom) params.dateFrom = filters.dateFrom
    if (filters.dateTo) params.dateTo = filters.dateTo
    const res = await getLogs(page.value, size.value, params)
    logs.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
</script>

<style scoped>
.page-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
</style>
