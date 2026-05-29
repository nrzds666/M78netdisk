# M78 NetDisk 项目文档

> 基于 Spring Boot 2.7.18 的网盘系统  
> 数据库: MySQL 8.0  
> ORM: MyBatis-Plus 3.5.7  
> 项目路径: `D:\M78netdisk`

---

## 模块结构

```\nm78-netdisk (根 POM)\n├── netdisk-bootstrap   — 启动入口、全局配置、Swagger 聚合\n├── netdisk-common      — 公共工具、响应体、异常处理、存储节点管理\n├── netdisk-file        — 文件/文件夹 CRUD、分片上传、媒体播放进度\n├── netdisk-share       — 分享链接管理、接收分享、分享内容浏览/下载/保存\n├── netdisk-user        — 用户注册登录、拦截器\n├── netdisk-vault       — 机密文件箱（密码保护、独立浏览/上传/下载）\n├── netdisk-calendar    — 日历模块（农历/黄历/宜忌/分享吉日）\n└── netdisk-album       — 相册模块（创建相册、增删照片、设置封面）\n```

---

## 数据表一览

| 表名 | 用途 | 说明 |
|------|------|------|
| `users` | 用户 | 用户名/密码/邮箱/配额 |
| `items` | 文件&文件夹 | 树形结构（parent_id）、软删除、is_vaulted、is_from_share |
| `item_versions` | 文件版本历史 | 每次覆盖保存一个版本 |
| `shares` | 分享链接 | 可设密码/时效/下载次数 |
| `received_shares` | 接收的分享记录 | 用户访问分享时的自动记录 |
| `upload_tasks` | 分片上传任务 | 断点续传状态追踪 |
| `upload_chunks` | 已上传分片记录 | 每片 etag/storage_key |
| `operation_logs` | 操作审计日志 | JSONB 详情 |
| `storage_nodes` | 存储后端节点 | 支持多后端扩展 |
| `user_vaults` | 机密文件箱密码 | 每个用户一条，BCrypt 哈希 |
| `media_progress` | 媒体播放进度 | 视频/音频进度秒数 + 完成标志 |
| `albums` | 相册 | 名称/封面/描述/排序 |
| `album_items` | 相册-文件关联 | N:M 关系，unique(album_id, item_id) |

---

## API 概览

### 文件模块 — `netdisk-file`

**接口前缀: `/api/files`**

| 方法 | 路径 | 说明 | 需分页 |
|------|------|------|--------|
| GET | `/api/files/list?parentId=` | 列出目录下文件/文件夹 | ✅ |
| POST | `/api/files/folder` | 创建文件夹 | ❌ |
| PUT | `/api/files/rename` | 重命名 | ❌ |
| PUT | `/api/files/move` | 移动文件/文件夹 | ❌ |
| DELETE | `/api/files/trash?ids=` | 软删除到回收站 | ❌ |
| POST | `/api/files/restore?ids=` | 从回收站恢复 | ❌ |
| DELETE | `/api/files/permanent?ids=` | 永久删除 | ❌ |
| GET | `/api/files/trash` | 列出回收站文件 | ✅ |
| POST | `/api/files/upload/init` | 初始化分片上传 | ❌ |
| POST | `/api/files/upload/chunk` | 确认分片上传完成 | ❌ |
| POST | `/api/files/upload/complete` | 完成上传 | ❌ |
| POST | `/api/files/upload/cancel` | 取消上传 | ❌ |
| GET | `/api/files/upload/status` | 查询上传状态 | ❌ |
| POST | `/api/files/upload` | 单文件上传（直接） | ❌ |
| GET | `/api/files/download/{id}` | 下载文件（支持 Range 断点续传） | ❌ |
| GET | `/api/files/preview/{id}` | 预览文件（浏览器内联展示） | ❌ |
| GET | `/api/files/download/folder/{id}` | 下载文件夹（ZIP 打包，保持目录结构） | ❌ |
| GET | `/api/files/progress/{itemId}` | 读取媒体播放进度（视频/音频） | ❌ |
| PUT | `/api/files/progress/{itemId}` | 保存媒体播放进度 | ❌ |

### 分享模块 — `netdisk-share`

**接口前缀: `/api/shares`**

| 方法 | 路径 | 说明 | 需分页 |
|------|------|------|--------|
| POST | `/api/shares` | 创建分享 | ❌ |
| POST | `/api/shares/{id}/cancel` | 取消分享 | ❌ |
| GET | `/api/shares/mine` | 我的分享列表 | ✅ |
| GET | `/api/shares/access/{token}` | 访问分享 | ❌ |
| GET | `/api/shares/received` | 我接收的分享记录 | ✅ |
| GET | `/api/shares/access/{token}/items` | 浏览分享文件夹内容 | ✅ |
| GET | `/api/shares/access/{token}/download` | 从分享中下载文件 | ❌ |
| POST | `/api/shares/access/{token}/save` | 保存分享文件到自己的存储 | ❌ |

### 机密文件箱模块 — `netdisk-vault`

**接口前缀: `/api/vault`**

| 方法 | 路径 | 说明 | 需分页 |
|------|------|------|--------|
| POST | `/api/vault/setup` | 设置保险箱密码 | ❌ |
| POST | `/api/vault/unlock` | 解锁保险箱（BCrypt 校验，Redis 记录解锁状态） | ❌ |
| POST | `/api/vault/lock` | 锁定保险箱（清除 Redis 解锁状态） | ❌ |
| GET | `/api/vault/status` | 查询保险箱状态（是否已设密码、是否解锁） | ❌ |
| GET | `/api/vault/files/list` | 列出保险箱文件（需已解锁） | ✅ |
| POST | `/api/vault/files/folder` | 在保险箱创建文件夹 | ❌ |
| POST | `/api/vault/files/upload` | 上传文件到保险箱（自动标记 is_vaulted） | ❌ |
| GET | `/api/vault/files/download/{id}` | 从保险箱下载文件 | ❌ |
| PUT | `/api/vault/files/remove` | 从保险箱移出（清除 is_vaulted 标记） | ❌ |

### 日历模块 — `netdisk-calendar`

**接口前缀: `/api/calendar`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/calendar/today` | 获取当日农历/黄历/宜忌/分享吉日建议 |

### 相册模块 — `netdisk-album`

**接口前缀: `/api/albums`**

| 方法 | 路径 | 说明 | 需分页 |
|------|------|------|--------|
| POST | `/api/albums` | 创建相册 | ❌ |
| GET | `/api/albums` | 相册列表 | ✅ |
| GET | `/api/albums/{id}` | 相册详情（含分页照片列表） | ✅ |
| PUT | `/api/albums/{id}` | 更新相册（名称/描述/排序） | ❌ |
| DELETE | `/api/albums/{id}` | 删除相册（级联删除关联记录） | ❌ |
| POST | `/api/albums/{id}/items` | 向相册添加照片 | ❌ |
| DELETE | `/api/albums/{id}/items` | 从相册移除照片 | ❌ |
| PUT | `/api/albums/{id}/cover` | 设置相册封面 | ❌ |

### 用户模块 — `netdisk-user`

**接口前缀: `/api/users`**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users/register` | 注册 |
| POST | `/api/users/login` | 登录（返回 JWT Token） |
| GET | `/api/users/info` | 获取当前用户信息 |

### 管理模块 — `netdisk-common`

**接口前缀: `/api/admin/nodes`**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/nodes` | 获取所有存储节点 |
| GET | `/api/admin/nodes/active` | 获取可用存储节点 |
| GET | `/api/admin/nodes/{id}` | 获取节点详情 |
| POST | `/api/admin/nodes` | 新增存储节点 |
| PUT | `/api/admin/nodes/{id}` | 更新存储节点 |
| DELETE | `/api/admin/nodes/{id}` | 删除存储节点 |

---

## 分页规范

> 所有集合展示接口必须分页，禁止一次性返回全量数据。

### 请求参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | Integer | 1 | 页码，从 1 开始 |
| `size` | Integer | 20 | 每页条数，最大 100 |

### 响应格式

使用 MyBatis-Plus 的 `Page<T>` 对象，Jackson 序列化后返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [...],      // 当前页数据
    "total": 100,          // 总记录数
    "size": 20,            // 每页条数
    "current": 1,          // 当前页码
    "pages": 5             // 总页数
  }
}
```

### 实现方式

1. Controller 接收 `@RequestParam(defaultValue = "1") Integer page` 和 `@RequestParam(defaultValue = "20") Integer size`
2. Service 构建 `Page<T>` 对象传入 Mapper
3. Mapper 返回 `IPage<T>`（MyBatis-Plus 自动生成 COUNT 查询）
4. 前端通过 `records` 取数据，通过 `total` / `pages` 渲染分页器

### 已实现分页的接口

- `GET /api/files/list?parentId=&page=&size=` — **FileController.listItems**
- `GET /api/files/trash?page=&size=` — **FileController.listTrash**
- `GET /api/shares/mine?page=&size=` — **ShareController.myShares**
- `GET /api/shares/received?page=&size=` — **ShareController.receivedShares**
- `GET /api/shares/access/{token}/items?parentId=&page=&size=` — **ShareController.listShareItems**
- `GET /api/vault/files/list?parentId=&page=&size=` — **VaultController.listItems**
- `GET /api/albums?page=&size=` — **AlbumController.listAlbums**
- `GET /api/albums/{id}?page=&size=` — **AlbumController.getAlbumDetail**

---

## 关键代码结构

### 控制器层

```java
// 示例: 分页后的 Controller
@GetMapping("/list")
public R<Page<ItemVO>> listItems(
        @RequestParam(required = false) Long parentId,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size) {
    return R.ok(fileService.listItems(UserContext.getUserId(), parentId, page, size));
}
```

### 服务层

```java
// 示例: 分页后的 Service
public Page<ItemVO> listItems(Long ownerId, Long parentId, Integer page, Integer size) {
    Page<Item> pg = new Page<>(page, Math.min(size, 100));
    // 调用 Mapper 的分页查询
    return ...;
}
```

### Mapper 层

利用 MyBatis-Plus `PaginationInnerInterceptor`（已在 `MyBatisConfig` 中注册）:
- Mapper 方法参数中带 `Page<T>` 即自动分页
- 返回 `IPage<T>` 或 `List<T>`（推荐 `IPage` 以获取 total 等元信息）

---

## 运行方式

```bash
# 1. 创建数据库（需先登录 MySQL）
mysql -u root -p -e "CREATE DATABASE m78netdisk DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
# 2. 导入表结构
mysql -u root -p m78netdisk < D:/M78netdisk/database/init.sql
# 3. 启动
cd D:/M78netdisk
mvn spring-boot:run -pl netdisk-bootstrap -am
```

默认端口: 8080  
数据库: jdbc:mysql://localhost:3306/m78netdisk  
Redis: localhost:6379