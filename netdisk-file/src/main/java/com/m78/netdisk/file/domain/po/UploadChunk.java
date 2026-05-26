package com.m78.netdisk.file.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("upload_chunks")
public class UploadChunk implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Integer chunkIndex;

    private Integer size;

    private String etag;

    private String storageKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadedAt;
}