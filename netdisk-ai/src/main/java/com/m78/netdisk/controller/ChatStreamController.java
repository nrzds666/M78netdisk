package com.m78.netdisk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.domain.ChatRequest;
import com.m78.netdisk.domain.ImageGenResponse;
import com.m78.netdisk.domain.TempDocumentVO;
import com.m78.netdisk.domain.ImageGenResponse;
import com.m78.netdisk.service.AiDocumentService;
import com.m78.netdisk.service.ImageGenerationService;
import com.m78.netdisk.service.StreamChatService;
import com.m78.netdisk.service.StreamChatService.GenDocTag;
import com.m78.netdisk.service.StreamChatService.GenImgTag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 聊天 SSE 流式接口。
 */
@Slf4j
@Tag(name = "AI 助手(流式)", description = "基于 SSE 的流式 AI 对话接口")
@RestController
@RequestMapping("/api/chat")
public class ChatStreamController {

    private final StreamChatService streamChatService;
    private final AiDocumentService aiDocumentService;
    private final ImageGenerationService imageGenerationService;
    private final ObjectMapper objectMapper;

    public ChatStreamController(StreamChatService streamChatService,
                                AiDocumentService aiDocumentService,
                                ImageGenerationService imageGenerationService,
                                ObjectMapper objectMapper) {
        this.streamChatService = streamChatService;
        this.aiDocumentService = aiDocumentService;
        this.imageGenerationService = imageGenerationService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "流式聊天（SSE）", description = "通过 SSE 逐字返回 AI 回复，首条为 RAG 增强状态标记")
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
                                if (chunk.startsWith("[RAG:")) {
                                    emitter.send(SseEmitter.event()
                                            .name("rag-status")
                                            .data(toJson(new RagStatusChunk("[RAG:ON]".equals(chunk)))));
                                    return;  // RAG 标记不拼入 fullReply
                                }
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
                                // onComplete: 扫描完整回复提取 [GEN_DOC:] 标记
                                String reply = fullReply.get();
                                List<GenDocTag> tags = streamChatService.extractGenDocTags(reply);
                                log.info("SSE onComplete: replyLen={}, docTags={}", reply.length(), tags.size());

                                for (GenDocTag tag : tags) {
                                    try {
                                        TempDocumentVO doc = aiDocumentService.generateTempDocument(
                                                userId, tag.body(), tag.title(), tag.format(),
                                                request.getDocContext());
                                        // 1. doc-generated named event（必须在 done 之前）
                                        emitter.send(SseEmitter.event()
                                                .name("doc-generated")
                                                .data(objectMapper.writeValueAsString(doc)));
                                        log.info("doc-generated 事件已发送: {}", doc.getTempFileId());
                                    } catch (Exception e) {
                                        log.error("文档生成失败: {}", e.getMessage());
                                        // 仍发送错误提示，不阻塞对话流
                                        emitter.send(SseEmitter.event().data(
                                                toJson(new StreamChunk("文档生成失败，请重试", false))));
                                    }
                                }

                                // 提取 [GEN_IMG:] 标记，生图（在 doc-generated 之后、done 之前）
                                GenImgTag imgTag = streamChatService.extractGenImgTag(reply);
                                if (imgTag != null) {
                                    try {
                                        ImageGenResponse imgResponse = imageGenerationService.generate(
                                                imgTag.prompt(),
                                                imgTag.negativePrompt(),
                                                imgTag.width(),
                                                imgTag.height(),
                                                userId
                                        );
                                        emitter.send(SseEmitter.event()
                                                .name("img-generated")
                                                .data(objectMapper.writeValueAsString(imgResponse)));
                                        log.info("img-generated 事件已发送: fileId={}", imgResponse.getFileId());
                                    } catch (Exception e) {
                                        log.error("图片生成失败: {}", e.getMessage());
                                        emitter.send(SseEmitter.event().data(
                                                toJson(new StreamChunk("图片生成失败: " + e.getMessage(), false))));
                                    }
                                }

                                // 2. done event
                                emitter.send(SseEmitter.event().data(
                                        toJson(new StreamChunk("", true))));
                                // 3. complete
                                emitter.complete();
                                log.info("SSE 流式输出完成");
                            } catch (Exception e) {
                                log.error("SSE onComplete 处理异常: {}", e.getMessage(), e);
                                try {
                                    emitter.send(SseEmitter.event().data(
                                            toJson(new StreamChunk("", true))));
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

    private String toJson(StreamChunk chunk) {
        try {
            return objectMapper.writeValueAsString(chunk);
        } catch (Exception e) {
            log.error("JSON 序列化失败", e);
            return "{\"chunk\":\"\",\"done\":true}";
        }
    }

    private String toJson(RagStatusChunk chunk) {
        try {
            return objectMapper.writeValueAsString(chunk);
        } catch (Exception e) {
            log.error("RagStatusChunk JSON 序列化失败", e);
            return "{\"ragActive\":false}";
        }
    }

    public record StreamChunk(String chunk, boolean done) {}

    public record RagStatusChunk(boolean ragActive) {}
}
