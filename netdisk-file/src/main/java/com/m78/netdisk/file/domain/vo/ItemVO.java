package com.m78.netdisk.file.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemVO {
    private Long id;
    private Long ownerId;
    private Long parentId;
    private String name;
    private Boolean isDirectory;
    private Long size;
    private String mimeType;
    private String storageKey;
    private String etag;
    private String thumbnailKey;
    private Boolean isFromShare;
    private String path;
    private Integer version;
    private String createdAt;
    private String updatedAt;
    private List<ItemVO> children;
}