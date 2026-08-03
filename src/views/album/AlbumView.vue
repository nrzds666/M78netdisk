<template>
  <div class="album-page">
    <!-- Action Bar -->
    <el-card shadow="never" class="action-bar">
      <el-row :gutter="16" align="middle">
        <el-col :span="12">
          <span class="action-title">相册</span>
          <span class="action-subtitle" v-if="total > 0">（共 {{ total }} 个）</span>
        </el-col>
        <el-col :span="12" style="text-align:right">
          <el-button type="primary" @click="showCreateDialog = true" size="default">
            新建相册
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- Album Grid -->
    <el-card shadow="never" class="album-table-card" v-loading="loading">
      <el-empty v-if="!loading && albums.length === 0" description="暂无相册，点击上方按钮新建" />
      <template v-else>
        <el-row :gutter="16">
          <el-col
            v-for="album in albums"
            :key="album.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
            style="margin-bottom: 16px"
          >
            <el-card
              shadow="hover"
              class="album-card"
              :body-style="{ padding: '0' }"
              @dblclick="enterAlbum(album)"
            >
              <!-- Cover — click to change -->
              <div class="album-cover" @click.stop="openCoverPicker(album)">
                <el-image
                  v-if="album.coverThumbnailKey || album.coverItemId"
                  :src="album.coverThumbnailKey || `/api/files/preview/${album.coverItemId}?token=${getToken()}`"
                  fit="cover"
                  class="cover-image"
                >
                  <template #error>
                    <div class="cover-placeholder">
                      <el-icon :size="48" color="#c0c4cc"><Picture /></el-icon>
                    </div>
                  </template>
                </el-image>
                <div v-else class="cover-placeholder">
                  <el-icon :size="48" color="#c0c4cc"><Picture /></el-icon>
                  <div class="cover-overlay">
                    <el-icon :size="24" color="#fff"><Edit /></el-icon>
                    <span>更换封面</span>
                  </div>
                </div>
                <div class="cover-overlay" v-if="album.coverThumbnailKey">
                  <el-icon :size="24" color="#fff"><Edit /></el-icon>
                  <span>更换封面</span>
                </div>
              </div>
              <!-- Info -->
              <div class="album-info">
                <!-- Inline rename: click name to edit -->
                <div v-if="editingAlbumId === album.id" class="album-name-editing" @click.stop>
                  <el-input
                    v-model="editName"
                    ref="renameInputRef"
                    size="small"
                    maxlength="128"
                    @keyup.enter="confirmRename(album)"
                    @blur="confirmRename(album)"
                  />
                  <el-button text size="small" @click.stop="cancelRename">取消</el-button>
                </div>
                <div
                  v-else
                  class="album-name"
                  :title="album.name"
                  @click.stop="startRename(album)"
                >
                  {{ album.name }}
                </div>
                <div class="album-meta">
                  <span>{{ album.itemCount ?? 0 }} 个文件</span>
                  <span class="album-date">{{ formatDateTime(album.createdAt) }}</span>
                </div>
                <div class="album-actions">
                  <el-button text type="danger" size="small" @click.stop="handleDelete(album)">
                    删除
                  </el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <div class="pagination-wrap" v-if="total > pageSize">
          <el-pagination
            v-model:current-page="page"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="loadAlbums"
            small
          />
        </div>
      </template>
    </el-card>

    <!-- Create Album Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="新建相册"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="0">
        <el-form-item prop="name">
          <el-input
            v-model="createForm.name"
            placeholder="请输入相册名称"
            maxlength="128"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- Cover Picker Dialog -->
    <el-dialog
      v-model="showCoverPicker"
      :title="'更换封面 — ' + (coverPickerAlbum?.name || '')"
      width="520px"
      :close-on-click-modal="false"
      @open="loadCoverItems"
    >
      <div class="cover-preview">
        <div class="cover-preview-label">当前选中预览：</div>
        <div class="cover-preview-img">
          <el-image
            v-if="selectedCoverUrl"
            :src="selectedCoverUrl"
            fit="contain"
            style="max-height:160px;width:100%"
          />
          <div v-else class="cover-preview-empty">
            <el-icon :size="40" color="#c0c4cc"><Picture /></el-icon>
            <span>请从下方选择封面图片</span>
          </div>
        </div>
      </div>

      <el-divider />

      <div class="cover-items-label">相册图片（点击选择）：</div>
      <div v-loading="coverItemsLoading" class="cover-items-grid">
        <el-empty v-if="!coverItemsLoading && coverItems.length === 0" description="相册中暂无图片" />
        <div
          v-for="item in coverItems"
          :key="item.itemId"
          class="cover-item-thumb"
          :class="{ 'cover-item-selected': selectedCoverId === item.itemId }"
          @click="selectCoverItem(item)"
        >
          <el-image
            :src="item.thumbnailKey || `/api/files/preview/${item.itemId}?token=${getToken()}`"
            fit="cover"
            class="cover-thumb-img"
          >
            <template #error>
              <div class="cover-thumb-placeholder">
                <el-icon :size="24" color="#c0c4cc"><Picture /></el-icon>
              </div>
            </template>
          </el-image>
          <div class="cover-item-check" v-if="selectedCoverId === item.itemId">
            <el-icon :size="18" color="#fff"><Check /></el-icon>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="closeCoverPicker">取消</el-button>
        <el-button type="primary" :loading="coverSaving" :disabled="!selectedCoverId" @click="saveCover">
          保存封面
        </el-button>
      </template>
    </el-dialog>

    <!-- Album Detail Dialog (kept for backward compat, replaced by AlbumDetailView) -->
    <el-dialog
      v-model="showDetailDialog"
      :title="detailAlbum?.name || '相册详情'"
      width="700px"
      :close-on-click-modal="false"
      @open="loadDetailItems"
    >
      <div class="album-detail-header">
        <span class="detail-count">{{ detailItems.length }} 个文件</span>
        <el-button type="primary" size="small" @click="handleAddItemsOpen">
          添加文件
        </el-button>
      </div>

      <el-empty v-if="detailItems.length === 0" description="相册中暂无文件" />

      <el-row :gutter="12" v-else>
        <el-col
          v-for="item in detailItems"
          :key="item.itemId"
          :xs="12"
          :sm="8"
          :md="6"
          style="margin-bottom: 12px"
        >
          <el-card shadow="hover" :body-style="{ padding: '8px' }" class="detail-item-card">
            <el-image
              v-if="item.mimeType?.startsWith('image')"
              :src="item.thumbnailKey || ''"
              fit="cover"
              class="detail-thumb"
            >
              <template #error>
                <div class="detail-placeholder">
                  <el-icon :size="32" color="#c0c4cc"><Picture /></el-icon>
                </div>
              </template>
            </el-image>
            <div v-else class="detail-placeholder">
              <el-icon :size="32" color="#409eff"><VideoCamera /></el-icon>
            </div>
            <div class="detail-item-name" :title="item.name">{{ item.name }}</div>
            <div class="detail-item-actions">
              <el-button text type="danger" size="small" @click="handleRemoveItem(item)">
                移除
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-dialog>

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
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, VideoCamera, Edit, Check } from '@element-plus/icons-vue'
import {
  listAlbums, createAlbum, deleteAlbum, getAlbumDetail,
  addAlbumItems, removeAlbumItems, updateAlbum, setAlbumCover
} from '@/api/album'
import { listItems } from '@/api/file'
import { getToken } from '@/utils/auth'

const router = useRouter()

const albums = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

// ─── Create Album ───
const showCreateDialog = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = ref({ name: '' })
const createRules = {
  name: [
    { required: true, message: '请输入相册名称', trigger: 'blur' },
    { max: 128, message: '名称最长128个字符', trigger: 'blur' }
  ]
}

// ─── Inline Rename ───
const editingAlbumId = ref(null)
const editName = ref('')
const renameInputRef = ref(null)

function startRename(album) {
  editingAlbumId.value = album.id
  editName.value = album.name
  nextTick(() => {
    renameInputRef.value?.focus()
  })
}

function cancelRename() {
  editingAlbumId.value = null
  editName.value = ''
}

async function confirmRename(album) {
  const name = editName.value?.trim()
  if (!name) {
    cancelRename()
    return
  }
  if (name === album.name) {
    cancelRename()
    return
  }
  try {
    await updateAlbum(album.id, { name })
    ElMessage.success('相册已重命名')
    editingAlbumId.value = null
    album.name = name
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '重命名失败')
  }
}

// ─── Cover Picker ───
const showCoverPicker = ref(false)
const coverPickerAlbum = ref(null)
const coverItems = ref([])
const coverItemsLoading = ref(false)
const selectedCoverId = ref(null)
const selectedCoverUrl = ref('')
const coverSaving = ref(false)

function openCoverPicker(album) {
  coverPickerAlbum.value = album
  selectedCoverId.value = null
  selectedCoverUrl.value = ''
  showCoverPicker.value = true
}

function closeCoverPicker() {
  showCoverPicker.value = false
  coverPickerAlbum.value = null
  coverItems.value = []
  selectedCoverId.value = null
  selectedCoverUrl.value = ''
}

async function loadCoverItems() {
  if (!coverPickerAlbum.value) return
  coverItemsLoading.value = true
  try {
    const res = await getAlbumDetail(coverPickerAlbum.value.id, 1, 200)
    // Only show image-type items
    coverItems.value = (res.data?.items || []).filter(i => i.mimeType?.startsWith('image/'))
  } catch {
    coverItems.value = []
  } finally {
    coverItemsLoading.value = false
  }
}

function selectCoverItem(item) {
  selectedCoverId.value = item.itemId
  selectedCoverUrl.value = item.thumbnailKey || ''
}

async function saveCover() {
  if (!selectedCoverId.value || !coverPickerAlbum.value) return
  coverSaving.value = true
  try {
    const res = await setAlbumCover(coverPickerAlbum.value.id, selectedCoverId.value)
    ElMessage.success('封面已更新')
    // Update the local album object's thumbnail
    const updated = res.data
    const localAlbum = albums.value.find(a => a.id === coverPickerAlbum.value.id)
    if (localAlbum && updated) {
      localAlbum.coverThumbnailKey = updated.coverThumbnailKey
    }
    closeCoverPicker()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '设置封面失败')
  } finally {
    coverSaving.value = false
  }
}

// ─── Enter Album (double-click) ───
function enterAlbum(album) {
  router.push(`/albums/${album.id}`)
}

// ─── Detail Dialog ───
const showDetailDialog = ref(false)
const detailAlbum = ref(null)
const detailItems = ref([])
const detailPage = ref(1)

// ─── Add Items Dialog ───
const showAddItemsDialog = ref(false)
const selectedItemIds = ref([])
const loadingFiles = ref(false)
const availableFiles = ref([])
const addingItems = ref(false)

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

async function loadAlbums() {
  loading.value = true
  try {
    const res = await listAlbums(page.value, pageSize.value)
    albums.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    albums.value = []
    total.value = 0
    ElMessage.error('加载相册列表失败')
  } finally {
    loading.value = false
  }
}

async function handleDelete(album) {
  try {
    await ElMessageBox.confirm(
      `确定要删除相册「${album.name}」？删除后相册中的文件不会被删除，但相册关系将丢失。`,
      '确认删除',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteAlbum(album.id)
    ElMessage.success(`已删除相册「${album.name}」`)
    loadAlbums()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.msg || e.message || '删除失败')
    }
  }
}

async function handleCreate() {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const res = await createAlbum({ name: createForm.value.name })
    ElMessage.success(`相册「${res.data?.name || createForm.value.name}」创建成功`)
    showCreateDialog.value = false
    createForm.value.name = ''
    createFormRef.value.resetFields()
    page.value = 1
    loadAlbums()
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || e.message || '创建相册失败')
  } finally {
    creating.value = false
  }
}

// ─── Detail ───
async function openAlbumDetail(album) {
  detailAlbum.value = album
  detailPage.value = 1
  showDetailDialog.value = true
}

async function loadDetailItems() {
  if (!detailAlbum.value) return
  try {
    const res = await getAlbumDetail(detailAlbum.value.id, detailPage.value, 100)
    detailItems.value = res.data?.items || []
  } catch {
    detailItems.value = []
  }
}

// ─── Add Items ───
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
    await addAlbumItems(detailAlbum.value.id, selectedItemIds.value)
    ElMessage.success(`已添加 ${selectedItemIds.value.length} 个文件`)
    showAddItemsDialog.value = false
    selectedItemIds.value = []
    loadDetailItems()
    loadAlbums()
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
    await removeAlbumItems(detailAlbum.value.id, [item.itemId])
    ElMessage.success(`已移除「${item.name}」`)
    loadDetailItems()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.msg || e.message || '移除失败')
    }
  }
}

onMounted(loadAlbums)
</script>

<style scoped>
.album-page {
  min-height: 400px;
}

.action-bar {
  margin-bottom: 16px;
  border-radius: 8px;
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

.album-table-card {
  border-radius: 8px;
  min-height: 300px;
}

.album-card {
  border-radius: 12px;
  cursor: pointer;
  transition: transform 0.2s, border-color 0.2s;
  border: 1px solid #e4e7ed;
}

.album-card:hover {
  transform: translateY(-2px);
  border-color: #409eff;
}

.album-cover {
  height: 140px;
  overflow: hidden;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.cover-image {
  width: 100%;
  height: 100%;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: #f5f7fa;
}

.cover-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.45);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.album-cover:hover .cover-overlay {
  opacity: 1;
}

.album-info {
  padding: 12px;
}

.album-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
  cursor: text;
  padding: 2px 4px;
  border-radius: 4px;
  transition: background 0.15s;
}

.album-name:hover {
  background: #f0f5ff;
}

.album-name-editing {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}

.album-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.album-date {
  flex-shrink: 0;
}

.album-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  border-top: 1px solid #f0f0f0;
  padding-top: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}

/* ─── Cover Picker ─── */
.cover-preview {
  margin-bottom: 8px;
}

.cover-preview-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.cover-preview-img {
  background: #f5f7fa;
  border-radius: 8px;
  min-height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.cover-preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
  color: #c0c4cc;
  font-size: 13px;
}

.cover-items-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.cover-items-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 60px;
}

.cover-item-thumb {
  width: 80px;
  height: 80px;
  border-radius: 6px;
  overflow: hidden;
  cursor: pointer;
  border: 2px solid transparent;
  position: relative;
  transition: border-color 0.2s;
}

.cover-item-thumb:hover {
  border-color: #409eff;
}

.cover-item-selected {
  border-color: #409eff;
}

.cover-thumb-img {
  width: 100%;
  height: 100%;
}

.cover-thumb-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.cover-item-check {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  background: #409eff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ─── Detail Dialog ─── */
.album-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.detail-count {
  font-size: 14px;
  color: #909399;
}

.detail-item-card {
  border-radius: 6px;
}

.detail-thumb {
  width: 100%;
  height: 100px;
  border-radius: 4px;
}

.detail-placeholder {
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 4px;
}

.detail-item-name {
  font-size: 12px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 4px;
}

.detail-item-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 2px;
}
</style>
