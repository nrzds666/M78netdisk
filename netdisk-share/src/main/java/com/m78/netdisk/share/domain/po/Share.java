package com.m78.netdisk.share.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("shares")
public class Share implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private Long itemId;

    private String shareToken;

    private String passwordHash;

    private String permission;

    private LocalDateTime expireAt;

    private Integer maxDownloads;

    private Integer downloadCount;

    private Boolean isCanceled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}