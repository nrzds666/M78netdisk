# M78 NetDisk 前端

基于 Vue 3 + Element Plus 的网盘 Web 客户端。

## 技术栈

| 层面 | 技术 |
|:-----|:-----|
| 框架 | Vue 3（Composition API / `<script setup>`） |
| 路由 | Vue Router 4 |
| 状态管理 | Pinia |
| UI 组件库 | Element Plus + @element-plus/icons-vue |
| HTTP | Axios（拦截器 / 防重复提交 / Token 自动刷新） |
| 构建 | Vite 5 |
| 测试 | Vitest + @vue/test-utils + jsdom |

## 页面结构

| 路由 | 页面 | 说明 |
|:-----|:-----|:-----|
| `/login` | LoginView | 登录/注册（含算术验证码） |
| `/files` | FileListView | 文件列表（搜索/筛选/分页/视图切换/批量操作） |
| `/trash` | TrashView | 回收站（还原/永久删除） |
| `/transfer` | TransferView | 传输管理（上传队列/进度/已完成） |
| `/shares` | MySharesView | 我的分享 & 收到的分享 |
| `/albums` | AlbumView | 相册列表（创建/重命名/封面） |
| `/albums/:id` | AlbumDetailView | 相册详情（幻灯片播放/分享） |
| `/vault` | VaultView | 保险箱（设置/解锁/文件管理） |
| `/share/:token` | ShareAccessView | 公开分享访问（密码门/文件浏览/保存到网盘） |
| `/album-share/:token` | AlbumShareAccessView | 公开相册分享访问 |

## 快速启动

```bash
npm install
npm run dev          # 开发模式，默认 http://localhost:5173
```

开发时 `/api` 自动代理到 `http://localhost:8080`（见 `vite.config.js`）。

## 构建

```bash
npm run build        # 输出到 dist/
npm run preview      # 预览构建产物
```

生产部署：将 `dist/` 放入后端 `resources/static/`，或通过 Nginx 反代。

## 测试

```bash
npm test             # 运行全部测试用例
npm run test:watch   # 监听模式
```

## 项目目录

```
src/
├── api/              API 请求封装（file / share / album / user / vault / calendar）
│   └── request.js    Axios 实例（拦截器 / Token 自动刷新）
├── stores/           Pinia 状态管理（user / file / upload）
├── views/            页面组件
│   ├── layout/       MainLayout（侧边栏 + 顶栏）
│   ├── login/        登录/注册
│   ├── file/         文件列表（核心页面）
│   ├── trash/        回收站
│   ├── transfer/     传输管理
│   ├── share/        分享管理 & 公开访问
│   ├── album/        相册列表 & 详情 & 公开访问
│   └── vault/        保险箱
├── components/       通用组件（BreadCrumb 等）
├── utils/            工具函数（auth.js / indexeddb.js）
└── plugins/          插件（cache.js 防重复提交）
```
