# M78 NetDisk 后端

Spring Boot 3.3.7 + JDK 17 + Maven 多模块。端口 8081，JWT+Redis 鉴权，MyBatis-Plus + MySQL 8.0。

## 模块一览

```
M78netdisk-backed/
├── pom.xml                  # 父 POM, Spring Boot 3.3.7, JDK 17
├── netdisk-bootstrap/       # 启动入口 + 管理后台 + 全局配置
├── netdisk-common/          # 公共基础设施（JWT/UserContext/R/Storage/审计日志）
├── netdisk-user/            # 用户注册/登录/Token 刷新
├── netdisk-file/            # 文件 CRUD/上传/版本/媒体进度
├── netdisk-share/           # 分享链接管理
├── netdisk-album/           # 相册管理
├── netdisk-vault/           # 保险箱
├── netdisk-calendar/        # 日历/农历
├── netdisk-ai/              # AI 对话/RAG/文档生成
├── database/                # init.sql + 迁移脚本
└── build.bat / package.bat / run.bat
```

模块依赖：bootstrap → 全部，其余 → common

## 已有功能（按模块）

### netdisk-user — 用户模块
- `POST /api/users/register` — 注册
- `POST /api/users/login` — 登录（返回 accessToken + refreshToken）
- `POST /api/users/refresh` — 静默刷新 token
- `POST /api/users/logout` — 登出（清 Redis）
- `GET /api/users/captcha` — 算术验证码
- `GET /api/users/info` — 当前用户信息
- 拦截器 `UserTokenInterceptor` 校验所有 `/api/**` 请求

### netdisk-file — 文件模块
- `GET /api/files/list` — 目录列表（分页）
- `POST /api/files/folder` — 创建文件夹
- `PUT /api/files/rename` / `PUT /api/files/move` — 重命名/移动
- `DELETE /api/files/trash` / `POST /api/files/restore` / `DELETE /api/files/permanent` — 回收站
- 上传：直接上传 + 分片断点续传（init→chunk→complete→cancel）
- `GET /api/files/download/{id}` — 下载（Range 断点续传）
- `GET /api/files/download/folder/{id}` — ZIP 打包下载文件夹
- `GET /api/files/preview/{id}` — 预览
- 文件版本历史（`item_versions` 表）
- `FileCleanupTask` 定时清理过期上传任务和 30 天前回收站
- `MediaProgress` 媒体播放进度读写
- Office→PDF 转换（`DocumentConversionService`）

### netdisk-share — 分享模块
- `POST /api/shares` — 创建分享（密码/时效/下载次数）
- `POST /api/shares/{id}/cancel` — 取消
- `GET /api/shares/mine` — 我的分享列表（分页）
- `GET /api/shares/access/{token}` — 访问分享
- `GET /api/shares/access/{token}/items` — 浏览分享文件夹
- `GET /api/shares/access/{token}/download` — 从分享下载
- `POST /api/shares/access/{token}/save` — 保存到自己的网盘
- `GET /api/shares/received` — 已接收分享记录

### netdisk-album — 相册模块
- 相册 CRUD + 分页列表
- 添加/移除照片，设置封面
- 相册分享（免登录 `GET /api/albums/share/{token}`）

### netdisk-vault — 保险箱
- 设置密码 / 解锁（BCrypt + Redis 状态）/ 锁定
- 保险箱内文件：列表/创建文件夹/上传/下载/移出
- 文件标记 `is_vaulted=true`

### netdisk-calendar — 日历
- `GET /api/calendar/today` — 农历/黄历/宜忌/分享吉日

### netdisk-ai — AI 模块
- `POST /api/chat/stream` — SSE 流式对话（DeepSeek）
- `POST /api/chat` — 非流式对话
- `GET /api/chat/documents/search` — 搜索文件
- `POST /api/chat/save-document` — 保存文档到网盘
- 临时文档：生成/预览/下载/确认保存
- RAG 检索增强（Qdrant 向量库）
- `StreamChatService` — 对话服务 + [GEN_DOC:标题|格式] 标记检测
- `DocumentGenerator` — Markdown→docx/xlsx（部分委托 Python 服务）

### netdisk-bootstrap — 管理后台
- `AdminUserController` — 用户管理
- `AdminStatsController` — 统计仪表盘
- `AdminLogController` — 操作日志查询
- `StorageNodeController` — 多存储节点 CRUD

## 关键基础设施
- `JwtTool` — HS256 签发/校验，Redis 存储
- `UserContext` — ThreadLocal userId
- `CaptchaUtil` — EasyCaptcha 算术验证码
- `R<T>` — 统一响应 `{code, msg, data}`
- `GlobalExceptionHandler` — 全局异常处理
- `FileStorageService` — 本地文件读写（路径穿越防护）
- `AuditLog` — AOP 审计日志切面
- `TokenCleanupRunner` — 启动时清 Redis token

## 未完成 / 已知问题
- JWT role 固化：DB role 变更后旧 token 中 role 不变（AdminUserController 无 updateRole，role 变更时也未调 logout）
- AI 图片生成：ComfyUI + SDXL Turbo 已部署，后端/前端接入 ComfyUI API 未打通
- 无单元测试（只有 `test_e2e.py` 端到端脚本）
