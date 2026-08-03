package com.m78.netdisk.controller;

import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.domain.ChatRequest;
import com.m78.netdisk.domain.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI 助手", description = "与 AI 模型对话的接口")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Operation(summary = "发送消息（GET）", description = "通过 URL 参数发送消息，适合快捷测试")
    @GetMapping("/test")
    public R<ChatResponse> completion(@RequestParam String message) {
        String reply = chatClient.prompt()
                .user(message)
                .call()
                .content();
        return R.ok(new ChatResponse(reply != null ? reply : ""));
    }

    @Operation(summary = "发送消息（POST）", description = "通过请求体发送消息，适合正式聊天场景")
    @PostMapping("/message")
    public R<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return R.fail(400, "消息不能为空");
        }
        String reply = chatClient.prompt()
                .user(request.getMessage())
                .call()
                .content();
        return R.ok(new ChatResponse(reply != null ? reply : ""));
    }
}
