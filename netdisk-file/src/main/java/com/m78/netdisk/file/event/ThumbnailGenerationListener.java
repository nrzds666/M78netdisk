package com.m78.netdisk.file.event;

import com.m78.netdisk.file.service.impl.MediaProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文件创建事件监听器。
 * 在事务提交后（AFTER_COMMIT）异步生成缩略图。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailGenerationListener {

    private final MediaProcessingService mediaProcessingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileCreated(FileCreatedEvent event) {
        log.debug("收到文件创建事件，开始异步缩略图生成: itemId={}", event.getItem().getId());
        mediaProcessingService.generateThumbnail(event.getItem());
    }
}
