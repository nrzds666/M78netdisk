package com.m78.netdisk.file.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件下载/预览信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDownloadVO {
    private String storageKey;
    private String fileName;
    private String mimeType;
    private Long fileSize;
}
