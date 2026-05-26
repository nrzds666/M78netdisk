package com.m78.netdisk.common.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class StorageNodeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String provider;

    private String endpoint;

    private String region;

    private String bucket;

    private Boolean isActive;

    private Integer weight;

    private LocalDateTime createdAt;
}
