# M78 网盘 — 数据库设计文档

> 设计日期：2026-05-15
> 引擎：MySQL 8.0（支持递归 CTE、JSON、函数索引）
> 字符集：UTF-8
> 项目路径：D:\M78netdisk

---

## 1. 用户与权限

### users — 用户表

```sql
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64) NOT NULL UNIQUE,
    email         VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    avatar_url    TEXT,
    status        TINYINT NOT NULL DEFAULT 1,  -- 1=active, 0=disabled, -1=frozen
    quota_bytes   BIGINT NOT NULL DEFAULT 10737418240,  -- 默认 10GB
    used_bytes    BIGINT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT now(),
    updated_at    DATETIME NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_email ON users(email);
```

### user_tokens — 登录令牌 / API Token

```sql
CREATE TABLE user_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(64) NOT NULL UNIQUE,
    type       VARCHAR(20) NOT NULL DEFAULT 'access',
    -- type: access, refresh, api
    expires_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_tokens_user_id ON user_tokens(user_id);
```

---

## 2. 文件与文件夹（核心存储）

### 核心设计思路

- **扁平存储 + 树形路径**：所有文件和目录统一存在 `items` 表中，通过 `parent_id` + `owner_id` 构建树。
- 文件夹的 `is_directory = true`，`size` 为 0。
- `path` 字段为物化路径（如 `/documents/photos`），用于加速显示，不做查询主依赖。
- 删除采用软删除 + 回收站机制。

### items — 文件/文件夹主表

```sql
CREATE TABLE items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id     BIGINT REFERENCES items(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    is_directory  TINYINT(1) NOT NULL DEFAULT 0,

    -- 文件专用字段
    size          BIGINT NOT NULL DEFAULT 0,        -- 字节数（目录为 0）
    mime_type     VARCHAR(127),
    storage_key   TEXT,                              -- 对象存储 key（目录为 NULL）
    etag          VARCHAR(64),                       -- 文件 MD5 / SHA256
    thumbnail_key TEXT,                              -- 缩略图存储 key

    -- 通用
    path          TEXT NOT NULL,                     -- 物化路径，如 /documents/photos/vacation.jpg
    sort_order    INT NOT NULL DEFAULT 0,
    is_deleted    TINYINT(1) NOT NULL DEFAULT 0,    -- 软删除
    deleted_at    DATETIME,
    version       INT NOT NULL DEFAULT 1,            -- 当前版本号
    created_at    DATETIME NOT NULL DEFAULT now(),
    updated_at    DATETIME NOT NULL DEFAULT now(),

    -- 约束：同一目录下不能有同名文件/文件夹
    UNIQUE (owner_id, parent_id, name)
);

-- 加速查询
CREATE INDEX idx_items_owner_parent ON items(owner_id, parent_id);
CREATE INDEX idx_items_owner_parent_deleted ON items(owner_id, parent_id, is_deleted);
CREATE INDEX idx_items_owner_parent_dir ON items(owner_id, parent_id, is_directory);
CREATE INDEX idx_items_storage_key ON items(storage_key);
CREATE INDEX idx_items_deleted_at ON items(owner_id, deleted_at);
```

### item_versions — 文件版本历史

```sql
CREATE TABLE item_versions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id     BIGINT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    version     INT NOT NULL,
    size        BIGINT NOT NULL,
    storage_key TEXT NOT NULL,
    etag        VARCHAR(64),
    created_by  BIGINT NOT NULL REFERENCES users(id),
    created_at  DATETIME NOT NULL DEFAULT now(),

    UNIQUE (item_id, version)
);
CREATE INDEX idx_item_versions_item_id ON item_versions(item_id);
```

---

## 3. 分享

### shares — 分享链接

```sql
CREATE TABLE shares (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    item_id       BIGINT NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    share_token   VARCHAR(32) NOT NULL UNIQUE,       -- 短 token，用于分享链接
    password_hash VARCHAR(255),                       -- 提取码（可选）
    permission    VARCHAR(20) NOT NULL DEFAULT 'view', -- view | download | edit
    expire_at     DATETIME,                        -- 过期时间（NULL=永久）
    max_downloads INT,                                -- 最大下载次数（NULL=不限）
    download_count INT NOT NULL DEFAULT 0,
    is_canceled   TINYINT(1) NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT now()
);
CREATE INDEX idx_shares_owner ON shares(owner_id);
CREATE INDEX idx_shares_item ON shares(item_id);
CREATE INDEX idx_shares_token ON shares(share_token);
```

---

## 4. 回收站

### 回收站逻辑

- 软删除：`items.is_deleted = true`, `deleted_at = now()`
- 自动清理：定时任务删除 `deleted_at < now() - INTERVAL 30 DAY` 的记录及对应存储文件
- 恢复：设置 `is_deleted = false, deleted_at = NULL`
- 回收站独立表只在需要记录"原始路径"以支持恢复时使用（简化方案直接用 items 表中的字段）

---

## 5. 上传任务

### upload_tasks — 断点续传 / 分片上传

```sql
CREATE TABLE upload_tasks (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id      BIGINT REFERENCES items(id),
    file_name      VARCHAR(255) NOT NULL,
    file_size      BIGINT NOT NULL,
    mime_type      VARCHAR(127),
    chunk_size     INT NOT NULL DEFAULT 5242880,     -- 5MB
    total_chunks   INT NOT NULL,
    received_chunks INT NOT NULL DEFAULT 0,
    status         VARCHAR(20) NOT NULL DEFAULT 'pending',
    -- status: pending, uploading, completed, expired, canceled
    storage_prefix TEXT NOT NULL,                     -- 分片临时存储前缀
    expires_at     DATETIME NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT now(),
    updated_at     DATETIME NOT NULL DEFAULT now()
);
CREATE INDEX idx_upload_tasks_owner ON upload_tasks(owner_id, status);
```

### upload_chunks — 分片记录

```sql
CREATE TABLE upload_chunks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT NOT NULL REFERENCES upload_tasks(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    size        INT NOT NULL,
    etag        VARCHAR(64),
    storage_key TEXT NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT now(),

    UNIQUE (task_id, chunk_index)
);
```

---

## 6. 审计日志 / 操作记录

```sql
CREATE TABLE operation_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action      VARCHAR(32) NOT NULL,
    -- action: upload, download, delete, restore, rename, move, share, trash
    item_id     BIGINT REFERENCES items(id),
    detail      JSON,                               -- 额外信息（原路径、目标路径等）
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    created_at  DATETIME NOT NULL DEFAULT now()
);
CREATE INDEX idx_operation_logs_user ON operation_logs(user_id, created_at DESC);
CREATE INDEX idx_operation_logs_action ON operation_logs(action, created_at DESC);
```

---

## 7. 存储节点（多机房 / 多存储后端）

```sql
CREATE TABLE storage_nodes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,         -- node-01, oss-cn-beijing
    provider    VARCHAR(32) NOT NULL,                 -- local, s3, oss, cos, minio
    endpoint    TEXT NOT NULL,
    region      VARCHAR(64),
    bucket      VARCHAR(128),
    access_key  TEXT,
    encrypted_sk TEXT,                                -- 加密存储的 SecretKey
    is_active   TINYINT(1) NOT NULL DEFAULT 1,
    weight      INT NOT NULL DEFAULT 100,             -- 调度权重
    created_at  DATETIME NOT NULL DEFAULT now()
);
```

---

## 关键查询示例

### 获取用户根目录下的文件和文件夹

```sql
SELECT * FROM items
WHERE owner_id = ? AND parent_id IS NULL AND NOT is_deleted
ORDER BY is_directory DESC, sort_order ASC, name ASC;
```

### 获取某个文件夹下所有内容（递归）

```sql
WITH RECURSIVE subtree AS (
    SELECT * FROM items WHERE id = ?
    UNION ALL
    SELECT i.* FROM items i
    INNER JOIN subtree s ON i.parent_id = s.id
    WHERE NOT i.is_deleted
)
SELECT * FROM subtree WHERE NOT is_deleted ORDER BY path;
```

### 空间使用统计

```sql
SELECT owner_id, SUM(size) AS total_used
FROM items
WHERE NOT is_deleted AND NOT is_directory
GROUP BY owner_id;
```

### 分享链接校验

```sql
SELECT s.*, i.name, i.mime_type, i.size, i.storage_key
FROM shares s
JOIN items i ON i.id = s.item_id
WHERE s.share_token = ?
  AND NOT s.is_canceled
  AND (s.expire_at IS NULL OR s.expire_at > now())
  AND (s.max_downloads IS NULL OR s.download_count < s.max_downloads)
  AND NOT i.is_deleted;
```

---

## 设计决策说明

| 决策 | 理由 |
|------|------|
| 单表 `items` 存文件和目录 | 简化树形查询，避免 JOIN 两张表；`is_directory` 字段区分类型 |
| 物化路径 `path` | 避免每次都要递归构建路径显示，写入时维护成本换读取速度 |
| `items` 软删除 | 回收站不需要额外表，恢复只需 `UPDATE is_deleted = false` |
| 版本独立表 | 版本元数据与当前文件隔离，支持回滚 |
| `UNIQUE (owner_id, parent_id, name)` | 在数据库层保证同一目录下无重名 |
| 分片上传独立表 | 对大文件友好，支持断点续传，上传完成后再写入 items |
| 操作日志 JSON detail | 灵活记录各操作的不同上下文（如 mv 的 src/dest） |
