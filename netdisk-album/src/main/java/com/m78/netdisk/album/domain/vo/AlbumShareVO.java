package com.m78.netdisk.album.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumShareVO {
    private Long id;
    private Long albumId;
    private String shareToken;
    private String shareUrl;
    private String expireAt;
    private String createdAt;
}
