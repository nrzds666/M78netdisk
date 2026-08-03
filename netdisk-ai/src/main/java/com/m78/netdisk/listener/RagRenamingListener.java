package com.m78.netdisk.listener;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.event.FileRenamedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文件重命名事件监听器。
 * 在事务提交后异步清理旧 RAG 索引并用新文件名重新索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagRenamingListener {

    private final RagClient ragClient;
    private final RagServiceProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileRenamed(FileRenamedEvent event) {
        Item item = event.getItem();
        String oldName = event.getOldName();

        // 始终清理旧索引（旧文件名可能曾受支持，但新文件名不再支持）
        ragClient.deleteDocumentByFileName(oldName);

        // 对新文件名：仅当扩展名受支持时才重新索引
        String ext = getFileExtension(item.getName());
        if (!properties.getSupportedExtensions().contains(ext)) {
            log.debug("跳过 RAG 重索引（不支持的文件类型）: {}, itemId={}", item.getName(), item.getId());
            return;
        }

        log.info("开始重命名文件 RAG 索引: {} -> {}, itemId={}", oldName, item.getName(), item.getId());

        try {
            // 下载文件并用新文件名重新索引
            byte[] fileBytes = ragClient.downloadFromStorage(item.getStorageKey());
            if (fileBytes == null || fileBytes.length == 0) {
                log.warn("文件内容为空，跳过 RAG 重索引: {}", item.getName());
                return;
            }
            ragClient.indexFile(item.getName(), fileBytes, item.getOwnerId());
            log.info("文件 RAG 索引重命名完成: itemId={}", item.getId());
        } catch (Exception e) {
            log.error("RAG 索引重命名失败: {}, itemId={}, error={}", item.getName(), item.getId(), e.getMessage());
        }
    }

    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot <= 0) ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
