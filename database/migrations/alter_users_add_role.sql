-- M78 NetDisk — 管理员角色迁移
-- 适用场景: 已有 users 表，升级加 role 字段
-- 执行方式: mysql -u root -p m78netdisk < alter_users_add_role.sql

ALTER TABLE users ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'user' COMMENT 'user | admin';

-- 把第一个用户设为管理员（按需修改 username）
-- UPDATE users SET role = 'admin' WHERE username = 'your_admin_username';
