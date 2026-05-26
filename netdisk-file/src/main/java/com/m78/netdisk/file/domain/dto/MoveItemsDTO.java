package com.m78.netdisk.file.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class MoveItemsDTO {
    @NotNull(message = "文件ID列表不能为空")
    private List<Long> itemIds;

    private Long targetParentId;
}