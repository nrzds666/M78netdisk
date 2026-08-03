-- ============================================================
-- Migration: 给 upload_tasks 表增加 upload_id 和 merged_key 列
-- 用于 OSS MultipartUpload 分片上传支持
-- 日期: 2026-06-12
-- 状态: 已合并入 init.sql（2026-06-17），新部署无需执行此迁移
-- ============================================================

ALTER TABLE upload_tasks
    ADD COLUMN upload_id  VARCHAR(255)  COMMENT 'OSS MultipartUpload uploadId' AFTER storage_prefix,
    ADD COLUMN merged_key VARCHAR(1024) COMMENT '合并后的 storageKey（OSS 用 CompleteMultipartUpload 写入）' AFTER upload_id;

ALTER TABLE upload_chunks
    ADD COLUMN part_number INT NULL COMMENT 'OSS MultipartUpload partNumber' AFTER chunk_index;
