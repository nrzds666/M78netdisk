<template>
  <div class="admin-nodes">
    <div class="page-header">
      <h3 style="margin: 0; color: #303133;">存储节点管理</h3>
      <el-button type="primary" @click="openDialog(null)">新增节点</el-button>
    </div>

    <el-table :data="nodes" v-loading="loading" stripe style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="provider" label="提供商" width="100" />
      <el-table-column prop="weight" label="权重" width="80" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
            {{ row.isActive ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ row.createdAt }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确定删除此节点？" @confirm="handleDelete(row.id)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑节点' : '新增节点'" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="提供商">
          <el-select v-model="form.provider" style="width: 100%">
            <el-option label="阿里云 OSS" value="oss" />
            <el-option label="本地存储" value="local" />
          </el-select>
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="form.weight" :min="1" :max="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.isActive" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveNode" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getNodes, createNode, updateNode, deleteNode } from '@/api/admin'

const nodes = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({ name: '', provider: 'oss', weight: 100, isActive: true })

async function loadNodes() {
  loading.value = true
  try {
    const res = await getNodes()
    nodes.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, { id: row.id, name: row.name, provider: row.provider, weight: row.weight, isActive: !!row.isActive })
  } else {
    Object.assign(form, { id: null, name: '', provider: 'oss', weight: 100, isActive: true })
  }
  dialogVisible.value = true
}

async function saveNode() {
  saving.value = true
  try {
    if (form.id) {
      await updateNode(form.id, { name: form.name, provider: form.provider, weight: form.weight, isActive: form.isActive ? 1 : 0 })
    } else {
      await createNode({ name: form.name, provider: form.provider, weight: form.weight, isActive: form.isActive ? 1 : 0 })
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadNodes()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id) {
  await deleteNode(id)
  ElMessage.success('已删除')
  loadNodes()
}

onMounted(loadNodes)
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
