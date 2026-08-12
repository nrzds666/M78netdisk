package com.m78.netdisk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.domain.ChatRequest;
import com.m78.netdisk.domain.ImageGenResponse;
import com.m78.netdisk.domain.TempDocumentVO;
import com.m78.netdisk.service.StreamChatService;
import com.m78.netdisk.tool.NetdiskTools;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 聊天 SSE 流式接口（工具调用模式）。
 */
@Slf4j
@Tag(name = "AI 助手(流式)", description = "基于 SSE 的流式 AI 对话接口（工具调用模式）")
@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private final StreamChatService streamChatService;
    private final NetdiskTools netdiskTools;
    private final ObjectMapper objectMapper;

    public ChatStreamController(StreamChatService streamChatService,
                                NetdiskTools netdiskTools,
                                ObjectMapper objectMapper) {
        this.streamChatService = streamChatService;
        this.netdiskTools = netdiskTools;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "流式聊天（SSE）", description = "通过 SSE 逐字返回 AI 回复，工具调用结果通过 named event 返回")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);
        String message = request.getMessage();
        Long userId = UserContext.getUserId();

        if (message == null || message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().data(toJson(new StreamChunk("消息不能为空", true))));
                emitter.complete();
            } catch (Exception e) {
                log.error("发送空消息错误失败", e);
                emitter.completeWithError(e);
            }
            return emitter;
        }

        AtomicReference<String> fullReply = new AtomicReference<>("");

        Disposable subscription = streamChatService.streamChat(
                        message, userId, request.getHistory(), request.getDocContext())
                .subscribe(
                        chunk -> {
                            try {
                                fullReply.updateAndGet(v -> v + chunk);
                                emitter.send(SseEmitter.event().data(toJson(new StreamChunk(chunk, false))));
                            } catch (Exception e) {
                                log.error("SSE 发送 chunk 失败: {}", e.getMessage(), e);
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("SSE 流式输出异常: {}", error.getMessage(), error);
                            try {
                                emitter.send(SseEmitter.event().data(
                                        toJson(new StreamChunk("服务暂时不可用，请稍后重试", true))));
                            } catch (Exception ignored) {}
                            emitter.complete();
                        },
                        () -> {
                            try {
                                String reply = fullReply.get();
                                log.info("SSE onComplete: replyLen={}", reply.length());

                                // 检查 pending 图片任务，发送 img-generated 事件
                                Map<String, ImageGenResponse> pendingImages = netdiskTools.getPendingImageTasks();
                                for (Map.Entry<String, ImageGenResponse> entry : pendingImages.entrySet()) {
                                    ImageGenResponse resp = entry.getValue();
                                    if ("completed".equals(resp.getStatus())) {
                                        try {
                                            emitter.send(SseEmitter.event()
                                                    .name("img-generated")
                                                    .data(objectMapper.writeValueAsString(resp)));
                                            log.info("img-generated 事件已发送: taskId={}, fileId={}",
                                                    entry.getKey(), resp.getFileId());
                                        } catch (Exception e) {
                                            log.error("img-generated 事件发送失败: taskId={}", entry.getKey(), e);
                                        }
                                        netdiskTools.completeImageTask(entry.getKey());
                                    }
                                }

                                // 检查 pending 文档任务，发送 doc-generated 事件
                                Map<String, TempDocumentVO> pendingDocs = netdiskTools.getPendingDocTasks();
                                for (Map.Entry<String, TempDocumentVO> entry : pendingDocs.entrySet()) {
                                    TempDocumentVO doc = entry.getValue();
                                    try {
                                        emitter.send(SseEmitter.event()
                                                .name("doc-generated")
                                                .data(objectMapper.writeValueAsString(doc)));
                                        log.info("doc-generated 事件已发送: tempFileId={}", doc.getTempFileId());
                                    } catch (Exception e) {
                                        log.error("doc-generated 事件发送失败: tempFileId={}", doc.getTempFileId(), e);
                                    }
                                    netdiskTools.completeDocTask(entry.getKey());
                                }

                                // done event
                                emitter.send(SseEmitter.event().data(toJson(new StreamChunk("", true))));
                                emitter.complete();
                                log.info("SSE 流式输出完成");
                            } catch (Exception e) {
                                log.error("SSE onComplete 处理异常: {}", e.getMessage(), e);
                                try {
                                    emitter.send(SseEmitter.event().data(toJson(new StreamChunk("", true))));
                                } catch (Exception ignored) {}
                                emitter.complete();
                            }
                        }
                );

        emitter.onCompletion(() -> { subscription.dispose(); log.debug("SSE 连接已关闭"); });
        emitter.onTimeout(() -> { subscription.dispose(); log.warn("SSE 连接超时"); });
        emitter.onError(err -> { subscription.dispose(); log.error("SSE 连接异常: {}", err.getMessage()); });

        return emitter;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("JSON 序列化失败", e);
            return "{\"chunk\":\"\",\"done\":true}";
        }
    }

    public record StreamChunk(String chunk, boolean done) {}
}
