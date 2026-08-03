package com.m78.netdisk.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "AI 聊天响应")
public class ChatResponse {

    @Schema(description = "AI 回复内容")
    private String reply;

    public ChatResponse() {}

    public ChatResponse(String reply) {
        this.reply = reply;
    }

}
