package com.m78.netdisk.album.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("album_items")
public class AlbumItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long albumId;

    private Long itemId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime addedAt;
}
