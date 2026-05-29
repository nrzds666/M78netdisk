package com.m78.netdisk.share.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareVO {
    private Long id;
    private Long ownerId;
    private Long itemId;
    private String shareToken;
    private String permission;
    private Boolean hasPassword;
    private String expireAt;
    private Integer maxDownloads;
    private Integer downloadCount;
    private Boolean isCanceled;
    private String createdAt;
    // 文件信息
    private String fileName;
    private Boolean isDirectory;
    private Long fileSize;
    private String mimeType;

    // 过期标签
    private String expireLabel;

    // 分类标记
    private Boolean isReceived;

    // 来源标记：是否为接收的分享文件
    private Boolean isFromShare;
}