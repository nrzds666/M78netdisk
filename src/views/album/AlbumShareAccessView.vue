<template>
  <div class="album-share-page">
    <div v-if="loading" class="loading-wrap" v-loading="loading" />
    <template v-else-if="error">
      <el-empty :description="error">
        <template #extra>
          <el-button type="primary" @click="loadAlbum">重试</el-button>
        </template>
      </el-empty>
    </template>
    <template v-else-if="album">
      <!-- Top Bar -->
      <div class="share-topbar">
        <div class="share-topbar-info">
          <h2 class="share-title">{{ album.name }}</h2>
          <span class="share-count">{{ album.itemCount }} 个文件</span>
        </div>
      </div>

      <!-- Image Grid (read-only) -->
      <div class="share-grid-wrap">
        <el-empty v-if="album.items?.length === 0" description="相册中暂无文件" />

        <el-row :gutter="12" v-else>
          <el-col
            v-for="item in album.items"
            :key="item.itemId"
            :xs="12"
            :sm="8"
            :md="6"
            :lg="4"
            style="margin-bottom: 12px"
          >
            <el-card shadow="hover" :body-style="{ padding: '0' }" class="share-item-card">
              <div class="share-item-img">
                <el-image
                  v-if="item.mimeType?.startsWith('image')"
                  :src="item.thumbnailKey || getSharePreviewUrl(item.itemId)"
                  fit="cover"
                  class="share-item-thumb"
                  :preview-src-list="imageUrls"
                  :initial-index="imageIndex(item.itemId)"
                  hide-on-click-modal
                >
                  <template #error>
                    <div class="share-item-placeholder">
                      <el-icon :size="32" color="#c0c4cc"><Picture /></el-icon>
                    </div>
                  </template>
                </el-image>
                <div v-else class="share-item-placeholder">
                  <el-icon :size="32" color="#409eff"><VideoCamera /></el-icon>
                </div>
              </div>
              <div class="share-item-footer">
                <div class="share-item-name" :title="item.name">{{ item.name }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Picture, VideoCamera } from '@element-plus/icons-vue'
import { getSharedAlbum } from '@/api/album'

const route = useRoute()
const loading = ref(false)
const error = ref('')
const album = ref(null)

// Build preview URL using share token (public, no JWT required)
function getSharePreviewUrl(itemId) {
  const token = route.params.token
  if (!token) return ''
  return `/api/albums/share-access/${token}/preview/${itemId}`
}

const imageUrls = computed(() => {
  if (!album.value?.items) return []
  return album.value.items
    .filter(i => i.mimeType?.startsWith('image/'))
    .map(i => i.thumbnailKey || getSharePreviewUrl(i.itemId))
})

function imageIndex(itemId) {
  if (!album.value?.items) return -1
  return imageUrls.value.findIndex((_, idx) => album.value.items[idx]?.itemId === itemId)
}

async function loadAlbum() {
  const token = route.params.token
  if (!token) {
    error.value = '缺少分享标识'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const res = await getSharedAlbum(token)
    album.value = res.data
  } catch (e) {
    if (e.response?.status === 404 || e.response?.data?.msg?.includes('不存在') || e.response?.data?.msg?.includes('过期')) {
      error.value = '分享链接不存在或已过期'
    } else {
      error.value = '加载相册失败: ' + (e.response?.data?.msg || e.message || '未知错误')
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadAlbum)
</script>

<style scoped>
.album-share-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24px;
}

.loading-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.share-topbar {
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
}

.share-topbar-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.share-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.share-count {
  font-size: 13px;
  color: #909399;
}

.share-grid-wrap {
  min-height: 300px;
}

.share-item-card {
  border-radius: 8px;
  transition: transform 0.2s;
}

.share-item-card:hover {
  transform: translateY(-2px);
}

.share-item-img {
  height: 140px;
  overflow: hidden;
  background: #f5f7fa;
  cursor: pointer;
  position: relative;
}

.share-item-thumb {
  width: 100%;
  height: 100%;
}

.share-item-placeholder {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.share-item-footer {
  padding: 8px;
}

.share-item-name {
  font-size: 12px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
