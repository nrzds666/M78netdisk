# M78 NetDisk — 模块逻辑文档

> 基于源码 v1.0.0 整理，供后续维护参考
> 最后更新: 2026-05-24

---

## 模块依赖拓扑

```
netdisk-bootstrap (启动入口, port 8080)
    ├── netdisk-common (公共基础设施)
    ├── netdisk-user   (用户认证)
    ├── netdisk-file   (文件/文件夹 CRUD + 上传)
    └── netdisk-share  (分享链接)
```

模块间依赖方向：`bootstrap → 其他全部`，`file/share/user → common`

---

## 1. netdisk-common — 公共基础设施

**包路径：** `com.m78.netdisk.common`

### 1.1 R<T> — 统一响应体
```
{
  "code": 200,           // 业务状态码
  "msg": "success",      // 提示信息
  "data": { ... }        // 泛型数据
}
```
静态工厂方法：
- `R.ok(data)` → code=200
- `R.fail(msg)` → code=500
- `R.fail(code, msg)` → 自定义错误码
- `R.unauthorized(msg)` → code=401
- `R.forbidden(msg)` → code=403

### 1.2 BizException — 业务异常
```java
throw new BizException("描述");              // code=500
throw new BizException(403, "无权限");        // 自定义 code
```

### 1.3 GlobalExceptionHandler — 统一异常处理
| 异常类型 | HTTP 状态码 | 说明 |
|---------|------------|------|
| BizException | 400 | 业务异常，code 取自异常 |
| IllegalArgumentException | 400 | 参数校验 |
| Exception | 500 | 系统异常，打印 error 日志 |

### 1.4 UserContext — 请求上下文
- `ThreadLocal<Long>` 存储当前请求的用户 ID
- Interceptor 在 `preHandle` 设置，`afterCompletion` 清理
- Controller/Service 通过 `UserContext.getUserId()` 获取

### 1.5 JwtTool — JWT 双 Token 认证
```
Access Token:  24h 有效期，存 Redis key=token:access:{userId}:{token}
Refresh Token: 30天有效期，存 Redis key=token:refresh:{userId}
```
- 算法：HMAC-SHA256（HS256），密钥从 `netdisk.jwt.secret` 读取
- `createAccessToken(userId)` → 签发 + 存 Redis
- `createRefreshToken(userId)` → 签发 + 存 Redis（每个用户只有一个有效的 refreshToken）
- `parseToken(token)` → 校验 JWT 签名 + 校验 Redis 中存在
- `logout(userId)` → 清除 Redis 中该用户所有 token

### 1.6 CaptchaUtil — 图形验证码
- 使用 EasyCaptcha 生成算术验证码（如 "1+2=?"）
- Redis key=`captcha:{uuid}`，有效期 2 分钟
- `verify(key, code)` → 校验后立即删除（一次性使用）

### 1.7 FileStorageService — 本地文件存储
**配置：** `netdisk.storage.local-path`（默认 `${user.dir}/storage`）

| 方法 | 功能 | 说明 |
|------|------|------|
| `store(relativeKey, bytes)` | 写入文件 | 自动创建父目录 |
| `delete(relativeKey)` | 删除文件 | 递归清理空父目录 |
| `resolvePath(relativeKey)` | 解析绝对路径 | 含路径穿越防护（`startsWith(rootPath)` 校验） |

### 1.8 MyBatisConfig — MyBatis-Plus 配置
- **分页插件：** `PaginationInnerInterceptor(DbType.MYSQL)`
- Mapper 方法参数带 `Page<T>` 即自动分页

### 1.9 AppConfig — Jackson 配置
- 注册 JavaTimeModule，禁用时间戳序列化
- 日期格式：`yyyy-MM-dd HH:mm:ss`

### 1.10 AuditLog — 审计日志（AOP 切面）

**空间：** `com.m78.netdisk.common.log`

| 组件 | 说明 |
|------|------|
| `@AuditLog` 注解 | 标注在需要审计的 Controller/Service 方法上 |
| `AuditLogAspect` 切面 | `@Around` 拦截，执行业务后异步写入 `operation_logs` 表 |

```java
// 使用示例
@AuditLog(action = "FILE_UPLOAD", itemId = "#result.data?.id")
public R<ItemVO> upload(...) { ... }

@AuditLog(action = "FOLDER_CREATE", detail = "#dto.name")
public R<Void> createFolder(@RequestBody CreateFolderDTO dto) { ... }
```

| 注解参数 | 类型 | 说明 |
|----------|------|------|
| `action` | String | 操作类型（如 FILE_UPLOAD, FOLDER_CREATE, FILE_DELETE 等） |
| `itemId` | String (SpEL) | 关联的文件/文件夹 ID，留空不关联 |
| `detail` | String (SpEL) | 动态提取的详情 JSON，留空不记录 |

**写入字段：**
- `user_id` → 从 `UserContext.getUserId()` 获取
- `action` → 注解指定
- `item_id` → SpEL 表达式提取
- `detail` → SpEL 表达式提取（字符串直接存，对象序列化为 JSON）
- `ip_address` → `HttpServletRequest.getRemoteAddr()`
- `user_agent` → 请求头 `User-Agent`
- `created_at` → 数据库默认值 `CURRENT_TIMESTAMP`

**异常安全：** 审计日志写入失败（catch Exception）不抛出，不影响主业务。

### 1.11 StorageNode — 存储节点管理

**空间：** `com.m78.netdisk.common.storage.node`

| 组件 | 说明 |
|------|------|
| `StorageNode` PO | 映射 `storage_nodes` 表 |
| `StorageNodeMapper` | 继承 `BaseMapper<StorageNode>` |
| `IStorageNodeService` | MP Service 接口 + `getActiveNodes()` |
| `StorageNodeServiceImpl` | 实现（按 weight 降序取 active 节点） |
| `StorageNodeController` | REST CRUD，路径 `/api/admin/nodes` |

**API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/admin/nodes` | 获取所有节点 |
| `GET` | `/api/admin/nodes/active` | 获取可用节点（is_active=true，按 weight 降序） |
| `GET` | `/api/admin/nodes/{id}` | 获取节点详情 |
| `POST` | `/api/admin/nodes` | 新增节点 |
| `PUT` | `/api/admin/nodes/{id}` | 更新节点 |
| `DELETE` | `/api/admin/nodes/{id}` | 删除节点 |

**字段说明：** `access_key` 明文存储，`encrypted_sk` 预留加密密钥存储。
当前仅提供 CRUD 管理，后续可扩展至 `FileStorageService` 多后端路由。

---

## 2. netdisk-user — 用户模块

**包路径：** `com.m78.netdisk.user`
**基础路径：** `/api/users`

### 2.1 认证流程

```
请求 → UserTokenInterceptor
  ├─ 取 Authorization header → 去掉 "Bearer " 前缀
  ├─ JwtTool.parseToken() → 校验 JWT + Redis
  └─ UserContext.setUserId(userId) → ThreadLocal

放行路径（不需登录）：
  POST /api/users/login
  POST /api/users/register
  GET  /api/users/captcha
  GET  /api/shares/access/{token}
```

### 2.2 接口清单

| 方法 | 路径 | 功能 | 请求参数 | 响应 |
|------|------|------|---------|------|
| `GET` | `/api/users/captcha` | 获取验证码 | 无 | `{key, imageBase64}` |
| `POST` | `/api/users/register` | 注册 | `{username, password, email, captchaKey, captchaCode}` | `UserLoginVO` |
| `POST` | `/api/users/login` | 登录 | `{username, password, captchaKey, captchaCode}` | `UserLoginVO` |
| `POST` | `/api/users/refresh` | 刷新令牌 | Header: `X-Refresh-Token` | `UserLoginVO` |
| `POST` | `/api/users/logout` | 登出 | 无 | `{}` |
| `GET` | `/api/users` | 当前用户信息 | 无（从 Token 取 userId） | `UserInfoVO` |
| `PUT` | `/api/users/password` | 修改密码 | `oldPassword, newPassword` | `{}` |
| `PUT` | `/api/users/avatar` | 更新头像 | `avatarUrl` | `{}` |

### 2.3 UserLoginVO
```json
{
  "userId": 1,
  "username": "admin",
  "avatarUrl": null,
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "expiresIn": 86400
}
```

### 2.4 UserInfoVO
```json
{
  "id": 1,
  "username": "admin",
  "email": "admin@example.com",
  "avatarUrl": null,
  "status": 1,
  "quotaBytes": 10737418240,
  "usedBytes": 0,
  "createdAt": "2026-05-24T10:00:00"
}
```

### 2.5 核心业务逻辑

**注册流程：**
1. 校验验证码（Redis 中查找 + 一次性删除）
2. 校验用户名是否已存在
3. 密码 BCrypt 加密，写入 users 表
4. 签发 accessToken + refreshToken，返回 UserLoginVO

**登录流程：**
1. 校验验证码
2. 用户名查用户，校验状态（status=1 正常）
3. BCrypt 校验密码
4. 签发双 token

**刷新 Token 流程：**
1. 从 `X-Refresh-Token` header 取 refreshToken
2. `jwtTool.parseToken()` 校验（Redis 中查找匹配）
3. 刷新 accessToken（refreshToken 保持不变，30天有效期内可反复刷新）

**登出流程：**
1. 清除 Redis 中该用户所有 access token（模糊匹配 `token:access:{userId}:*`）
2. 清除 Redis 中该用户的 refresh token

### 2.6 密码策略
- 算法：BCrypt（`BCryptPasswordEncoder`）
- 密码长度校验：6-255 字符
- 历史用户密码兼容：不兼容（BCrypt 无法校验 MD5 哈希）

### 2.7 UserMapper 额外方法
```sql
-- 原子增用量，超配额返回 0（affected=0 表示超配额）
UPDATE users SET used_bytes = used_bytes + #{delta}
WHERE id = #{userId} AND used_bytes + #{delta} <= quota_bytes

-- 减用量，最小值 0
UPDATE users SET used_bytes = GREATEST(0, used_bytes - #{delta})
WHERE id = #{userId}
```

### 2.8 用户表结构
```sql
users (id BIGINT PK, username VARCHAR(64) UNIQUE, email VARCHAR(255),
       password_hash VARCHAR(255), avatar_url TEXT, status TINYINT DEFAULT 1,
       quota_bytes BIGINT DEFAULT 10GB, used_bytes BIGINT DEFAULT 0,
       created_at DATETIME, updated_at DATETIME)
```

---

## 3. netdisk-file — 文件模块（核心）

**包路径：** `com.m78.netdisk.file`
**基础路径：** `/api/files`

### 3.1 数据模型

#### Item — 文件/文件夹（核心实体）
```
items 表：
  id            BIGINT PK AUTO_INCREMENT
  owner_id      BIGINT NOT NULL FK→users
  parent_id     BIGINT FK→items（树形结构，NULL=根目录）
  name          VARCHAR(255) NOT NULL
  is_directory  TINYINT(1) NOT NULL DEFAULT 0
  size          BIGINT NOT NULL DEFAULT 0
  mime_type     VARCHAR(127)
  storage_key   TEXT（对象存储/本地路径 key）
  etag          VARCHAR(64)
  thumbnail_key TEXT
  path          TEXT NOT NULL（物化路径，如 /documents/photo.jpg）
  sort_order    INT DEFAULT 0
  is_deleted    TINYINT(1) DEFAULT 0
  deleted_at    DATETIME
  version       INT DEFAULT 1
  created_at    DATETIME
  updated_at    DATETIME
  UNIQUE(owner_id, parent_id, name)
```

#### ItemVersion — 文件版本历史
```
item_versions 表：
  id, item_id, version, size, storage_key, etag, created_by, created_at
  UNIQUE(item_id, version)
```

#### UploadTask — 分片上传任务
```
upload_tasks 表：
  id, owner_id, parent_id, file_name, file_size, mime_type,
  chunk_size(默认5MB), total_chunks, received_chunks,
  status(pending|uploading|completed|expired|canceled),
  storage_prefix(UUID), expires_at(创建后24h), created_at, updated_at
```

#### UploadChunk — 分片记录
```
upload_chunks 表：
  id, task_id, chunk_index, size, etag, storage_key, uploaded_at
  UNIQUE(task_id, chunk_index)
```

### 3.2 接口清单

#### 文件/文件夹 CRUD

| 方法 | 路径 | 功能 | 参数 | 说明 |
|------|------|------|------|------|
| `GET` | `/api/files/list` | 列出目录内容 | `parentId`(可选), `page`, `size` | 分页，null=根目录 |
| `POST` | `/api/files/folder` | 创建文件夹 | body: `{parentId, name}` | parentId=null/0=根目录 |
| `PUT` | `/api/files/rename` | 重命名 | body: `{itemId, newName}` | 自动重建 path |
| `PUT` | `/api/files/move` | 移动 | body: `{itemIds, targetParentId}` | 批量移动，跳过无权限 |
| `DELETE` | `/api/files/trash` | 软删除 | `ids=1,2,3` | 设 is_deleted=true |
| `POST` | `/api/files/restore` | 恢复 | `ids=1,2,3` | 设 is_deleted=false |
| `DELETE` | `/api/files/permanent` | 永久删除 | `ids=1,2,3` | 删 DB + 删磁盘文件 |
| `GET` | `/api/files/trash` | 回收站列表 | `page`, `size` | 分页，按 deleted_at DESC |

#### 分片上传

| 方法 | 路径 | 功能 | 参数 | 说明 |
|------|------|------|------|------|
| `POST` | `/api/files/upload/init` | 初始化 | body: `{fileName, fileSize, parentId, mimeType, chunkSize}` | 返回 taskId |
| `POST` | `/api/files/upload/chunk` | 确认分片 | `taskId, chunkIndex, storageKey, etag, size` | 记录已上传的分片 |
| `POST` | `/api/files/upload/complete` | 完成上传 | `taskId` | 创建 Item + ItemVersion |
| `POST` | `/api/files/upload/cancel` | 取消 | `taskId` | 状态→canceled |
| `GET` | `/api/files/upload/status` | 查询进度 | `taskId` | 返回 UploadTaskVO |
| `POST` | `/api/files/upload` | 单文件上传 | MultipartFile 直接存本地 | 已实现 |
| `GET` | `/api/files/download/{id}` | 下载文件 | 支持 HTTP Range 206，流式输出 | 含断点续传 |
| `GET` | `/api/files/preview/{id}` | 预览文件 | 同下载但 Content-Disposition: inline | 图片/PDF/文本直接显示 |
| `GET` | `/api/files/download/folder/{id}` | 下载文件夹 | 递归 ZIP 打包流式输出 | 保持目录结构 |

#### 直接上传（非分片）

| 方法 | 路径 | 功能 | 参数 | 说明 |
|------|------|------|------|------|
| `POST` | `/api/files/upload` | 单文件上传 | `file`(MultipartFile), `parentId`(可选) | 存入本地磁盘 + 建 Item+Version |

### 3.3 核心业务逻辑

#### 文件列表（分页）
```
GET /list?parentId=1&page=1&size=20
  → FileServiceImpl.listItems()
  → parentId=null → selectRootItems(page, ownerId)  // 根目录
  → parentId>0   → selectChildren(page, ownerId, parentId)
  → 排序: is_directory DESC, sort_order ASC, name ASC
  → 返回: IPage<ItemVO>
```

#### 创建文件夹
```
POST /folder {parentId: 1, name: "新建文件夹"}
  → 校验同名（ownerId + parentId + name）
  → buildPath: 查父目录 path → "/父路径/新建文件夹"
  → insert Item (isDirectory=true)
```

#### 移动文件（批量）
```
PUT /move {itemIds: [1,2,3], targetParentId: 5}
  → 预查目标目录 path（只需一次）
  → 逐项：
    1. selectById 校验所有者
    2. countByName 校验重名
    3. 更新 parentId + path
  → 跳过无权限项（打 warn 日志），重名冲突则抛异常中断
```

#### 回收站
```
软删除:  UPDATE items SET is_deleted=true, deleted_at=now() WHERE id=#{id}
恢复:    UPDATE items SET is_deleted=false, deleted_at=NULL WHERE id=#{id}
永久删除:
  1. 查所有版本 → 删磁盘文件 (fileStorageService.delete)
  2. 删版本记录
  3. 删文件本身磁盘文件 (非目录 + storageKey 非空)
  4. DELETE FROM items
```

#### 分片上传流程
```
前端 → 1. POST /upload/init         → 服务端: 创建 upload_task, 返回 taskId+总分片数
     → 2. 前端自行上传分片内容到 storageKey 指定位置
     → 3. POST /upload/chunk         → 服务端: 记录分片元数据
     → 4. 重复 2-3 直至全部分片完成
     → 5. POST /upload/complete      → 服务端: 创建 Item + ItemVersion
```

#### 直接上传流程
```
前端 → POST /upload (MultipartFile)
     → 1. 校验文件非空
     → 2. 生成 storageKey = "uploads/{uuid}/{originalName}"
     → 3. fileStorageService.store() 写入本地磁盘
     → 4. fileService.createFile() 创建 Item + ItemVersion 记录
```

### 3.4 定时任务

| 任务 | 频率 | 行为 |
|------|------|------|
| `cleanExpiredUploadTasks` | 每小时 | 清理 status=pending/uploading 且 expiresAt<now 的上传任务，删分片文件+标记 expired |
| `cleanTrashOlderThan30Days` | 每天 03:00 | 删除回收站中超过 30 天的 is_deleted 记录，清理对应磁盘文件 |

---

## 4. netdisk-share — 分享模块

**包路径：** `com.m78.netdisk.share`
**基础路径：** `/api/shares`

### 4.1 数据模型

```sql
shares 表：
  id              BIGINT PK AUTO_INCREMENT
  owner_id        BIGINT FK→users
  item_id         BIGINT FK→items
  share_token     VARCHAR(32) UNIQUE（16 位 UUID 子串）
  password_hash   VARCHAR(255)（SHA256，可选）
  permission      VARCHAR(20) DEFAULT 'view'（view/download/edit）
  expire_at       DATETIME（NULL=永久有效）
  max_downloads   INT（NULL=不限次数）
  download_count  INT DEFAULT 0
  is_canceled     TINYINT(1) DEFAULT 0
  created_at      DATETIME
```

### 4.2 接口清单

| 方法 | 路径 | 功能 | 参数 | 说明 |
|------|------|------|------|------|
| `POST` | `/api/shares` | 创建分享 | body: `{itemId, password?, permission?, expireHours?, maxDownloads?}` | 校验 permission 枚举 |
| `POST` | `/api/shares/{id}/cancel` | 取消分享 | path: id | 设 is_canceled=true |
| `GET` | `/api/shares/mine` | 我的分享列表 | `page, size` | 分页，未取消+未过期 |
| `GET` | `/api/shares/access/{token}` | 访问分享 | path: token, query: `password?` | 返回 ShareVO（含文件信息） |

### 4.3 核心业务逻辑

**创建分享：**
```
→ 生成 16 位 token（UUID.substring(0,16)）
→ 校验 permission ∈ {view, download, edit}
→ 密码可选，SHA256 哈希后存 password_hash
→ expireHours 可选 → 转 expire_at = now() + expireHours
→ maxDownloads 可选 → 限制下载次数
```

**访问分享（accessShare）：**
```
1. ShareMapper.selectValidShare(token)
   → 条件：NOT is_canceled AND (expire_at IS NULL OR expire_at > now())
          AND (max_downloads IS NULL OR download_count < max_downloads)
2. 校验密码（SHA256 对比）
3. 增加 download_count + 1（updateById）
4. 补查 items 表 → 填充 fileName/fileSize/mimeType/isDirectory 到 ShareVO
```

**我的分享列表（分页）：**
```
→ selectActiveShares(page, ownerId)
→ 条件：NOT is_canceled AND (expire_at IS NULL OR expire_at > now())
→ 每条补查 items 表获取文件信息
```

### 4.4 ShareVO
```json
{
  "id": 1,
  "ownerId": 1,
  "itemId": 10,
  "shareToken": "a1b2c3d4e5f6g7h8",
  "permission": "view",
  "hasPassword": false,
  "expireAt": null,
  "maxDownloads": null,
  "downloadCount": 3,
  "isCanceled": false,
  "createdAt": "2026-05-24T10:00:00",
  "fileName": "photo.jpg",        // ← 来自 items 表
  "isDirectory": false,           // ← 来自 items 表
  "fileSize": 1048576,            // ← 来自 items 表
  "mimeType": "image/jpeg"        // ← 来自 items 表
}
```

---

## 5. netdisk-bootstrap — 启动入口

**包路径：** `com.m78.netdisk`
**配置：** `application.yaml`

### 5.1 核心配置
```yaml
server.port: 8080

spring.datasource:
  url: jdbc:mysql://localhost:3306/m78netdisk
  driver-class-name: com.mysql.cj.jdbc.Driver
  username: m78_netdisk
  password: 942649ZXzm.

spring.redis:
  host: localhost
  port: 6379

spring.servlet.multipart:
  max-file-size: 100MB
  max-request-size: 500MB

netdisk.jwt:
  secret: (HS256 密钥，至少 256 位)
  access-token-expiration: 86400000     # 24 小时
  refresh-token-expiration: 2592000000  # 30 天

netdisk.storage.local-path: D:/M78netdisk/storage
```

### 5.2 日志配置
- MyBatis-Plus 开启 SQL 日志：`log-impl: org.apache.ibatis.logging.stdout.StdOutImpl`
- Jackson 日期格式：`yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai`

### 5.3 API 文档（Swagger）
- Knife4j + SpringDoc，分 4 组：用户模块、文件模块、分享模块、管理模块
- 访问地址：`http://localhost:8080/swagger-ui/index.html`

---

## 6. 数据库

### 6.1 建表脚本
`D:\M78netdisk\database\init.sql`（MySQL 8.0 语法）

### 6.2 表清单

| 表名 | 用途 | 关联模块 | 是否有代码操作 |
|------|------|---------|--------------|
| `users` | 用户 | user | ✅ |
| `items` | 文件&文件夹（树形，软删除） | file | ✅ |
| `item_versions` | 文件版本历史 | file | ✅ |
| `shares` | 分享链接 | share | ✅ |
| `upload_tasks` | 分片上传任务 | file | ✅ |
| `upload_chunks` | 分片记录 | file | ✅ |
| `operation_logs` | 审计日志 | common | ✅ |
| `storage_nodes` | 存储后端节点 | common | ✅ |

### 6.3 分页统一规范
- 入参：`page`(默认1) + `size`(默认20，最大100)
- MP 分页插件：`PaginationInnerInterceptor(DbType.MYSQL)`
- 响应：`IPage<T>` → `{records, total, size, current, pages}`

### 6.4 冗余字段说明
- `items.path` — 物化路径（如 `/documents/photo.jpg`），写入时维护，读取时直接用，避免递归查询树形结构

---

## 7. 常见维护场景

### 7.1 新增一个 API
1. 确定 Controller 路径和 HTTP 方法
2. Service 接口 + 实现
3. DTO（请求体校验用 `@Valid`）+ VO（响应体）
4. Mapper（继承 `BaseMapper` 或写 XML/注解）
5. 如需分页，在 Service 中 `new Page<>(page, size)` 传入 Mapper

### 7.2 增加新模块
1. 创建 Maven module（在根 pom.xml 添加 `<module>`）
2. 在 bootstrap/pom.xml 添加依赖
3. 遵循包命名规范：`com.m78.netdisk.{module}`
4. Controller 路径前缀：`/api/{module}`

### 7.3 修改数据库表
1. 修改 `init.sql`
2. 修改对应 POJO（`@TableName` + 字段）
3. 修改 `schema-design.md`
4. 检查所有引用的 Mapper XML/注解 SQL 是否需要调整

### 7.4 切换存储后端
`FileStorageService` 封装了所有文件读写操作。如需从本地磁盘切换至 S3/MinIO：
1. 新增 `S3StorageService` 实现相同接口
2. 通过配置切换 Bean（`@Profile` 或 `@ConditionalOnProperty`）
