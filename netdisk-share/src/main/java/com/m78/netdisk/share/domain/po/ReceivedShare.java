package com.m78.netdisk.share.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("received_shares")
public class ReceivedShare implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long shareId;

    private Long itemId;

    private Long ownerId;

    private String accessToken;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime accessedAt;
}
