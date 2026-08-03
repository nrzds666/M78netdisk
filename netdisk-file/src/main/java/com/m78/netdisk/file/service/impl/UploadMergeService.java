package com.m78.netdisk.file.service.impl;

import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.event.FileCreatedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.m78.netdisk.file.domain.po.ItemVersion;
import com.m78.netdisk.file.domain.po.UploadChunk;
import com.m78.netdisk.file.domain.po.UploadTask;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.file.mapper.ItemVersionMapper;
import com.m78.netdisk.file.mapper.UploadChunkMapper;
import com.m78.netdisk.file.mapper.UploadTaskMapper;
import com.m78.netdisk.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 分片合并服务。
 * 提取为独立 @Service 确保 @Async 通过 AOP 代理生效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadMergeService {

    private final ItemMapper itemMapper;
    private final ItemVersionMapper itemVersionMapper;
    private final UploadTaskMapper uploadTaskMapper;
    private final UploadChunkMapper uploadChunkMapper;
    private final StorageService storageService;
    private final UserMapper userMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 异步执行分片合并 + 物品创建（新事务）。
     * 提取到独立 Service 中，避免 self-invocation 绕过 @Async 代理。
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void performMerge(Long ownerId, Long taskId) {
        try {
            UploadTask task = uploadTaskMapper.selectById(taskId);
            if (task == null) return;

            List<UploadChunk> chunks = uploadChunkMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UploadChunk>()
                            .eq(UploadChunk::getTaskId, taskId)
                            .orderByAsc(UploadChunk::getChunkIndex));
            String mergedStorageKey = mergeChunks(chunks, task.getFileName());
            String path = buildPath(ownerId, task.getParentId(), task.getFileName());

            Item item = new Item()
                    .setOwnerId(ownerId)
                    .setParentId(task.getParentId())
                    .setName(task.getFileName())
                    .setIsDirectory(false)
                    .setSize(task.getFileSize())
                    .setMimeType(task.getMimeType())
                    .setStorageKey(mergedStorageKey)
                    .setPath(path)
                    .setVersion(1);

            itemMapper.insert(item);

            ItemVersion version = new ItemVersion()
                    .setItemId(item.getId())
                    .setVersion(1)
                    .setSize(task.getFileSize())
                    .setStorageKey(mergedStorageKey)
                    .setCreatedBy(ownerId);
            itemVersionMapper.insert(version);

            // 原子增用量，超配额则回滚
            if (userMapper.tryAddUsedBytes(ownerId, task.getFileSize()) == 0) {
                itemMapper.deleteById(item.getId());
                itemVersionMapper.deleteById(version.getId());
                throw new BizException("存储空间不足，无法完成上传");
            }

            task.setStatus("completed");
            uploadTaskMapper.updateById(task);

            if (eventPublisher != null) {
                eventPublisher.publishEvent(new FileCreatedEvent(this, item));
            }
            log.info("上传完成(异步): userId={}, fileName={}, size={}, itemId={}",
                    ownerId, task.getFileName(), task.getFileSize(), item.getId());
        } catch (Exception e) {
            log.error("异步合并失败: taskId={}", taskId, e);
            UploadTask task = uploadTaskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("failed");
                uploadTaskMapper.updateById(task);
            }
        }
    }

    /**
     * 流式合并分片：逐个读取分片 InputStream 写入 StorageService，O(1) 内存。
     */
    private String mergeChunks(List<UploadChunk> chunks, String fileName) {
        if (chunks == null || chunks.isEmpty()) {
            throw new BizException("没有分片可合并");
        }
        String mergedKey = "merged/" + UUID.randomUUID().toString().replace("-", "") + "/" + fileName;

        // 懒加载 InputStream：逐个打开并读取分片，不一次性打开所有句柄
        java.util.Iterator<UploadChunk> it = chunks.iterator();
        InputStream combined = new InputStream() {
            private InputStream current;

            private InputStream nextStream() {
                while (it.hasNext()) {
                    UploadChunk chunk = it.next();
                    if (chunk.getStorageKey() != null) {
                        return storageService.getInputStream(chunk.getStorageKey());
                    }
                }
                return null;
            }

            @Override
            public int read() throws IOException {
                byte[] b = new byte[1];
                return read(b, 0, 1) == -1 ? -1 : b[0] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                while (true) {
                    if (current == null) {
                        current = nextStream();
                        if (current == null) return -1;
                    }
                    int n = current.read(b, off, len);
                    if (n != -1) return n;
                    current.close();
                    current = null;
                }
            }

            @Override
            public void close() throws IOException {
                if (current != null) {
                    current.close();
                    current = null;
                }
            }
        };

        try {
            storageService.store(mergedKey, combined);
            log.debug("分片合并完成(懒加载流式): mergedKey={}, chunks={}", mergedKey, chunks.size());
            return mergedKey;
        } catch (Exception e) {
            throw new RuntimeException("合并分片失败", e);
        }
    }

    private String buildPath(Long ownerId, Long parentId, String name) {
        if (parentId == null) return "/" + name;
        Item parent = itemMapper.selectById(parentId);
        if (parent == null) return "/" + name;
        return (parent.getPath() != null ? parent.getPath() : "") + "/" + name;
    }
}
