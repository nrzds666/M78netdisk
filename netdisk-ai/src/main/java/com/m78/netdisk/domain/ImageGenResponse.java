package com.m78.netdisk.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
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
}
