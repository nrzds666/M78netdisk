package com.m78.netdisk.file.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 文件永久删除事件。在 permanentlyDelete 方法中发布。
 * RagDeletionListener 监听此事件，清理 RAG 知识库中的索引。
 */
@Getter
public class FilePermanentlyDeletedEvent extends ApplicationEvent {

    private final Long ownerId;
    private final List<Long> itemIds;
    /** 被删除文件的信息（用于 RAG 清理，避免回查已删除的 DB 记录） */
    private final List<DeletedItemInfo> items;

    public FilePermanentlyDeletedEvent(Object source, Long ownerId, List<Long> itemIds,
                                        List<DeletedItemInfo> items) {
        super(source);
        this.ownerId = ownerId;
        this.itemIds = itemIds;
        this.items = items;
    }

    /**
     * 被永久删除的文件/文件夹快照信息。
     * 在 DB 删除前采集，供异步监听器使用。
     */
    @Getter
    public static class DeletedItemInfo {
        private final Long id;
        private final String name;
        private final String storageKey;
        private final boolean isDirectory;

        public DeletedItemInfo(Long id, String name, String storageKey, boolean isDirectory) {
            this.id = id;
            this.name = name;
            this.storageKey = storageKey;
            this.isDirectory = isDirectory;
        }
    }
}
