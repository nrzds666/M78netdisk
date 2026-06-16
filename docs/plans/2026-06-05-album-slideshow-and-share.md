# 幻灯片播放 + 相册分享 Implementation Plan

**Goal:** 在相册详情页实现全屏幻灯片播放 + 为相册添加分享功能

## 清单

| # | Task | File(s) | Status |
|---|------|---------|--------|
| **C** | **方案C: 幻灯片自动播放** | `AlbumDetailView.vue` | ✅ |
| C.1 | 全屏幻灯片 overlay（遮罩层+图片+过渡） | same | ✅ |
| C.2 | 自动播放（setInterval） + 暂停/继续 | same | ✅ |
| C.3 | 速度选择（1s/3s/5s/10s） | same | ✅ |
| C.4 | 键盘快捷键（Esc/Space/←/→） | same | ✅ |
| C.5 | 顶部工具栏"幻灯片播放"按钮 | same | ✅ |
| **D1** | **方案D 后端: Album Share API** | 后端 | ✅ |
| D1.1 | 创建 `album_shares` 表 → init.sql | `init.sql` | ✅ |
| D1.2 | `AlbumShare` 实体 + Mapper | 新建文件 | ✅ |
| D1.3 | `IAlbumShareService` + 实现（创建/查询） | 新建文件 | ✅ |
| D1.4 | `AlbumShareController`（创建/访问） | 新建文件 | ✅ |
| **D2** | **方案D 前端: 分享弹窗 + 公开展示页** | 前端 | ✅ |
| D2.1 | AlbumDetailView 顶部加"分享"按钮 + 弹窗 | `AlbumDetailView.vue` | ✅ |
| D2.2 | album.js 新增 createAlbumShare API | `src/api/album.js` | ✅ |
| D2.3 | 创建 `AlbumShareAccessView.vue`（无认证） | 新建文件 | ✅ |
| D2.4 | 路由 `/album-share/:token` | `router/index.js` | ✅ |
| **D3** | **测试与验证** | 全量 | ✅ |
