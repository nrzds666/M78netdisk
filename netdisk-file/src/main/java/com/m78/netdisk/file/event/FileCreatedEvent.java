package com.m78.netdisk.file.event;

import com.m78.netdisk.file.domain.po.Item;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文件创建事件。在 Item 插入数据库后发布。
 * ThumbnailGenerationListener 通过 @TransactionalEventListener(AFTER_COMMIT)
 * 在事务提交后异步生成缩略图。
 */
@Getter
public class FileCreatedEvent extends ApplicationEvent {

    private final Item item;

    public FileCreatedEvent(Object source, Item item) {
        super(source);
        this.item = item;
    }
}
