package com.m78.netdisk.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "AI 聊天请求")
public class ChatRequest {

    @Schema(description = "用户消息", example = "你好", required = true)
    private String message;

    @Schema(description = "对话历史（最近 N 条），每条包含 role 和 content")
    private List<HistoryMessage> history = new ArrayList<>();

    @Schema(description = "修改文档上下文（对已有临时文档继续修改时传入）")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private DocContext docContext;

    public ChatRequest() {}

    public ChatRequest(String message) {
        this.message = message;
    }

    @Data
    public static class HistoryMessage {
        @Schema(description = "角色: user / assistant", example = "user")
        private String role;
        @Schema(description = "消息内容", example = "帮我写周报")
        private String content;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DocContext {
        @Schema(description = "要修改的临时文档 ID")
        private String tempFileId;
        @Schema(description = "文件名", example = "项目周报.docx")
        private String fileName;
        @Schema(description = "文件格式", example = "docx")
        private String fileType;
        @Schema(description = "当前第几轮修改（1-based），首次生成时 round=0")
        private int round;
    }
}
