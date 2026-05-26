package com.m78.netdisk.file.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("items")
public class Item implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
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

    private String path;

    private Integer sortOrder;

    private Boolean isDeleted;

    private LocalDateTime deletedAt;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}