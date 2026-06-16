# 文件夹上传 + 传输页面完善 Implementation Plan

**Goal:** 实现文件夹上传功能 + 完善传输页面（进度、队列、实时状态）

## 清单

| # | Task | File(s) | Status |
|---|------|---------|--------|
| **1** | **文件夹上传** | `src/views/file/FileListView.vue` | ✅ |
| 1.1 | 添加隐藏 `<input webkitdirectory>` 触发文件夹选择 | same | ✅ |
| 1.2 | 解析目录结构 → 递归创建文件夹 → 上传文件 | same | ✅ |
| 1.3 | 上传过程中显示进度反馈 | same | ✅ |
| **2** | **创建 Upload Store** | `src/stores/upload.js` | ✅ |
| 2.1 | Pinia store: 上传队列、进度、状态管理 | same | ✅ |
| 2.2 | 全局上传进度管理（axios onUploadProgress 回调） | same | ✅ |
| **3** | **传输页面重构** | `src/views/transfer/TransferView.vue` | ✅ |
| 3.1 | 三 tab：上传中 / 已完成 / 下载列表 | same | ✅ |
| 3.2 | 上传中列表绑定 upload store 展示实时进度 | same | ✅ |
| 3.3 | 已完成列表保留现有 `recentItems` | same | ✅ |
| 3.4 | 下载列表保留现有 `recentSaves` | same | ✅ |
| **4** | **验证** | 全量测试 | ✅ |
