package com.m78.netdisk.file.event;

import com.m78.netdisk.file.domain.po.Item;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文件重命名事件。在 Item 名称变更后发布。
 * RagRenamingListener 通过 @TransactionalEventListener(AFTER_COMMIT)
 * 在事务提交后异步清理旧 RAG 索引并用新文件名重新索引。
 */
@Getter
public class FileRenamedEvent extends ApplicationEvent {

    private final Item item;
    /** 重命名前的文件名 */
    private final String oldName;

    public FileRenamedEvent(Object source, Item item, String oldName) {
        super(source);
        this.item = item;
        this.oldName = oldName;
    }
}
