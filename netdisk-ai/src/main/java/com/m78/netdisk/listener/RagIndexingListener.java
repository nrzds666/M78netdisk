package com.m78.netdisk.listener;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.event.FileCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文件创建事件监听器 — 将文件索引到 RAG 知识库。
 * 在事务提交后异步执行，失败不影响文件上传主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIndexingListener {

    private final RagClient ragClient;
    private final RagServiceProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileCreated(FileCreatedEvent event) {
        Item item = event.getItem();

        // 只对支持 RAG 索引的文件类型
        String ext = getFileExtension(item.getName());
        if (!properties.getSupportedExtensions().contains(ext)) {
            log.debug("跳过 RAG 索引（不支持的文件类型）: {}, itemId={}", item.getName(), item.getId());
            return;
        }

        log.info("开始索引文件到 RAG: {}, itemId={}", item.getName(), item.getId());

        try {
            byte[] fileBytes = ragClient.downloadFromStorage(item.getStorageKey());
            if (fileBytes == null || fileBytes.length == 0) {
                log.warn("文件内容为空，跳过 RAG 索引: {}", item.getName());
                return;
            }
            ragClient.indexFile(item.getName(), fileBytes, item.getOwnerId());
        } catch (Exception e) {
            log.error("RAG 索引失败: {}, itemId={}, error={}", item.getName(), item.getId(), e.getMessage());
        }
    }

    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot <= 0) ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
