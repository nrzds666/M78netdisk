package com.m78.netdisk.file.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class InitUploadDTO {
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    private Long fileSize;

    private String mimeType;

    @NotNull(message = "目标目录ID不能为空")
    private Long parentId;

    private Integer chunkSize = 5242880; // 默认 5MB
}