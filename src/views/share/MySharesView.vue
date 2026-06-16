<template>
  <div class="shares-page">
    <!-- Header -->
    <el-card shadow="never" class="action-bar">
      <el-row :gutter="16" align="middle">
        <el-col :span="24">
          <span class="action-title" v-if="activeTab === 'mine'">我的分享</span>
          <span class="action-title" v-else>收到的分享</span>
          <span class="action-subtitle" v-if="total > 0">（共 {{ total }} 个）</span>
        </el-col>
      </el-row>
    </el-card>

    <!-- Tabs: My Shares / Received -->
    <el-card shadow="never" class="shares-tabs-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="我的分享" name="mine" />
        <el-tab-pane label="收到的分享" name="received" />
      </el-tabs>
    </el-card>

    <!-- Shares Table -->
    <el-card shadow="never" class="shares-table-card" v-loading="loading">
      <el-empty v-if="!loading && shares.length === 0" :description="emptyText" />
      <template v-else>
        <el-table :data="shares" style="width:100%" stripe>
          <el-table-column label="文件名" min-width="200">
            <template #default="{ row }">
              <span>{{ row.fileName }}</span>
            </template>
          </el-table-column>
          <el-table-column label="分享链接" min-width="200">
            <template #default="{ row }">
              <span class="share-token-text">{{ row.shareToken }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="过期时间" width="170">
            <template #default="{ row }">
              {{ row.expireAt ? formatDateTime(row.expireAt) : '永久' }}
            </template>
          </el-table-column>
          <el-table-column v-if="activeTab === 'mine'" label="下载次数" width="110" align="center">
            <template #default="{ row }">
              {{ row.downloadCount ?? 0 }} / {{ row.maxDownloads ?? '不限' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="320" fixed="right">
            <template #default="{ row }">
              <el-button text size="small" @click="copyShareLink(row)">复制链接</el-button>
              <el-button
                v-if="activeTab === 'mine' && getSharePassword(row.id)"
                text
                size="small"
                @click="copySharePassword(row)"
              >
                复制提取码
              </el-button>
              <el-button
                v-if="activeTab === 'mine'"
                text
                type="danger"
                size="small"
                @click="handleCancel(row)"
              >
                取消分享
              </el-button>
              <el-button
                v-if="activeTab === 'received'"
                text
                size="small"
                @click="openReceivedShare(row)"
              >
                查看
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap" v-if="total > pageSize">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="loadShares"
            small
          />
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyShares, cancelShare, listReceivedShares } from '@/api/share'

const router = useRouter()

const activeTab = ref('mine')
const shares = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const emptyText = computed(() => {
  return activeTab.value === 'mine' ? '暂无分享' : '暂未收到分享'
})

function formatDateTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const y = d.getFullYear()
  const mo = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${mo}-${da} ${h}:${mi}`
}

function handleTabChange() {
  page.value = 1
  shares.value = []
  loadShares()
}

async function loadShares() {
  loading.value = true
  try {
    const fn = activeTab.value === 'mine' ? listMyShares : listReceivedShares
    const res = await fn(page.value, pageSize.value)
    shares.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    shares.value = []
    total.value = 0
    ElMessage.error('加载分享列表失败')
  } finally {
    loading.value = false
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定要取消「${row.fileName}」的分享？取消后分享链接将失效。`,
      '确认取消',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await cancelShare(row.id)
    ElMessage.success(`已取消「${row.fileName}」的分享`)
    loadShares()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.msg || e.message || '取消分享失败')
    }
  }
}

function copyShareLink(row) {
  const url = `${window.location.origin}/share/${row.shareToken}`
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('分享链接已复制')
  }).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = url
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('分享链接已复制')
  })
}

function getSharePassword(shareId) {
  try {
    return sessionStorage.getItem(`share_pwd_${shareId}`) || ''
  } catch {
    return ''
  }
}

function copySharePassword(row) {
  const pwd = getSharePassword(row.id)
  if (!pwd) {
    ElMessage.info('提取码在分享创建时已展示，请参考创建记录')
    return
  }
  navigator.clipboard.writeText(pwd).then(() => {
    ElMessage.success('提取码已复制')
  }).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = pwd
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('提取码已复制')
  })
}

function openReceivedShare(row) {
  const token = row.shareToken || row.accessToken
  if (token) {
    const url = router.resolve({ name: 'ShareAccess', params: { token } })
    window.open(url.href, '_blank')
  }
}

onMounted(loadShares)
</script>

<style scoped>
.shares-page {
  min-height: 400px;
}

.action-bar {
  margin-bottom: 0;
  border-radius: 8px 8px 0 0;
}

.shares-tabs-card {
  border-radius: 0;
  border-top: none;
  margin-bottom: 16px;
}

.shares-table-card {
  border-radius: 8px;
  min-height: 300px;
}

.action-title {
  font-size: 16px;
  font-weight: 600;
}

.action-subtitle {
  font-size: 14px;
  color: #909399;
  margin-left: 8px;
}

.share-token-text {
  font-family: 'Courier New', Courier, monospace;
  font-size: 13px;
  color: #409eff;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}
</style>
