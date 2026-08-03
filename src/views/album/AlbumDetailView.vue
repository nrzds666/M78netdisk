<template>
  <div class="album-detail-page">
    <!-- Top Bar -->
    <div class="detail-topbar">
      <el-button text :icon="ArrowLeft" @click="goBack" class="back-btn">
        返回相册列表
      </el-button>
      <div class="detail-topbar-info">
        <div class="detail-title-area">
          <h2 class="detail-title">{{ albumInfo?.name || '相册' }}</h2>
          <span class="detail-count-badge">{{ items.length }} 个文件</span>
        </div>
        <div class="detail-topbar-actions">
          <el-button size="small" @click="startSlideshow" :disabled="imageUrls.length === 0">
            <template #icon><el-icon><VideoCamera /></el-icon></template>
            幻灯片
          </el-button>
          <el-button size="small" @click="showShareDialog = true">
            分享
          </el-button>
          <el-button size="small" @click="handleAddItemsOpen">
            添加文件
          </el-button>
        </div>
      </div>
    </div>

    <!-- Image Grid -->
    <div class="detail-grid-wrap" v-loading="loading">
      <el-empty v-if="!loading && items.length === 0" description="相册中暂无文件，点击右上角添加" />

      <el-row :gutter="12" v-else>
        <el-col
          v-for="item in items"
          :key="item.itemId"
          :xs="12"
          :sm="8"
          :md="6"
          :lg="4"
          style="margin-bottom: 12px"
        >
          <el-card shadow="hover" :body-style="{ padding: '0' }" class="detail-item-card">
            <div class="detail-item-img">
              <!-- Image with preview-src-list for built-in gallery -->
              <el-image
                v-if="item.mimeType?.startsWith('image')"
                :src="item.thumbnailKey || getPreviewUrl(item.itemId)"
                fit="cover"
                class="detail-item-thumb"
                style="cursor:pointer"
                @click.stop="openPreview(item)"
              >
                <template #error>
                  <div class="detail-item-placeholder">
                    <el-icon :size="32" color="#c0c4cc"><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
              <div v-else class="detail-item-placeholder">
                <el-icon :size="32" color="#409eff"><VideoCamera /></el-icon>
              </div>
              <!-- Hover info overlay (方案 B) -->
              <div class="item-info-overlay" v-if="item.mimeType?.startsWith('image')">
                <div class="item-info-line">{{ item.name }}</div>
                <div class="item-info-line">{{ formatSize(item.size) }} · {{ getTypeLabel(item.mimeType) }}</div>
              </div>
            </div>
            <div class="detail-item-footer">
              <div class="detail-item-name" :title="item.name">{{ item.name }}</div>
              <el-button
                text
                type="danger"
                size="small"
                @click="handleRemoveItem(item)"
              >
                移除
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- Add Items Dialog -->
    <el-dialog
      v-model="showAddItemsDialog"
      title="从文件列表选择"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-select
        v-model="selectedItemIds"
        multiple
        filterable
        placeholder="搜索并选择文件"
        style="width:100%"
        :loading="loadingFiles"
      >
        <el-option
          v-for="f in availableFiles"
          :key="f.id"
          :label="f.name"
          :value="f.id"
          :disabled="f.isDirectory"
        >
          <span>{{ f.name }}</span>
          <span style="float:right;color:#909399;font-size:12px">{{ f.mimeType || '文件' }}</span>
        </el-option>
      </el-select>
      <template #footer>
        <el-button @click="showAddItemsDialog = false">取消</el-button>
        <el-button type="primary" :loading="addingItems" @click="confirmAddItems">
          添加到相册
        </el-button>
      </template>
    </el-dialog>

    <!-- Album Share Dialog -->
    <el-dialog
      v-model="showShareDialog"
      title="分享相册"
      width="420px"
      :close-on-click-modal="false"
      @close="resetShareDialog"
    >
      <template v-if="!shareResult">
        <p style="color:#606266;margin-bottom:16px">生成链接后，任何人可查看此相册内的文件</p>
        <el-form label-width="90px">
          <el-form-item label="过期时间">
            <el-select v-model="shareExpireDays" style="width:100%">
              <el-option label="1天后过期" :value="1" />
              <el-option label="7天后过期" :value="7" />
              <el-option label="30天后过期" :value="30" />
              <el-option label="永不过期" :value="0" />
            </el-select>
          </el-form-item>
        </el-form>
      </template>
      <template v-else>
        <el-alert type="success" :closable="false" show-icon>
          <template #title>分享链接已创建</template>
        </el-alert>
        <div style="margin-top:16px">
          <label style="font-size:13px;color:#606266">分享链接：</label>
          <div style="display:flex;gap:8px;margin-top:4px">
            <el-input :model-value="shareUrl" readonly>
              <template #append>
                <el-button @click="copyShareLink">复制</el-button>
              </template>
            </el-input>
          </div>
        </div>
      </template>
      <template #footer>
        <template v-if="!shareResult">
          <el-button @click="showShareDialog = false">取消</el-button>
          <el-button type="primary" :loading="shareCreating" @click="handleCreateShare">
            生成分享链接
          </el-button>
        </template>
        <template v-else>
          <el-button @click="showShareDialog = false">关闭</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- ═══ Slideshow Overlay (方案C) ═══ -->
    <Transition name="slideshow">
      <div v-if="slideshowVisible" class="slideshow-overlay" @click.self="exitSlideshow" tabindex="0"
        @keydown="handleSlideshowKeydown" ref="slideshowRef">
        <!-- Top bar -->
        <div class="slideshow-topbar">
          <span class="slideshow-counter">{{ slideshowIndex + 1 }} / {{ slideshowImages.length }}</span>
          <span class="slideshow-filename">{{ slideshowImages[slideshowIndex]?.name || '' }}</span>
          <div class="slideshow-top-controls">
            <el-button text size="small" @click="prevSlide" :disabled="slideshowImages.length <= 1"
              style="color:#fff">
              <template #icon><el-icon><ArrowLeft /></el-icon></template>
            </el-button>
            <el-button text size="small" @click="togglePlay" style="color:#fff">
              <template #icon><el-icon>{{ slideshowPlaying ? '⏸' : '▶' }}</el-icon></template>
              {{ slideshowPlaying ? '暂停' : '播放' }}
            </el-button>
            <el-button text size="small" @click="nextSlide" :disabled="slideshowImages.length <= 1"
              style="color:#fff">
              <template #icon><el-icon><ArrowRight /></el-icon></template>
            </el-button>
            <el-select v-model="slideshowSpeed" size="small" style="width:90px;margin-left:12px"
              @change="restartAutoPlay">
              <el-option label="1秒" :value="1000" />
              <el-option label="3秒" :value="3000" />
              <el-option label="5秒" :value="5000" />
              <el-option label="10秒" :value="10000" />
            </el-select>
            <el-button text size="small" @click="exitSlideshow" style="color:#fff;margin-left:8px">
              退出 (Esc)
            </el-button>
          </div>
        </div>

        <!-- Image display -->
        <div class="slideshow-body">
          <Transition name="fade" mode="out-in">
            <img
              :key="slideshowImages[slideshowIndex]?.itemId"
              :src="slideshowImages[slideshowIndex]?.thumbnailKey"
              class="slideshow-img"
              alt="slideshow"
            />
          </Transition>
        </div>

        <!-- Bottom progress bar -->
        <div class="slideshow-progress">
          <div class="slideshow-progress-bar" :style="{ width: slideshowProgress + '%' }"></div>
        </div>

        <!-- Thumbnail strip -->
        <div class="slideshow-thumbstrip">
          <div
            v-for="(img, idx) in slideshowImages"
            :key="img.itemId"
            class="slideshow-thumb"
            :class="{ active: idx === slideshowIndex }"
            @click="goToSlide(idx)"
          >
            <img :src="img.thumbnailKey" :alt="img.name" />
          </div>
        </div>
      </div>
    </Transition>
  </div>

  <!-- Preview Dialog -->
  <el-dialog v-model="showPreviewDialog" title="图片预览" width="70%" :close-on-click-modal="false" destroy-on-close>
    <div style="display:flex;justify-content:center;align-items:center;min-height:200px">
      <el-image :src="previewUrl" fit="contain" style="max-width:100%;max-height:70vh" :preview-src-list="[previewUrl]" />
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, VideoCamera, ArrowLeft } from '@element-plus/icons-vue'
import {
  getAlbumDetail, addAlbumItems, removeAlbumItems, createAlbumShare
} from '@/api/album'
import { listItems } from '@/api/file'
import { getToken } from '@/utils/auth'

const route = useRoute()
const router = useRouter()

const albumId = computed(() => Number(route.params.id))

const albumInfo = ref(null)
const items = ref([])
const loading = ref(false)

// ─── Preview (方案 A: 全屏画廊) ───
function getPreviewUrl(itemId) {
  return `/api/files/preview/${itemId}?token=${getToken()}`
}

const imageUrls = computed(() => {
  return items.value
    .filter(i => i.mimeType?.startsWith('image/'))
    .map(i => getPreviewUrl(i.itemId))
})

function imageIndex(itemId) {
  return imageUrls.value.findIndex((_, idx) => items.value[idx]?.itemId === itemId)
}

// ─── Preview Dialog ───
const showPreviewDialog = ref(false)
const previewUrl = ref('')

function openPreview(item) {
  previewUrl.value = getPreviewUrl(item.itemId)
  showPreviewDialog.value = true
}

// ─── Load ───
async function loadAlbum() {
  if (!albumId.value) return
  loading.value = true
  try {
    const res = await getAlbumDetail(albumId.value, 1, 200)
    albumInfo.value = res.data
    items.value = res.data?.items || []
  } catch {
    ElMessage.error('加载相册失败')
    router.push('/albums')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/albums')
}

// ─── Add Items ───
const showAddItemsDialog = ref(false)
const selectedItemIds = ref([])
const loadingFiles = ref(false)
const availableFiles = ref([])
const addingItems = ref(false)

async function handleAddItemsOpen() {
  showAddItemsDialog.value = true
  selectedItemIds.value = []
  loadingFiles.value = true
  try {
    const res = await listItems(null, 1, 200)
    availableFiles.value = (res.data?.records || []).filter(f => !f.isDirectory)
  } catch {
    availableFiles.value = []
  } finally {
    loadingFiles.value = false
  }
}

async function confirmAddItems() {
  if (!selectedItemIds.value.length) {
    ElMessage.warning('请选择要添加的文件')
    return
  }
  addingItems.value = true
  try {
    await addAlbumItems(albumId.value, selectedItemIds.value)
    ElMessage.success(`已添加 ${selectedItemIds.value.length} 个文件`)
    showAddItemsDialog.value = false
    selectedItemIds.value = []
    loadAlbum()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '添加失败')
  } finally {
    addingItems.value = false
  }
}

// ─── Remove Item ───
async function handleRemoveItem(item) {
  try {
    await ElMessageBox.confirm(
      `确定从相册移除「${item.name}」？`,
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    await removeAlbumItems(albumId.value, [item.itemId])
    ElMessage.success(`已移除「${item.name}」`)
    loadAlbum()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.msg || e.message || '移除失败')
    }
  }
}

// ─── Slideshow (方案 C) ───
const slideshowVisible = ref(false)
const slideshowIndex = ref(0)
const slideshowPlaying = ref(false)
const slideshowSpeed = ref(3000)
const slideshowProgress = ref(0)
const slideshowTimer = ref(null)
const slideshowRef = ref(null)

const slideshowImages = computed(() => {
  return items.value.filter(i => i.mimeType?.startsWith('image/'))
    .map(i => ({
      ...i,
      thumbnailKey: getPreviewUrl(i.itemId)
    }))
})

function startSlideshow() {
  if (slideshowImages.value.length === 0) {
    ElMessage.warning('相册中没有可播放的图片')
    return
  }
  slideshowVisible.value = true
  slideshowIndex.value = 0
  slideshowPlaying.value = true
  slideshowProgress.value = 0
  nextTick(() => {
    slideshowRef.value?.focus()
    startAutoPlay()
  })
}

function startAutoPlay() {
  stopAutoPlay()
  if (!slideshowPlaying.value) return
  const interval = 50 // update progress every 50ms
  const total = slideshowSpeed.value
  let elapsed = 0
  slideshowTimer.value = setInterval(() => {
    elapsed += interval
    slideshowProgress.value = Math.min(100, (elapsed / total) * 100)
    if (elapsed >= total) {
      nextSlide()
    }
  }, interval)
}

function stopAutoPlay() {
  if (slideshowTimer.value) {
    clearInterval(slideshowTimer.value)
    slideshowTimer.value = null
  }
}

function restartAutoPlay() {
  if (slideshowPlaying.value && slideshowVisible.value) {
    startAutoPlay()
  }
}

function togglePlay() {
  slideshowPlaying.value = !slideshowPlaying.value
  if (slideshowPlaying.value) {
    startAutoPlay()
  } else {
    stopAutoPlay()
  }
}

function nextSlide() {
  if (slideshowImages.value.length === 0) return
  slideshowIndex.value = (slideshowIndex.value + 1) % slideshowImages.value.length
  slideshowProgress.value = 0
  if (slideshowPlaying.value) startAutoPlay()
}

function prevSlide() {
  if (slideshowImages.value.length === 0) return
  slideshowIndex.value = (slideshowIndex.value - 1 + slideshowImages.value.length) % slideshowImages.value.length
  slideshowProgress.value = 0
  if (slideshowPlaying.value) startAutoPlay()
}

function goToSlide(idx) {
  slideshowIndex.value = idx
  slideshowProgress.value = 0
  if (slideshowPlaying.value) startAutoPlay()
}

function exitSlideshow() {
  slideshowVisible.value = false
  stopAutoPlay()
  slideshowPlaying.value = false
  slideshowProgress.value = 0
}

function handleSlideshowKeydown(e) {
  switch (e.key) {
    case 'Escape':
      e.preventDefault()
      exitSlideshow()
      break
    case 'ArrowLeft':
      e.preventDefault()
      prevSlide()
      break
    case 'ArrowRight':
      e.preventDefault()
      nextSlide()
      break
    case ' ':
      e.preventDefault()
      togglePlay()
      break
  }
}

// ─── Album Share (方案D) ───
const showShareDialog = ref(false)
const shareCreating = ref(false)
const shareResult = ref(null)
const shareExpireDays = ref(7)

const shareUrl = computed(() => {
  if (!shareResult.value?.shareToken) return ''
  return `${window.location.origin}/album-share/${shareResult.value.shareToken}`
})

async function handleCreateShare() {
  const albumIdVal = albumId.value
  if (!albumIdVal) return
  shareCreating.value = true
  shareResult.value = null
  try {
    const res = await createAlbumShare(albumIdVal, shareExpireDays.value > 0 ? shareExpireDays.value : null)
    shareResult.value = res.data
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '创建分享链接失败')
  } finally {
    shareCreating.value = false
  }
}

function copyShareLink() {
  const url = shareUrl.value
  if (!url) return
  navigator.clipboard.writeText(url).then(() => {
    ElMessage.success('分享链接已复制到剪贴板')
  }).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = url
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('分享链接已复制到剪贴板')
  })
}

function resetShareDialog() {
  shareResult.value = null
  shareExpireDays.value = 7
}

// ─── Helpers ───
function formatSize(bytes) {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function getTypeLabel(mimeType) {
  if (!mimeType) return '文件'
  if (mimeType.startsWith('image')) return '图片'
  if (mimeType.startsWith('video')) return '视频'
  return mimeType.split('/').pop() || '文件'
}

onMounted(loadAlbum)
</script>

<style scoped>
.album-detail-page {
  min-height: 400px;
}

.detail-topbar {
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.back-btn {
  align-self: flex-start;
}

.detail-topbar-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-title-area {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.detail-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}

.detail-count-badge {
  font-size: 13px;
  color: #909399;
}

.detail-grid-wrap {
  min-height: 300px;
}

.detail-item-card {
  border-radius: 8px;
  transition: transform 0.2s;
}

.detail-item-card:hover {
  transform: translateY(-2px);
}

.detail-item-img {
  height: 140px;
  overflow: hidden;
  background: #f5f7fa;
  cursor: pointer;
  position: relative;
}

.detail-item-thumb {
  width: 100%;
  height: 100%;
}

.detail-item-placeholder {
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.item-info-overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0,0,0,0.7));
  color: #fff;
  padding: 24px 8px 6px;
  font-size: 11px;
  opacity: 0;
  transition: opacity 0.2s;
  line-height: 1.5;
  pointer-events: none;
}

.detail-item-img:hover .item-info-overlay {
  opacity: 1;
}

.item-info-line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-item-footer {
  padding: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-item-name {
  font-size: 12px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

/* ═══ Slideshow Styles (方案C) ═══ */
.slideshow-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.95);
  display: flex;
  flex-direction: column;
  color: #fff;
  outline: none;
}

.slideshow-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: rgba(0,0,0,0.5);
  z-index: 10;
}

.slideshow-counter {
  font-size: 14px;
  color: #ccc;
  min-width: 60px;
}

.slideshow-filename {
  font-size: 14px;
  color: #eee;
  flex: 1;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin: 0 16px;
}

.slideshow-top-controls {
  display: flex;
  align-items: center;
  gap: 4px;
}

.slideshow-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 20px;
}

.slideshow-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
}

.slideshow-progress {
  height: 3px;
  background: rgba(255,255,255,0.15);
  flex-shrink: 0;
}

.slideshow-progress-bar {
  height: 100%;
  background: #409eff;
  transition: width 0.05s linear;
}

.slideshow-thumbstrip {
  display: flex;
  gap: 6px;
  padding: 12px 20px;
  overflow-x: auto;
  background: rgba(0,0,0,0.3);
  flex-shrink: 0;
  justify-content: center;
}

.slideshow-thumb {
  width: 48px;
  height: 48px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  opacity: 0.5;
  border: 2px solid transparent;
  transition: all 0.2s;
  flex-shrink: 0;
}

.slideshow-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.slideshow-thumb:hover {
  opacity: 0.8;
}

.slideshow-thumb.active {
  opacity: 1;
  border-color: #409eff;
}

/* Transitions */
.slideshow-enter-active,
.slideshow-leave-active {
  transition: opacity 0.3s ease;
}
.slideshow-enter-from,
.slideshow-leave-to {
  opacity: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
