package com.m78.netdisk.file.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.po.ItemVersion;
import com.m78.netdisk.file.domain.po.UploadChunk;
import com.m78.netdisk.file.domain.po.UploadTask;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.file.mapper.ItemVersionMapper;
import com.m78.netdisk.file.mapper.UploadChunkMapper;
import com.m78.netdisk.file.mapper.UploadTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件模块定时任务
 * - 清理过期未完成的上传任务
 * - 清理回收站中超过 30 天的软删除记录
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupTask {

    private final UploadTaskMapper uploadTaskMapper;
    private final UploadChunkMapper uploadChunkMapper;
    private final ItemMapper itemMapper;
    private final ItemVersionMapper itemVersionMapper;
    private final StorageService storageService;

    /**
     * 每小时清理一次过期的上传任务
     */
    @Scheduled(fixedRate = 3600_000)
    @Transactional
    public void cleanExpiredUploadTasks() {
        List<UploadTask> expiredTasks = uploadTaskMapper.selectList(
                new LambdaQueryWrapper<UploadTask>()
                        .in(UploadTask::getStatus, "pending", "uploading", "canceled")
                        .lt(UploadTask::getExpiresAt, LocalDateTime.now())
                        .last("LIMIT 1000"));

        if (expiredTasks.isEmpty()) return;

        for (UploadTask task : expiredTasks) {
            // 清理分片存储
            List<UploadChunk> chunks = uploadChunkMapper.selectList(
                    new LambdaQueryWrapper<UploadChunk>().eq(UploadChunk::getTaskId, task.getId()));
            for (UploadChunk chunk : chunks) {
                if (chunk.getStorageKey() != null) {
                    storageService.delete(chunk.getStorageKey());
                }
            }
            uploadChunkMapper.delete(
                    new LambdaQueryWrapper<UploadChunk>().eq(UploadChunk::getTaskId, task.getId()));
            // 标记任务为过期
            task.setStatus("expired");
            uploadTaskMapper.updateById(task);
            log.info("上传任务已过期: taskId={}, fileName={}", task.getId(), task.getFileName());
        }
    }

    /**
     * 每天凌晨 3 点清理回收站中超过 30 天的文件
     * 同时清理对应的磁盘文件和版本历史
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanTrashOlderThan30Days() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(30);
        List<Item> expiredItems = itemMapper.selectList(
                new LambdaQueryWrapper<Item>()
                        .eq(Item::getIsDeleted, true)
                        .lt(Item::getDeletedAt, deadline)
                        .last("LIMIT 1000"));

        if (expiredItems.isEmpty()) return;

        for (Item item : expiredItems) {
            recursivelyCleanTrashItem(item);
        }
    }

    /**
     * 递归清理回收站中的单个条目（含子目录、版本记录、磁盘存储）
     */
    private void recursivelyCleanTrashItem(Item item) {
        if (item == null) return;

        // 如果是目录，先递归清理所有子文件/子目录
        if (item.getIsDirectory()) {
            List<Item> children = itemMapper.selectList(
                    new LambdaQueryWrapper<Item>()
                            .eq(Item::getParentId, item.getId()));
            for (Item child : children) {
                recursivelyCleanTrashItem(child);
            }
        }

        // 清理 item_versions 记录和对应的磁盘存储
        List<ItemVersion> versions = itemVersionMapper.selectList(
                new LambdaQueryWrapper<ItemVersion>().eq(ItemVersion::getItemId, item.getId()));
        for (ItemVersion v : versions) {
            if (v.getStorageKey() != null) {
                try {
                    storageService.delete(v.getStorageKey());
                } catch (Exception e) {
                    log.warn("删除版本存储失败: storageKey={}", v.getStorageKey(), e);
                }
            }
        }
        itemVersionMapper.delete(
                new LambdaQueryWrapper<ItemVersion>().eq(ItemVersion::getItemId, item.getId()));

        // 清理文件本身的磁盘存储（非目录且非空 storageKey）
        if (!item.getIsDirectory() && item.getStorageKey() != null) {
            try {
                storageService.delete(item.getStorageKey());
            } catch (Exception e) {
                log.warn("删除文件存储失败: storageKey={}", item.getStorageKey(), e);
            }
        }

        // 删除 DB 记录
        itemMapper.deleteById(item.getId());
        log.info("回收站自动清理: itemId={}, name={}, deletedAt={}",
                item.getId(), item.getName(), item.getDeletedAt());
    }
}
