package com.m78.netdisk.album.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumVO {
    private Long id;
    private String name;
    private Long coverItemId;
    private String coverThumbnailKey;
    private String description;
    private Integer itemCount;
    private Integer sortOrder;
    private String createdAt;
    private String updatedAt;
    private List<AlbumItemVO> items;
}
