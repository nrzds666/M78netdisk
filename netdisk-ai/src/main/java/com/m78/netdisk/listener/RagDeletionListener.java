package com.m78.netdisk.listener;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.file.event.FilePermanentlyDeletedEvent;
import com.m78.netdisk.file.event.FilePermanentlyDeletedEvent.DeletedItemInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文件永久删除事件监听器。
 * 在事务提交后异步清理 RAG 知识库中的文档索引。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagDeletionListener {

    private final RagClient ragClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFilePermanentlyDeleted(FilePermanentlyDeletedEvent event) {
        if (event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }

        for (DeletedItemInfo item : event.getItems()) {
            // 文件夹不需要清理 RAG 索引（只有文件才被索引）
            if (item.isDirectory()) {
                log.debug("跳过文件夹 RAG 清理: itemId={}, name={}", item.getId(), item.getName());
                continue;
            }

            try {
                ragClient.deleteDocumentByFileName(item.getName());
                log.info("RAG 索引已清理: itemId={}, fileName={}", item.getId(), item.getName());
            } catch (Exception e) {
                log.error("RAG 索引清理失败: itemId={}, fileName={}, error={}",
                        item.getId(), item.getName(), e.getMessage());
            }
        }
    }
}
