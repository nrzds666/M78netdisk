-- ============================================================
-- M78 网盘 — 数据库建表脚本
-- 引擎: MySQL 8.0
-- 日期: 2026-05-15
-- 用法: mysql -u root -p m78netdisk < init.sql
-- ============================================================

DROP DATABASE IF EXISTS m78netdisk;
CREATE DATABASE m78netdisk DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE m78netdisk;

-- ============================================================
-- 1. 用户与权限
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64) NOT NULL UNIQUE,
    email         VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    avatar_url    TEXT,
    status        TINYINT NOT NULL DEFAULT 1,  -- 1=active, 0=disabled, -1=frozen
    quota_bytes   BIGINT NOT NULL DEFAULT 10737418240,  -- 10 GB
    used_bytes    BIGINT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_users_email ON users(email);



-- ============================================================
-- 2. 文件与文件夹（核心存储）
-- ============================================================

CREATE TABLE IF NOT EXISTS items (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id      BIGINT NOT NULL,
    parent_id     BIGINT,
    name          VARCHAR(255) NOT NULL,
    is_directory  TINYINT(1) NOT NULL DEFAULT 0,

    -- 文件专用字段
    size          BIGINT NOT NULL DEFAULT 0,
    mime_type     VARCHAR(127),
    storage_key   VARCHAR(1024),
    etag          VARCHAR(64),
    thumbnail_key TEXT,

    -- 通用
    path          VARCHAR(1024) NOT NULL,
    sort_order    INT NOT NULL DEFAULT 0,
    is_deleted    TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at    DATETIME,
    version       INT NOT NULL DEFAULT 1,
    is_vaulted    TINYINT(1) NOT NULL DEFAULT 0,
    is_from_share TINYINT(1) NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 同一目录下不允许同名
    UNIQUE (owner_id, parent_id, name),
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_items_owner_parent
    ON items(owner_id, parent_id);
CREATE INDEX idx_items_owner_parent_deleted
    ON items(owner_id, parent_id, is_deleted);
CREATE INDEX idx_items_owner_parent_dir
    ON items(owner_id, parent_id, is_directory);
CREATE INDEX idx_items_storage_key
    ON items(storage_key(255));
CREATE INDEX idx_items_owner_deleted_at
    ON items(owner_id, deleted_at);
CREATE INDEX idx_items_path
    ON items(path(255));
CREATE INDEX idx_items_vaulted
    ON items(owner_id, is_vaulted, parent_id);

-- 文件版本历史
CREATE TABLE IF NOT EXISTS item_versions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    item_id     BIGINT NOT NULL,
    version     INT NOT NULL,
    size        BIGINT NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    etag        VARCHAR(64),
    created_by  BIGINT NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (item_id, version),
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_item_versions_item_id ON item_versions(item_id);
CREATE INDEX idx_item_versions_created_by ON item_versions(created_by);

-- ============================================================
-- 3. 分享
-- ============================================================

CREATE TABLE IF NOT EXISTS shares (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id       BIGINT NOT NULL,
    item_id        BIGINT NOT NULL,
    share_token    VARCHAR(32) NOT NULL UNIQUE,
    password_hash  VARCHAR(255),
    permission     VARCHAR(20) NOT NULL DEFAULT 'view',
    expire_at      DATETIME,
    max_downloads  INT,
    download_count INT NOT NULL DEFAULT 0,
    is_canceled    TINYINT(1) NOT NULL DEFAULT 0,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_shares_owner ON shares(owner_id);
CREATE INDEX idx_shares_item ON shares(item_id);
CREATE INDEX idx_shares_token ON shares(share_token);

-- ============================================================
-- 3b. 接收分享记录
-- ============================================================

CREATE TABLE IF NOT EXISTS received_shares (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    share_id      BIGINT NOT NULL,
    item_id       BIGINT NOT NULL,
    owner_id      BIGINT NOT NULL,
    access_token  VARCHAR(32) NOT NULL,
    accessed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (user_id, share_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (share_id) REFERENCES shares(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_received_shares_user ON received_shares(user_id, accessed_at DESC);

-- ============================================================
-- 4. 分片上传（断点续传）
-- ============================================================

CREATE TABLE IF NOT EXISTS upload_tasks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id        BIGINT NOT NULL,
    parent_id       BIGINT,
    file_name       VARCHAR(255) NOT NULL,
    file_size       BIGINT NOT NULL,
    mime_type       VARCHAR(127),
    chunk_size      INT NOT NULL DEFAULT 5242880,       -- 5 MB
    total_chunks    INT NOT NULL,
    received_chunks INT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    storage_prefix  TEXT NOT NULL,
    upload_id       VARCHAR(255) COMMENT 'OSS MultipartUpload uploadId',
    merged_key      VARCHAR(1024) COMMENT '合并后的 storageKey（OSS 用 CompleteMultipartUpload 写入）',
    expires_at      DATETIME NOT NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES items(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_upload_tasks_owner ON upload_tasks(owner_id, status);
CREATE INDEX idx_upload_tasks_parent ON upload_tasks(parent_id);
CREATE INDEX idx_upload_tasks_expires ON upload_tasks(expires_at);

CREATE TABLE IF NOT EXISTS upload_chunks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id     BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    part_number INT NULL,
    size INT NOT NULL,
    etag        VARCHAR(64),
    storage_key TEXT NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (task_id, chunk_index),
    FOREIGN KEY (task_id) REFERENCES upload_tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. 审计日志 / 操作记录
-- ============================================================

CREATE TABLE IF NOT EXISTS operation_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    action      VARCHAR(32) NOT NULL,
    item_id     BIGINT,
    detail      JSON,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_operation_logs_user
    ON operation_logs(user_id, created_at DESC);
CREATE INDEX idx_operation_logs_action
    ON operation_logs(action, created_at DESC);
CREATE INDEX idx_operation_logs_item
    ON operation_logs(item_id);

-- ============================================================
-- 6. 存储节点（多后端）
-- ============================================================

CREATE TABLE IF NOT EXISTS storage_nodes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,
    provider    VARCHAR(32) NOT NULL,
    endpoint    TEXT NOT NULL,
    region      VARCHAR(64),
    bucket      VARCHAR(128),
    access_key  TEXT,
    encrypted_sk TEXT,
    is_active   TINYINT(1) NOT NULL DEFAULT 1,
    weight      INT NOT NULL DEFAULT 100,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 7. 机密文件箱
-- ============================================================

CREATE TABLE IF NOT EXISTS user_vaults (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 8. 媒体播放进度
-- ============================================================

CREATE TABLE IF NOT EXISTS media_progress (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    item_id          BIGINT NOT NULL,
    progress_seconds INT NOT NULL DEFAULT 0,
    total_duration   INT NOT NULL DEFAULT 0,
    finished         TINYINT(1) NOT NULL DEFAULT 0,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE (user_id, item_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_media_progress_user ON media_progress(user_id);
CREATE INDEX idx_media_progress_item ON media_progress(item_id);


-- ============================================================
-- 9. 相册
-- ============================================================

CREATE TABLE IF NOT EXISTS albums (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    name          VARCHAR(128) NOT NULL,
    cover_item_id BIGINT,
    description   TEXT,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (cover_item_id) REFERENCES items(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_albums_user ON albums(user_id, sort_order);

CREATE TABLE IF NOT EXISTS album_items (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    album_id  BIGINT NOT NULL,
    item_id   BIGINT NOT NULL,
    added_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (album_id, item_id),
    FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_album_items_album ON album_items(album_id, added_at DESC);
CREATE INDEX idx_album_items_item ON album_items(item_id);


-- ============================================================
-- 6. 保险箱
-- ============================================================

CREATE TABLE IF NOT EXISTS user_vaults (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_user_vaults_user ON user_vaults(user_id);


-- ============================================================
-- 10. 相册分享
-- ============================================================

CREATE TABLE IF NOT EXISTS album_shares (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    album_id      BIGINT NOT NULL,
    user_id       BIGINT NOT NULL,
    share_token   VARCHAR(36) NOT NULL UNIQUE,
    expire_at     DATETIME,
    is_active     TINYINT(1) NOT NULL DEFAULT 1,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_album_shares_token ON album_shares(share_token);
CREATE INDEX idx_album_shares_album ON album_shares(album_id, is_active);

-- ============================================================
-- 完成
-- ============================================================
