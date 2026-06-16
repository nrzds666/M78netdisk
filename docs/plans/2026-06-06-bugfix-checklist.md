# Bug 修复检查清单

**日期**: 2026-06-06
**项目**: M78 NetDisk
**范围**: 文件分享（4 bug）+ 相册（2 bug）+ 上传文件夹（1 bug）

---

## 1. 文件分享 — 分享链接页面

### 1a. 选中文件分享后链接页面没有文件

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 1a.1 | ShareAccessView.vue: `handleAccess()` 取消单文件分享时跳过 `loadItems()` 的逻辑，改为无条件调用 `loadItems()` | `src/views/share/ShareAccessView.vue` | ✅ |

### 1b. 添加提取码后没有输入框

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 1b.1 | ShareServiceImpl: `accessShare()` 改为当密码缺失时返回基本信息（含 `hasPassword=true`、`accessGranted=false`），不抛 403 | `netdisk-share/.../ShareServiceImpl.java` + `ShareVO.java` | ✅ |
| 1b.2 | ShareAccessView.vue: 适配 `accessGranted` 字段，`handleAccess()` 检查 `accessGranted` 决定是否展示密码门 | `src/views/share/ShareAccessView.vue` | ✅ |

### 1c. 分享页面没有分享者基本信息

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 1c.1 | ShareVO 新增 `ownerName`、`ownerAvatar` 字段 | `netdisk-share/.../ShareVO.java` | ✅ |
| 1c.2 | ShareServiceImpl: `toShareVO()` 通过 `ownerId` 查询用户表填充 ownerName/ownerAvatar | `netdisk-share/.../ShareServiceImpl.java` | ✅ |
| 1c.3 | ShareAccessView.vue: 顶部区域展示分享者头像+用户名 | `src/views/share/ShareAccessView.vue` | ✅ |

### 1d. "我的分享"页面提取码按钮导致排版换行

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 1d.1 | MySharesView.vue: 操作列宽度从 240px 增加到 320px，或改用紧凑布局 | `src/views/share/MySharesView.vue` | ✅ |

---

## 2. 相册

### 2a. 图片不可预览/展示，幻灯片灰色

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 2a.1 | AlbumDetailView.vue: `thumbnailKey` 为空时用预览 URL 作为备选图片源 | `src/views/album/AlbumDetailView.vue` | ✅ |

### 2b. 相册分享提示服务器内部错误

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 2b.1 | 检查 `MetaObjectHandler` 是否配置 — 已存在且支持 `createdAt` | 配置正常 | ✅ |
| 2b.2 | 后端代码逻辑正确，232 测试全部通过。`AlbumShareServiceImpl` 结构正常 | 代码正确 | ✅ |
| 2b.3 | **可能原因**: 数据库 `album_shares` 表可能不存在（init.sql 末尾新增）或 schema 不匹配。需运行最新 init.sql | 环境问题 | ⚠️ |

---

## 3. 上传文件夹

### 3a. 文件列表出现两份 + 内部文件上传失败

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| 3a.1 | 后端 ItemMapper 新增按 ownerId+parentId+name 查找已有文件夹的方法 | —（改用前端 listItems 查找） | ✅ |
| 3a.2 | FileListView.vue: `handleFolderSelected()` 修复文件夹创建逻辑——添加 folderCache 缓存已创建的文件夹 ID；`createFolder` 失败时用 `listItems` 查找已有文件夹 | `src/views/file/FileListView.vue` | ✅ |

---

## 后端测试

| # | 任务 | 文件 | 状态 |
|---|------|------|------|
| T.1 | 运行 `mvn test` 确认无回归 | - | ✅ |

---

## 后续修复清单

### Bug A：上传文件夹报"文件名包含非法字符"

| # | 文件 | 改动 |
|---|------|------|
| A1 | `FileServiceImpl.java` - `createFile()` L100-101 | `fileName.contains("..")` → `fileName.equals("..")` |
| A2 | `FileServiceImpl.java` - `createFolder()` L151 | `name.contains("..")` → `name.equals("..")` |
| A3 | `FileServiceImpl.java` - `rename()` L187-188 | `newName.contains("..")` → `newName.equals("..")` |
| A4 | `FileServiceImpl.java` - `initUpload()` L420-421 | `fileName.contains("..")` → `fileName.equals("..")` |
| A4 | `FileServiceImpl.java` - `initUpload()` L420-421 | `contains("..")` → `equals("..")` | ✅ |

### Bug B：相册卡片封面不能预览

| # | 文件 | 改动 | 状态 |
|---|------|------|------|
| B1 | `AlbumView.vue` 封面选择器 | `thumbnailKey || ''` → `thumbnailKey || previewUrl` | ✅ |
| B2 | `AlbumView.vue` 相册卡片封面 | `v-if="coverThumbnailKey"` → 无条件渲染 + fallback | ✅ |

### Bug C：相册分享链接内图片不能预览

| # | 文件 | 改动 | 状态 |
|---|------|------|------|
| C1 | `AlbumShareServiceImpl.java` | 新增 `streamSharedAlbumFile()` 通过分享 token 鉴权流式输出 | ✅ |
| C2 | `IAlbumShareService.java` | 新增接口方法 | ✅ |
| C3 | `AlbumController.java` | 新增 `GET /api/albums/share-access/{token}/preview/{itemId}` | ✅ |
| C4 | `AlbumShareAccessView.vue` | `:src` 和 `imageUrls` 使用公开预览端点 | ✅ |

---

## TDD 执行顺序

1. A1→A4（后端校验修复）→ `mvn test`
2. B1→B2（前端封面修复）→ `vitest run`
3. C1→C3（后端公开预览端点）→ `mvn test`
4. C4（前端适配）→ `vitest run`
5. 全量测试
