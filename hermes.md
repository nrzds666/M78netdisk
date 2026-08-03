# M78 NetDisk 前端

Vue 3 + Vite + Element Plus。开发时 Vite 代理后端 8081，生产时打包入 JAR。

## 目录结构

```
M78netdisk-frontend/
├── src/
│   ├── api/                 # API 封装层
│   │   ├── request.js       # Axios 实例（拦截器：注入 token + 401 刷新）
│   │   ├── user.js          # 登录/注册/验证码
│   │   ├── file.js          # 文件 CRUD
│   │   ├── share.js         # 分享管理
│   │   ├── album.js         # 相册
│   │   ├── vault.js         # 保险箱
│   │   ├── calendar.js      # 日历
│   │   ├── admin.js         # 管理后台
│   │   ├── chat.js          # AI 对话（非流式）
│   │   └── chat-stream.js   # AI SSE 流式对话
│   ├── components/
│   │   ├── AiAssistant.vue  # AI 助手浮动面板（SSE 流式 + 文档卡片）
│   │   └── BreadCrumb.vue   # 面包屑导航
│   ├── views/
│   │   ├── login/LoginView.vue
│   │   ├── home/HomeView.vue          # 首页（运势/日历摘要）
│   │   ├── file/FileListView.vue      # 文件列表（含上传/预览/下载）
│   │   ├── trash/TrashView.vue        # 回收站
│   │   ├── transfer/TransferView.vue  # 传输任务
│   │   ├── share/MySharesView.vue     # 我的分享
│   │   ├── share/ShareAccessView.vue  # 分享访问（免登录）
│   │   ├── album/AlbumView.vue        # 相册列表
│   │   ├── album/AlbumDetailView.vue  # 相册详情
│   │   ├── album/AlbumShareAccessView.vue  # 相册分享（免登录）
│   │   ├── vault/VaultView.vue        # 保险箱
│   │   ├── admin/AdminDashboardView.vue    # 管理仪表盘
│   │   ├── admin/AdminUsersView.vue        # 用户管理
│   │   ├── admin/AdminNodesView.vue        # 存储节点管理
│   │   ├── admin/AdminLogsView.vue         # 操作日志
│   │   └── layout/MainLayout.vue      # 主布局（侧边栏+顶栏+AiAssistant）
│   ├── router/index.js      # 路由（createWebHistory + 守卫）
│   ├── stores/              # Pinia stores
│   └── utils/auth.js        # token 读写（localStorage）
├── vite.config.js           # proxy /api → localhost:8081
└── dist/                    # 构建产物
```

## 已有功能

- 登录/注册页：算术验证码、记住密码
- 首页：运势（农历黄历）、日历摘要
- 文件管理：文件夹树导航、网格/列表视图切换、上传（拖拽+直接+文件夹）、进度条、预览（iframe）、下载、重命名、移动、删除、回收站恢复、永久删除
- 分享：创建分享链接（密码/时效）、我的分享列表、接收的分享、分享访问页（免登录浏览+下载+保存）、取消分享
- 相册：创建/删除/重命名、添加/移除照片、设置封面、相册分享（免登录预览）
- 保险箱：设置密码、解锁/锁定、文件上传/下载/移出
- 传输任务：上传/下载任务列表
- 管理后台：仪表盘统计图表、用户列表、存储节点管理、操作日志查询
- AI 助手（AiAssistant）：浮动按钮打开面板、SSE 流式对话、多轮对话历史、文件选择器（选择对话上下文）、文档自动生成（[GEN_DOC:标题|格式] → 预览卡片 → 保存/下载）
- 鉴权：路由守卫（首次加载主动验 token）、Axios 拦截器（401 静默刷新 token 后重试）

## 未完成 / 已知问题

- Vite 热更新有时不生效（需硬重启 dev server）
- AiAssistant 文件预览 401：`window.open` 不带 Authorization header，改 fetch+blob 方案部分路径已修
- AiAssistant 保存按钮双击 → MySQL 死锁（前端 `_saving` 防重复标志部分已加）
- AiAssistant 临时文档保存后卡片仍走临时接口 → 404（已部分修复：保存成功记 `fileId` 后续走网盘接口）
- FileListView iframe 预览 401（拦截器不读 query 参数 token）
- localStorage 未区分用户（运势等功能的 key 不含 userId，同浏览器多用户串扰）
- 路由守卫只验 token 存在性不验有效期（已加首次主动验 token，但后续路由切换不验）
- AiAssistant el-select 下拉被面板遮挡（z-index+overflow 冲突，已通过 `:teleported="false"` 修复）
- `createWebHistory()` 需要后端 SPA fallback，否则非根路径刷新 404
