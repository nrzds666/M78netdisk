package com.m78.netdisk.album.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumItemVO {
    private Long itemId;
    private String name;
    private String mimeType;
    private Long size;
    private String thumbnailKey;
    private String addedAt;
}
