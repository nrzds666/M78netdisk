package com.m78.netdisk.common.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("storage_nodes")
public class StorageNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String name;

    private String provider;

    private String endpoint;

    private String region;

    private String bucket;

    private String accessKey;

    private String encryptedSk;

    private Boolean isActive;

    private Integer weight;

    private LocalDateTime createdAt;
}
