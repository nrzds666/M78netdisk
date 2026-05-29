package com.m78.netdisk.album.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class AddItemsDTO {

    @NotEmpty(message = "文件ID列表不能为空")
    private List<Long> itemIds;
}
