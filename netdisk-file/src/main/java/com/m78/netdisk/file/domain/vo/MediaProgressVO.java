package com.m78.netdisk.file.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaProgressVO {
    private Long itemId;
    private Integer progressSeconds;
    private Integer totalDuration;
    private Boolean finished;
    private String updatedAt;
}
