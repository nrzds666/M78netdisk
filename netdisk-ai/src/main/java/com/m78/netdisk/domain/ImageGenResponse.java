package com.m78.netdisk.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "图片生成响应")
public class ImageGenResponse {

    @Schema(description = "网盘文件 ID", example = "12345")
    private Long fileId;

    @Schema(description = "文件名", example = "ai-image-20260730-153000.png")
    private String fileName;

    @Schema(description = "图片预览/下载 URL", example = "/api/files/preview/12345")
    private String fileUrl;

    @Schema(description = "文件大小（字节）", example = "245760")
    private Long fileSize;

    @Schema(description = "图片宽度（像素），示例：512", example = "512")
    private Integer width;

    @Schema(description = "图片高度（像素），示例：512", example = "512")
    private Integer height;

    @Schema(description = "任务 ID（UUID），用于标识生图任务", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String taskId;

    @Schema(description = "任务状态: pending | completed", example = "completed")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String status;

    /**
     * 已完成状态构造函数
     */
    public ImageGenResponse(Long fileId, String fileName, String fileUrl, Long fileSize, Integer width, Integer height) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.fileSize = fileSize;
        this.width = width;
        this.height = height;
        this.taskId = null;
        this.status = "completed";
    }

    /**
     * 待处理状态构造函数
     */
    public ImageGenResponse(String taskId, Integer width, Integer height) {
        this.taskId = taskId;
        this.width = width;
        this.height = height;
        this.status = "pending";
    }
}
