# 相册封面修改 + 改名 + 相册入口 Implementation Plan

**Goal:** 相册卡片交互改造：点击封面换封面（从相册图片选）、点击名字内联改名、双击进入全屏相册。

## 后续待实现
- **方案 C:** 幻灯片自动播放 — 全屏自动轮播（每 3 秒切换），可暂停/继续、调整速度
- **方案 D:** 相册分享 — 生成相册分享链接，对方打开后看到只读的相册画廊

## 前置条件
- 后端 `PUT /api/albums/{id}` (updateAlbum) 已实现 ✅
- 后端 `PUT /api/albums/{id}/cover` (setCover) 已实现 ✅
- 前端 `getAlbumDetail/deleteAlbum/addItems/removeItems` 已实现 ✅

## 清单

| # | Task | File(s) | Status |
|---|------|---------|--------|
| **1** | **album.js: 补全缺失的 API** | `src/api/album.js` | ✅ |
| 1.1 | 添加 `updateAlbum(id, data)` — PUT /api/albums/{id} | same | ✅ |
| 1.2 | 添加 `setAlbumCover(id, itemId)` — PUT /api/albums/{id}/cover?itemId=xxx | same | ✅ |
| **2** | **AlbumView: 相册卡片交互拆分 + 内联改名 + 封面选择器** | `src/views/album/AlbumView.vue` | ✅ |
| 2.1 | 点击相册名 → 内联 input 改名，回车调 `updateAlbum` 保存 | same | ✅ |
| 2.2 | 点击封面 → 弹出封面选择器 dialog，展示相册内图片缩略图 | same | ✅ |
| 2.3 | 选择图片 → 预览区更新 → 点「保存封面」调 `setAlbumCover` | same | ✅ |
| 2.4 | 双击卡片 → 进入相册详情页（路由跳转） | same | ✅ |
| **3** | **AlbumDetailView: 相册全屏详情页** | `src/views/album/AlbumDetailView.vue` | ✅ |
| 3.1 | 页面结构：顶部相册名/编辑/返回，网格展示所有图片 | same | ✅ |
| 3.2 | 迁移现有功能：添加图片、移除图片 | same | ✅ |
| 3.3 | 图片预览支持（点开大图） | same | ✅ |
| **4** | **Router: 添加相册详情路由** | `src/router/index.js` | ✅ |
| 4.1 | 添加 `/albums/:id` 子路由 | same | ✅ |
| **5** | **验证** | 全量测试 | ✅ |
