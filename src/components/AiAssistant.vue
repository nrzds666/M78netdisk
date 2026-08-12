<template>
  <div>
    <!-- ========== AI 助手图标 ========== -->
    <el-tooltip content="AI助手" placement="left" :disabled="state === 'dialog' || state === 'hidden'">
      <div
        ref="iconRef"
        class="ai-icon-wrapper"
        :class="{ 'is-hidden': state === 'hidden', 'is-dragging': dragging }"
        :style="iconStyle"
        @mousedown.prevent="onIconMouseDown"
        @click.stop="onIconClick"
      >
        <img :src="aiIcon" alt="AI助手" class="ai-icon-img" draggable="false" />
      </div>
    </el-tooltip>

    <!-- ========== 对话框 ========== -->
    <transition name="ai-slide">
      <div
        v-if="state === 'dialog'"
        ref="dialogRef"
        class="ai-panel"
        :style="dialogStyle"
      >
        <!-- 可拖拽标题栏 -->
        <div class="ai-header" @mousedown.prevent="onDialogMouseDown">
          <span class="ai-header-title">
            <el-icon :size="18"><ChatDotRound /></el-icon>
            AI 助手
          </span>
          <el-button text size="small" @click.stop="closeDialog">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>


        <!-- 消息列表 -->
        <div class="ai-messages" ref="aiMessageListRef">
          <div v-if="aiMessages.length === 0" class="ai-empty">
            <el-empty :image-size="48" description="开始和 AI 对话吧" />
          </div>


          <div
            v-for="(msg, idx) in aiMessages"
            :key="idx"
            class="ai-msg"
            :class="msg.role === 'user' ? 'ai-msg-user' : 'ai-msg-ai'"
          >
            <div class="ai-msg-avatar">
              <el-avatar :size="28" :icon="msg.role === 'user' ? 'UserFilled' : 'ChatDotRound'" :style="msg.role === 'user' ? { background: '#409eff' } : { background: '#67c23a' }" />
            </div>
            <div class="ai-msg-bubble">
              <div v-if="msg.ragActive" class="ai-rag-badge">
                <el-icon><Search /></el-icon>
                <span>已检索知识库</span>
              </div>
              <div class="ai-msg-text">{{ msg.content }}</div>
              <span v-if="msg.content === '' && aiLoading" class="ai-cursor">|</span>
              <div v-if="msg.error" class="ai-msg-error">发送失败</div>

              <!-- 文档卡片：LLM 自动生成的文档预览 -->
              <div v-if="msg.docCards && msg.docCards.length > 0" class="ai-doc-cards">
                <div v-for="doc in msg.docCards" :key="doc.tempFileId" class="ai-doc-card"
                     :class="{ 'ai-doc-card-warn': doc.round >= 5 }">
                  <div v-if="doc.round >= 5" class="ai-doc-warn">
                    ⚠️ 已修改 5 次，请确认最终版本
                  </div>
                  <div class="ai-doc-info">
                    <el-icon :size="18"><Document /></el-icon>
                    <span class="ai-doc-name">{{ doc.fileName }}</span>
                    <span class="ai-doc-size">{{ formatSize(doc.fileSize) }}</span>
                    <el-tag v-if="doc.round > 0" size="small" type="info">v{{ doc.round + 1 }}</el-tag>
                    <el-tag v-if="doc.status === 'saved'" size="small" type="success">已保存</el-tag>
                  </div>
                  <div class="ai-doc-actions">
                    <el-button size="small" text @click="previewTempDoc(doc)">预览</el-button>
                    <el-button v-if="doc.status === 'pending'" size="small" type="primary"
                      @click="saveTempDoc(msg, doc)">保存到网盘</el-button>
                    <el-button size="small" text @click="downloadTempDoc(doc)">下载到本地</el-button>
                  </div>
                </div>
              </div>

              <!-- 文件卡片：保存成功后显示 -->
              <div v-if="msg.fileRef" class="ai-file-card" @click="openFilePreview(msg.fileRef.fileId)">
                <el-icon :size="18"><Document /></el-icon>
                <span class="ai-file-name">{{ msg.fileRef.fileName }}</span>
                <span class="ai-file-size">{{ formatSize(msg.fileRef.fileSize) }}</span>
                <el-button text size="small" class="ai-file-open">打开</el-button>
              </div>

              <!-- 图片卡片：AI 生成图片展示 -->
              <div v-if="msg.imageRef" class="ai-image-card-msg">
                <el-image
                  :src="msg.imageRef.fileUrl"
                  :preview-src-list="[msg.imageRef.fileUrl]"
                  fit="contain"
                  style="width: 100%; max-height: 280px; border-radius: 8px; cursor: pointer;"
                >
                  <template #placeholder>
                    <div class="ai-image-placeholder">
                      <el-icon :size="48"><Document /></el-icon>
                    </div>
                  </template>
                </el-image>
                <div class="ai-image-msg-info">
                  <span class="ai-image-msg-name">{{ msg.imageRef.fileName }}</span>
                  <span class="ai-image-msg-size">{{ formatSize(msg.imageRef.fileSize) }}</span>
                  <el-button size="mini" text @click.stop="downloadImage(msg.imageRef)">下载</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="ai-input-area">
          <!-- 普通聊天输入框 -->
          <div class="ai-chat-input">
            <el-popover
              v-model:visible="filePickerVisible"
              placement="top"
              width="300"
              trigger="click"
            >
              <template #reference>
                <el-button :disabled="aiLoading" circle size="small" title="选择文件">
                  <el-icon :size="16"><Upload /></el-icon>
                </el-button>
              </template>
              <div class="ai-file-picker">
                <el-input
                  v-model="filePickerQuery"
                  size="small"
                  placeholder="搜索文件..."
                  :prefix-icon="Search"
                  clearable
                />
                <div v-if="filePickerResults.length === 0 && !filePickerLoading" class="ai-picker-empty">
                  暂无搜索结果
                </div>
                <div
                  v-for="f in filePickerResults"
                  :key="f.fileId"
                  class="ai-picker-item"
                  @click="selectPickerFile(f)"
                >
                  <el-icon><Document /></el-icon>
                  <span class="ai-picker-name">{{ f.fileName }}</span>
                  <span class="ai-picker-size">{{ formatSize(f.fileSize) }}</span>
                </div>
              </div>
            </el-popover>
            <el-input
              v-model="aiInput"
              type="textarea"
              :rows="2"
              :placeholder="filePickerTarget ? '按 Enter 附加 @' + filePickerTarget : '输入消息...'"
              :disabled="aiLoading"
              @keydown.enter.prevent="handleAiSend"
            />
            <el-button type="primary" :loading="aiLoading" @click="handleAiSend" class="ai-send-btn">
              发送
            </el-button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { ChatDotRound, Close, Search, UserFilled, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { streamChatMessage, saveDocument, confirmTempDocument } from '@/api/chat-stream'
import { searchFiles } from '@/api/chat.js'
import aiIcon from '@/assets/ai-assistant.png'

const ICON_SIZE = 48
const EDGE_THRESHOLD = 60    // 距边缘多少 px 判定半隐藏
const HIDDEN_VISIBLE = 22    // 半隐藏时露出多少 px
const CLICK_THRESHOLD = 5    // 移动 < 5px 视为点击而非拖拽
const EDGE_IDLE_DELAY = 5000 // 边缘停留 5 秒后半隐藏
const PANEL_W = 380
const PANEL_H = 500
let edgeTimer = null   // 边缘停留定时器（用 let 因为会被重新赋值）

// ─── 状态 ───
const state = ref('visible')   // 'visible' | 'hidden' | 'dialog'
const iconRef = ref(null)
const dialogRef = ref(null)

// 图标位置 (position: fixed)
const iconX = ref(window.innerWidth - ICON_SIZE - 24)
const iconY = ref(window.innerHeight - ICON_SIZE - 80)

// 对话框位置
const dialogX = ref(0)
const dialogY = ref(0)

// 半隐藏前的位置（恢复用）
const fullX = ref(iconX.value)
const fullY = ref(iconY.value)

// 拖拽状态
const dragging = ref(false)
const dragStart = reactive({ x: 0, y: 0, ix: 0, iy: 0 })
let wasDrag = false               // 本轮是否发生了拖拽（区分点击）
const draggedDialog = ref(false)  // 对话框是否被拖拽过

// ─── 计算样式 ───
const iconStyle = computed(() => ({
  left: iconX.value + 'px',
  top: iconY.value + 'px'
}))

const dialogStyle = computed(() => ({
  left: dialogX.value + 'px',
  top: dialogY.value + 'px'
}))

// ─── 图标拖拽 ───
function onIconMouseDown(e) {
  if (state.value === 'dialog') return
  clearTimeout(edgeTimer)
  wasDrag = false
  dragging.value = true
  dragStart.x = e.clientX
  dragStart.y = e.clientY
  dragStart.ix = iconX.value
  dragStart.iy = iconY.value

  document.addEventListener('mousemove', onIconMouseMove)
  document.addEventListener('mouseup', onIconMouseUp)
}

function onIconMouseMove(e) {
  const dx = e.clientX - dragStart.x
  const dy = e.clientY - dragStart.y
  const moved = Math.abs(dx) + Math.abs(dy)

  if (moved > CLICK_THRESHOLD) {
    wasDrag = true
  }

  if (wasDrag && state.value === 'hidden') {
    iconX.value = fullX.value
    iconY.value = fullY.value
    state.value = 'visible'
    dragStart.ix = iconX.value
    dragStart.iy = iconY.value
    dragStart.x = e.clientX
    dragStart.y = e.clientY
    return
  }

  let nx = dragStart.ix + dx
  let ny = dragStart.iy + dy
  nx = Math.max(0, Math.min(nx, window.innerWidth - ICON_SIZE))
  ny = Math.max(0, Math.min(ny, window.innerHeight - ICON_SIZE))
  iconX.value = nx
  iconY.value = ny
}

function onIconMouseUp() {
  document.removeEventListener('mousemove', onIconMouseMove)
  document.removeEventListener('mouseup', onIconMouseUp)
  dragging.value = false

  if (wasDrag) {
    startEdgeTimer()
  }
}

function onIconClick() {
  if (wasDrag) return
  clearTimeout(edgeTimer)
  if (state.value === 'hidden') {
    iconX.value = fullX.value
    iconY.value = fullY.value
    state.value = 'visible'
  } else if (state.value === 'visible') {
    openDialog()
  }
}

function startEdgeTimer() {
  clearTimeout(edgeTimer)

  const cx = iconX.value + ICON_SIZE / 2
  const cy = iconY.value + ICON_SIZE / 2

  fullX.value = iconX.value
  fullY.value = iconY.value

  const nearLeft   = cx < EDGE_THRESHOLD
  const nearRight  = cx > window.innerWidth - EDGE_THRESHOLD
  const nearTop    = cy < EDGE_THRESHOLD
  const nearBottom = cy > window.innerHeight - EDGE_THRESHOLD

  if (!nearLeft && !nearRight && !nearTop && !nearBottom) {
    state.value = 'visible'
    return
  }

  edgeTimer = setTimeout(() => {
    if (nearLeft) {
      iconX.value = -(ICON_SIZE - HIDDEN_VISIBLE)
    } else if (nearRight) {
      iconX.value = window.innerWidth - HIDDEN_VISIBLE
    }
    if (nearTop) {
      iconY.value = -(ICON_SIZE - HIDDEN_VISIBLE)
    } else if (nearBottom) {
      iconY.value = window.innerHeight - HIDDEN_VISIBLE
    }
    state.value = 'hidden'
  }, EDGE_IDLE_DELAY)
}

// ─── 对话框定位 ───
function openDialog() {
  const cx = iconX.value + ICON_SIZE / 2
  const cy = iconY.value + ICON_SIZE / 2

  let dx = cx + 16
  if (cx > window.innerWidth / 2) {
    dx = cx - PANEL_W - 16
  }

  let dy = cy - PANEL_H - 8
  if (cy <= window.innerHeight / 2 || dy < 0) {
    dy = cy + ICON_SIZE + 8
  }

  dx = Math.max(0, Math.min(dx, window.innerWidth - PANEL_W))
  dy = Math.max(0, Math.min(dy, window.innerHeight - PANEL_H))

  dialogX.value = dx
  dialogY.value = dy
  draggedDialog.value = false

  if (aiMessages.value.length === 0) {
    const greeting = getTimeGreeting()
    aiMessages.value.push({
      role: 'ai',
      content: greeting,
      ragActive: false
    })
  }

  state.value = 'dialog'
}

function closeDialog() {
  if (draggedDialog.value) {
    iconX.value = Math.max(0, Math.min(dialogX.value - 16, window.innerWidth - ICON_SIZE))
    iconY.value = Math.max(0, Math.min(dialogY.value + PANEL_H / 2, window.innerHeight - ICON_SIZE))
  }
  iconX.value = Math.max(0, Math.min(iconX.value, window.innerWidth - ICON_SIZE))
  iconY.value = Math.max(0, Math.min(iconY.value, window.innerHeight - ICON_SIZE))
  fullX.value = iconX.value
  fullY.value = iconY.value
  state.value = 'visible'
}

// ─── 对话框拖拽 ───
function onDialogMouseDown(e) {
  const sx = e.clientX
  const sy = e.clientY
  const dx = dialogX.value
  const dy = dialogY.value

  function onMove(ev) {
    const mx = ev.clientX - sx
    const my = ev.clientY - sy
    if (Math.abs(mx) + Math.abs(my) > CLICK_THRESHOLD) {
      draggedDialog.value = true
    }
    dialogX.value = Math.max(0, Math.min(dx + mx, window.innerWidth - PANEL_W))
    dialogY.value = Math.max(0, Math.min(dy + my, window.innerHeight - PANEL_H))
  }

  function onUp() {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

// ─── 窗口 resize ───
function onResize() {
  if (state.value === 'hidden') {
    iconX.value = Math.max(-ICON_SIZE + HIDDEN_VISIBLE, Math.min(iconX.value, window.innerWidth - HIDDEN_VISIBLE))
    iconY.value = Math.max(-ICON_SIZE + HIDDEN_VISIBLE, Math.min(iconY.value, window.innerHeight - HIDDEN_VISIBLE))
  } else {
    iconX.value = Math.max(0, Math.min(iconX.value, window.innerWidth - ICON_SIZE))
    iconY.value = Math.max(0, Math.min(iconY.value, window.innerHeight - ICON_SIZE))
  }
  fullX.value = Math.max(0, Math.min(fullX.value, window.innerWidth - ICON_SIZE))
  fullY.value = Math.max(0, Math.min(fullY.value, window.innerHeight - ICON_SIZE))
  dialogX.value = Math.max(0, Math.min(dialogX.value, window.innerWidth - PANEL_W))
  dialogY.value = Math.max(0, Math.min(dialogY.value, window.innerHeight - PANEL_H))
}

onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => {
  window.removeEventListener('resize', onResize)
  document.removeEventListener('mousemove', onIconMouseMove)
  document.removeEventListener('mouseup', onIconMouseUp)
})

// ─── AI 对话逻辑 ───
const aiMessages = ref([])
const aiInput = ref('')
const aiLoading = ref(false)
const aiMessageListRef = ref(null)
const aiAbortController = ref(null)

watch(() => state.value, (val) => {
  if (val === 'dialog') nextTick(() => scrollToBottom())
})

function scrollToBottom() {
  const el = aiMessageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function handleAiSend() {
  const text = aiInput.value.trim()
  if (!text || aiLoading.value) return

  // ── docContext 检测 ──
  let docContext = null
  const lastAiMsg = [...aiMessages.value].reverse().find(m => m.role === 'ai')
  if (lastAiMsg?.docCards?.length > 0) {
    const unsaved = lastAiMsg.docCards.find(d => d.status === 'pending')
    if (unsaved) {
      const newRound = (unsaved.round || 0) + 1
      if (newRound > 5) {
        try {
          await ElMessageBox.confirm(
            '修改次数已达上限（5次），继续可能影响回复质量。确定继续？',
            '警告', { confirmButtonText: '继续修改', cancelButtonText: '取消' }
          )
        } catch { return }
      }
      docContext = {
        tempFileId: unsaved.tempFileId,
        fileName: unsaved.fileName,
        fileType: unsaved.fileType,
        round: newRound
      }
    }
  }

  aiMessages.value.push({ role: 'user', content: text })
  aiInput.value = ''
  aiLoading.value = true

  const aiMsgIndex = aiMessages.value.length
  aiMessages.value.push({
    role: 'ai',
    content: '',
    ragActive: false,
    docCards: [],
    fileRef: null,
    imageRef: null
  })
  nextTick(() => scrollToBottom())

  if (aiAbortController.value) {
    aiAbortController.value.abort()
  }
  aiAbortController.value = new AbortController()
  const { signal } = aiAbortController.value

  try {
    const history = aiMessages.value
      .slice(0, aiMsgIndex - 1)
      .filter(m => m.role === 'user' || m.role === 'ai')
      .slice(-20)
      .map(m => ({
        role: m.role === 'ai' ? 'assistant' : 'user',
        content: m.docCards?.length > 0
          ? `[已生成文档: ${m.docCards[0].fileName}，内容已通过 docContext 提供]`
          : m.content
      }))

    for await (const chunk of streamChatMessage(text, { signal }, history, docContext)) {
      if (chunk.imageRef) {
    aiMessages.value[aiMsgIndex].imageRef = chunk.imageRef
    nextTick(() => scrollToBottom())
    continue
  }
  if (chunk.ragStatus !== undefined) {
        aiMessages.value[aiMsgIndex].ragActive = chunk.ragStatus
        continue
      }
      if (chunk.docCard) {
        const existing = aiMessages.value[aiMsgIndex].docCards
          .find(d => d.tempFileId === chunk.docCard.tempFileId)
        if (existing) {
          Object.assign(existing, chunk.docCard)
          existing.round = (existing.round || 0) + 1
        } else {
          aiMessages.value[aiMsgIndex].docCards.push({
            ...chunk.docCard,
            round: 0,
            status: 'pending'
          })
        }
        nextTick(() => scrollToBottom())
        continue
      }
      aiMessages.value[aiMsgIndex].content += chunk.text
      nextTick(() => scrollToBottom())
    }
  } catch (e) {
    if (e.name === 'AbortError') {
      aiMessages.value.splice(aiMsgIndex, 1)
    } else {
      aiMessages.value[aiMsgIndex].content = '抱歉，我暂时无法回复。请检查网络连接后重试。'
      aiMessages.value[aiMsgIndex].error = true
    }
  } finally {
    aiLoading.value = false
    nextTick(() => scrollToBottom())
  }
}

// ─── 图片预览和下载 ───
// （handleImageGen 已删除，生图改为 LLM 驱动，imageRef 通过 SSE 事件设置）

// ── 预览临时文档 ──
async function previewTempDoc(doc) {
  try {
    const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
    const token = localStorage.getItem('m78_token')

    if (doc.fileId) {
      window.open(`${baseUrl}/files/preview/${doc.fileId}?token=${encodeURIComponent(token)}`, '_blank')
      return
    }

    if (!doc.tempFileId) return
    const response = await fetch(`${baseUrl}/chat/preview-temp/${doc.tempFileId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
  } catch (e) {
    ElMessage.error('预览失败：' + e.message)
  }
}

// ── 保存临时文档到网盘 ──
async function saveTempDoc(msg, doc) {
  if (!doc.tempFileId || !doc.fileName) return
  try {
    const result = await confirmTempDocument(doc.tempFileId, null)
    doc.status = 'saved'
    if (result?.data?.fileId) {
      doc.fileId = result.data.fileId
    }
    ElMessage.success('文档已保存到网盘')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '未知错误'))
  }
}

// ── 下载临时文档到本地 ──
function downloadTempDoc(doc) {
  const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
  const token = localStorage.getItem('m78_token')

  if (doc.fileId) {
    const a = document.createElement('a')
    a.href = `${baseUrl}/files/download/${doc.fileId}?token=${encodeURIComponent(token)}`
    a.download = doc.fileName
    a.style.display = 'none'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    return
  }

  if (!doc.tempFileId || !doc.fileName) return
  const a = document.createElement('a')
  a.href = `${baseUrl}/chat/download-temp/${doc.tempFileId}?token=${encodeURIComponent(token)}`
  a.download = doc.fileName
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

// ─── 打开文件预览 ───
async function openFilePreview(fileId) {
  if (!fileId) return
  try {
    const baseUrl = import.meta.env.VITE_APP_BASE_API || ''
    const token = localStorage.getItem('m78_token')
    const response = await fetch(`${baseUrl}/files/preview/${fileId}`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
  } catch (e) {
    ElMessage.error('预览失败：' + e.message)
  }
}

// ─── 下载图片 ───
async function downloadImage(imgCard) {
  try {
    const token = localStorage.getItem('m78_token')
    const url = imgCard.fileUrl + '?token=' + encodeURIComponent(token)
    const response = await fetch(url)
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const blob = await response.blob()
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = imgCard.fileName
    a.style.display = 'none'
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    ElMessage.success('图片下载成功')
  } catch (e) {
    ElMessage.error('下载失败：' + e.message)
  }
}

// ─── 格式化大小 ───
function formatSize(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = Number(bytes)
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024
    i++
  }
  return size.toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}

// ─── 时间问候语 ───
function getTimeGreeting() {
  const h = new Date().getHours()
  let timeWord = '晚上好'
  if (h >= 6 && h < 12) timeWord = '早上好'
  else if (h >= 12 && h < 14) timeWord = '中午好'
  else if (h >= 14 && h < 18) timeWord = '下午好'

  return `${timeWord}！我是你的网盘AI助手，可以帮你搜索文件、回答关于网盘内容的问题、总结文档、生成图片等。有什么直接问我！`
}

// ─── 文件选择器 ───
const filePickerVisible = ref(false)
const filePickerQuery = ref('')
const filePickerResults = ref([])
const filePickerLoading = ref(false)
const filePickerTarget = ref(null)

watch(filePickerQuery, async (val) => {
  if (!val || val.trim().length < 1) {
    filePickerResults.value = []
    return
  }
  filePickerLoading.value = true
  try {
    const res = await searchFiles(val.trim(), 10)
    filePickerResults.value = res.data || []
  } catch (e) {
    console.warn('文件搜索失败', e)
    filePickerResults.value = []
  } finally {
    filePickerLoading.value = false
  }
})

function selectPickerFile(file) {
  filePickerTarget.value = file.fileName
  aiInput.value += '@文件：' + file.fileName + ' '
  filePickerVisible.value = false
  filePickerQuery.value = ''
  filePickerResults.value = []
}

// ─── 初始化问候 ───
onMounted(() => {
  // 首次打开对话框时注入欢迎语
  const originalOpenDialog = openDialog
  openDialog = function() {
    originalOpenDialog()
    if (aiMessages.value.length === 0) {
      const greeting = getTimeGreeting()
      aiMessages.value.push({
        role: 'ai',
        content: greeting,
        ragActive: false
      })
    }
  }
})
</script>

<style scoped>
/* ─── 图标 ─── */
.ai-icon-wrapper {
  position: fixed;
  z-index: 2100;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  cursor: grab;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.35);
  transition: left 0.3s ease, top 0.3s ease, transform 0.3s ease, box-shadow 0.2s;
  user-select: none;
}

.ai-icon-wrapper:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 24px rgba(64, 158, 255, 0.5);
}

.ai-icon-wrapper.is-dragging {
  cursor: grabbing;
  transition: none;
  transform: scale(1.05);
}

.ai-icon-wrapper.is-hidden {
  transition: transform 0.3s ease;
}

.ai-icon-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  pointer-events: none;
}

/* ─── 对话框 ─── */
.ai-panel {
  position: fixed;
  z-index: 2150;
  width: 380px;
  height: 500px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.ai-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  background: #f8faff;
  cursor: grab;
  user-select: none;
  flex-shrink: 0;
}

.ai-header:active {
  cursor: grabbing;
}

.ai-header-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

/* 消息列表 */
.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: #fafafa;
}

.ai-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.ai-msg {
  display: flex;
  gap: 8px;
  max-width: 85%;
}

.ai-msg-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.ai-msg-ai {
  align-self: flex-start;
}

.ai-msg-avatar {
  flex-shrink: 0;
}

.ai-msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

.ai-msg-user .ai-msg-bubble {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-msg-ai .ai-msg-bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 4px;
}

/* RAG 徽章 */
.ai-rag-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #67c23a;
  background: #f0f9ff;
  border: 1px solid #e1f3d8;
  border-radius: 4px;
  padding: 2px 8px;
  margin-bottom: 6px;
}

.ai-msg-error {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
}

.ai-cursor {
  display: inline-block;
  color: #409eff;
  font-weight: 700;
  animation: cursorBlink 0.8s step-end infinite;
  margin-left: 2px;
}

@keyframes cursorBlink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* 文档卡片 */
.ai-doc-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.ai-doc-card {
  padding: 10px 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  font-size: 13px;
  transition: border-color 0.2s;
}

.ai-doc-card:hover {
  border-color: #409eff;
}

.ai-doc-card-warn {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.ai-doc-warn {
  color: #e6a23c;
  font-size: 12px;
  margin-bottom: 6px;
  padding-bottom: 6px;
  border-bottom: 1px solid #f0d59e;
}

.ai-doc-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: #303133;
}

.ai-doc-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.ai-doc-size {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}

.ai-doc-actions {
  display: flex;
  gap: 4px;
}

/* 文件卡片 */
.ai-file-card {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 10px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #409eff;
  transition: background 0.2s;
}

.ai-file-card:hover {
  background: #d9ecff;
}

.ai-file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}

.ai-file-size {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}

.ai-file-open {
  flex-shrink: 0;
  color: #409eff;
  padding: 0;
  font-size: 12px;
}

/* 消息中的图片卡片 */
.ai-image-card-msg {
  margin-top: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}

.ai-image-msg-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #f5f7fa;
  font-size: 12px;
}

.ai-image-msg-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}

.ai-image-msg-size {
  color: #909399;
  flex-shrink: 0;
}

/* 输入区 */
.ai-input-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
  flex-shrink: 0;
}

/* 聊天输入框 */
.ai-chat-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.ai-send-btn {
  flex-shrink: 0;
  height: 36px;
}

/* 动画 */
.ai-slide-enter-active,
.ai-slide-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.ai-slide-enter-from,
.ai-slide-leave-to {
  opacity: 0;
  transform: scale(0.95);
}

/* 文件选择器样式（复用原有样式） */
.ai-file-picker {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 240px;
  overflow-y: auto;
}

.ai-picker-empty {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 12px 0;
}

.ai-picker-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
}

.ai-picker-item:hover {
  background: #f5f7fa;
}

.ai-picker-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-picker-size {
  font-size: 11px;
  color: #909399;
  flex-shrink: 0;
}
</style>