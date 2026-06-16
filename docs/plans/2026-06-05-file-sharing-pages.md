# 文件分享页面补全 Implementation Plan

**Goal:** 补全文件分享功能的所有前端页面和 API 对接，包括：创建分享、访问分享链接、浏览分享内容、下载/保存文件、收到的分享。

## 前置分析发现的 BUG（先修）
- `MySharesView.vue:40` — `row.accessCount` / `row.maxAccessCount` 字段不存在，后端 ShareVO 实际发送 `downloadCount` / `maxDownloads`。需要在完善时一并修复。

---

## 清单

| # | Task | File(s) | Status |
|---|------|---------|--------|
| **1** | **share.js: 补全所有缺失的 API** | `src/api/share.js` | ✅ |
| 1.1 | 添加 `createShare(data)` — POST /api/shares | same | ✅ |
| 1.2 | 添加 `accessShare(token, password)` — GET /api/shares/access/{token} | same | ✅ |
| 1.3 | 添加 `listReceivedShares(page, size)` — GET /api/shares/received | same | ✅ |
| 1.4 | 添加 `listShareItems(token, password, parentId, page, size)` — GET /api/shares/access/{token}/items | same | ✅ |
| 1.5 | 添加 `downloadShareFile(token, password, itemId)` — GET /api/shares/access/{token}/download (blob) | same | ✅ |
| 1.6 | 添加 `saveShareFiles(token, password, itemIds)` — POST /api/shares/access/{token}/save | same | ✅ |
| **2** | **FileListView: 创建分享弹窗** | `src/views/file/FileListView.vue` | ✅ |
| 2.1 | 实现 `handleShare(row)` — 弹出创建分享 dialog | same | ✅ |
| 2.2 | dialog UI: 提取码、过期时间(1天/7天/30天/永久)、权限(view/download/edit)、下载次数限制 | same | ✅ |
| 2.3 | 点击创建 → 调 `createShare` → 展示分享 token 链接 + 提取码，可复制 | same | ✅ |
| **3** | **ShareAccessView: 访问分享链接页面** | `src/views/share/ShareAccessView.vue` | ✅ |
| 3.1 | 页面结构：输入提取码（如需）、展示分享文件信息 | same | ✅ |
| 3.2 | 访问验证 → 调 `accessShare` → 展示文件/文件夹内容 | same | ✅ |
| 3.3 | 浏览分享文件夹内容 → 调 `listShareItems`（支持进入子目录、返回上级） | same | ✅ |
| 3.4 | 下载分享文件 → 调 `downloadShareFile` | same | ✅ |
| 3.5 | 保存到我的网盘 → 调 `saveShareFiles` | same | ✅ |
| **4** | **Router: 添加新路由** | `src/router/index.js` | ✅ |
| 4.1 | 添加 `/share/:token` 路由（顶级路由，不经过 MainLayout，允许未登录访问） | same | ✅ |
| 4.2 | 添加 `/share/:token/folder/:folderId` 子路由 | same | ✅ |
| **5** | **MySharesView: 完善分享列表页面** | `src/views/share/MySharesView.vue` | ✅ |
| 5.1 | 修复字段名: `accessCount`→`downloadCount`, `maxAccessCount`→`maxDownloads` | same | ✅ |
| 5.2 | 增加「复制链接」「复制提取码」按钮 | same | ✅ |
| 5.3 | 增加「收到的分享」tab，调 `listReceivedShares` 展示 | same | ✅ |
| **6** | **后端补充（如需）** | 待定 | ⬜ |
| **7** | **验证** | 全量测试 + 前端 lint | ⬜ |

---

## 任务依赖图

```
Task 1 (API) ─┬─→ Task 2 (创建分享弹窗)
              ├─→ Task 3 (访问分享页面)
              └─→ Task 5 (完善列表)

Task 4 (Router) ─→ Task 3 (需要路由才能访问)

Task 6 (后端) ─ 按需，若前端需要后台修改
```

## 后端已有 API 一览（无需修改）
- `POST /api/shares` — createShare
- `POST /api/shares/{id}/cancel` — cancelShare (已有前端对接)
- `GET /api/shares/mine` — listMyShares (已有前端对接)
- `GET /api/shares/access/{token}` — accessShare
- `GET /api/shares/received` — listReceivedShares
- `GET /api/shares/access/{token}/items` — listShareItems
- `GET /api/shares/access/{token}/download` — downloadShareFile
- `POST /api/shares/access/{token}/save` — saveShareFiles

注意：`/api/shares/access/**` 路径被 interceptor 放行（不校验登录），但 save 接口通过手动解析 JWT 来处理已登录用户。
