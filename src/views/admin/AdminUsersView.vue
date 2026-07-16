<template>
  <div class="admin-users">
    <div class="page-header">
      <h3 style="margin: 0; color: #303133;">用户管理</h3>
      <el-input v-model="keyword" placeholder="搜索用户名" style="width: 240px" clearable @input="loadUsers" />
    </div>

    <el-table :data="users" v-loading="loading" stripe style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="160">
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </el-table-column>
      <el-table-column label="已用/配额" min-width="180">
        <template #default="{ row }">
          {{ formatBytes(row.usedBytes) }} / {{ formatBytes(row.quotaBytes) }}
          <el-button type="primary" link size="small" @click="showQuotaDialog(row)">修改</el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="170">
        <template #default="{ row }">{{ row.createdAt }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="warning" link size="small" v-if="row.status === 1" @click="updateStatus(row, 0)">禁用</el-button>
          <el-button type="success" link size="small" v-if="row.status !== 1" @click="updateStatus(row, 1)">启用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top: 16px; justify-content: flex-end"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @size-change="loadUsers"
      @current-change="loadUsers"
    />

    <!-- Quota Dialog -->
    <el-dialog v-model="quotaVisible" title="调整配额" width="360px">
      <el-form>
        <el-form-item label="用户">{{ quotaUser?.username }}</el-form-item>
        <el-form-item label="配额(GB)">
          <el-input-number v-model="quotaGb" :min="0" :step="1" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="quotaVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuota">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers, updateUserStatus, updateUserQuota } from '@/api/admin'

const users = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')

const quotaVisible = ref(false)
const quotaUser = ref(null)
const quotaGb = ref(0)

function statusType(s) {
  return s === 1 ? 'success' : s === -1 ? 'danger' : 'info'
}
function statusLabel(s) {
  return s === 1 ? '正常' : s === 0 ? '禁用' : '冻结'
}
function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return '-'
  if (bytes > 1073741824) return (bytes / 1073741824).toFixed(1) + ' GB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

async function loadUsers() {
  loading.value = true
  try {
    const res = await getUsers(page.value, size.value, keyword.value || undefined)
    users.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function updateStatus(row, status) {
  await updateUserStatus(row.id, status)
  ElMessage.success(status === 1 ? '已启用' : '已禁用')
  loadUsers()
}

function showQuotaDialog(row) {
  quotaUser.value = row
  quotaGb.value = Math.round(row.quotaBytes / 1073741824)
  quotaVisible.value = true
}

async function saveQuota() {
  await updateUserQuota(quotaUser.value.id, quotaGb.value * 1073741824)
  ElMessage.success('配额已更新')
  quotaVisible.value = false
  loadUsers()
}

onMounted(loadUsers)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
