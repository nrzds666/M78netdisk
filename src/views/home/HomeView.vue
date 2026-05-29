<template>
  <div class="home-page">
    <div class="home-grid">
      <!-- Left: Recent files -->
      <div class="home-section">
        <h3 class="section-title">最近上传</h3>
        <div class="file-list-card">
          <div v-if="recentFiles.length === 0" class="empty-state">
            <el-empty :image-size="80" description="暂无最近上传的文件" />
          </div>
          <div
            v-for="file in recentFiles"
            :key="file.id"
            class="file-item"
            @click="goToFiles"
          >
            <el-icon :size="20" :color="file.isDirectory ? '#e6a23c' : '#409eff'">
              <Folder v-if="file.isDirectory" />
              <Document v-else />
            </el-icon>
            <span class="file-name">{{ file.name }}</span>
            <span class="file-time">{{ formatTime(file.createdAt) }}</span>
          </div>
        </div>

        <h3 class="section-title" style="margin-top: 20px">最近提取</h3>
        <div class="file-list-card">
          <div v-if="recentSaves.length === 0" class="empty-state">
            <el-empty :image-size="80" description="暂无最近提取的文件" />
          </div>
          <div
            v-for="file in recentSaves"
            :key="file.id"
            class="file-item"
            @click="goToTransfer"
          >
            <el-icon :size="20" color="#67c23a"><Download /></el-icon>
            <span class="file-name">{{ file.name }}</span>
            <span class="file-time">{{ formatTime(file.createdAt) }}</span>
          </div>
        </div>
      </div>

      <!-- Right: Calendar + Fortune -->
      <div class="home-section calendar-section">
        <div class="date-card">
          <div class="date-main">{{ currentDate }}</div>
          <div class="date-lunar" v-if="calendarData.lunar">
            {{ calendarData.lunar.year }}年 · {{ calendarData.lunar.month }}{{ calendarData.lunar.day }}
          </div>
          <div class="date-zodiac" v-if="calendarData.lunar">
            生肖：{{ calendarData.lunar.zodiac }} · 节气：{{ calendarData.lunar.jieQi || '无' }}
          </div>
        </div>

        <template v-if="showFortune">
          <el-button type="primary" size="large" class="fortune-btn" @click="checkFortune" :loading="loading">
            🔮 测运势
          </el-button>
        </template>

        <template v-else>
          <div class="fortune-card">
            <div class="fortune-title">今日运势</div>
            <div class="fortune-content">
              <div class="fortune-column yi-column">
                <div class="fortune-label yi-label">宜</div>
                <div class="fortune-tags">
                  <el-tag v-for="item in fortuneYi" :key="item" type="success" effect="plain" class="fortune-tag">{{ item }}</el-tag>
                </div>
              </div>
              <div class="fortune-column ji-column">
                <div class="fortune-label ji-label">忌</div>
                <div class="fortune-tags">
                  <el-tag v-for="item in fortuneJi" :key="item" type="danger" effect="plain" class="fortune-tag">{{ item }}</el-tag>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getToday } from '@/api/calendar'
import { recentItems, recentSaves } from '@/api/file'
import { Folder, Document, Download } from '@element-plus/icons-vue'

const router = useRouter()

const recentFiles = ref([])
const recentSavesList = ref([])
const calendarData = ref({})
const showFortune = ref(true)
const loading = ref(false)

const fortuneYi = computed(() => calendarData.value.yi || [])
const fortuneJi = computed(() => calendarData.value.ji || [])

const weekDays = ['日', '一', '二', '三', '四', '五', '六']
const currentDate = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 星期${weekDays[d.getDay()]}`
})

async function loadData() {
  try {
    const [filesRes, savesRes] = await Promise.all([
      recentItems(3),
      recentSaves(3)
    ])
    recentFiles.value = filesRes.data || []
    recentSavesList.value = savesRes.data || []
  } catch (e) {
    // API might not be ready yet
  }
}

async function checkFortune() {
  loading.value = true
  try {
    const res = await getToday()
    calendarData.value = res.data || {}
    showFortune.value = false
  } catch (e) {
    calendarData.value = {
      yi: ['学习', '交流'],
      ji: ['冒险', '投资']
    }
    showFortune.value = false
  } finally {
    loading.value = false
  }
}

function goToFiles() {
  router.push('/files')
}

function goToTransfer() {
  router.push('/transfer')
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

onMounted(loadData)
</script>

<style scoped>
.home-page {
  max-width: 1200px;
  margin: 0 auto;
}

.home-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
}

.home-section {
  min-width: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.file-list-card {
  background: #fff;
  border-radius: 8px;
  padding: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.empty-state {
  padding: 20px 0;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.file-item:hover {
  background: #f0f5ff;
}

.file-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-time {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}

.calendar-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.date-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 24px;
  color: #fff;
  text-align: center;
}

.date-main {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 8px;
}

.date-lunar {
  font-size: 15px;
  opacity: 0.9;
  margin-bottom: 4px;
}

.date-zodiac {
  font-size: 13px;
  opacity: 0.7;
}

.fortune-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  border-radius: 10px;
}

.fortune-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.fortune-title {
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  color: #303133;
  margin-bottom: 16px;
}

.fortune-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.fortune-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fortune-label {
  font-size: 14px;
  font-weight: 600;
  width: 24px;
}

.yi-label { color: #67c23a; }
.ji-label { color: #f56c6c; }

.fortune-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.fortune-tag {
  font-size: 13px;
}
</style>
