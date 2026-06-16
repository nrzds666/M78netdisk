# 相册图片预览进阶功能 Implementation Plan

**Goal:** 实现方案 A（全屏画廊模式）+ 方案 B（图片信息浮层）

## 记录后续实现（C+D）
- **方案 C:** 幻灯片自动播放 — 全屏自动轮播（每 3 秒切换），可暂停/继续、调整速度
- **方案 D:** 相册分享 — 生成相册分享链接，对方打开后看到只读的相册画廊

## 清单

| # | Task | File(s) | Status |
|---|------|---------|--------|
| **1** | **AlbumDetailView: 全屏画廊 + 信息浮层** | `src/views/album/AlbumDetailView.vue` | ✅ |
| 1.1 | 移除旧的 el-dialog 预览，改用 `preview-src-list` | same | ✅ |
| 1.2 | 在图片网格每张 el-image 绑定相同的 preview-src-list | same | ✅ |
| 1.3 | 在预览底部添加信息浮层（文件名、大小、类型、上传时间） | same | ✅ |
| **2** | **验证** | 全量测试 | ✅ |
