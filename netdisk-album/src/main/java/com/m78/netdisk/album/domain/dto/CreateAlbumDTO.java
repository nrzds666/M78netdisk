package com.m78.netdisk.album.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class CreateAlbumDTO {

    @NotBlank(message = "相册名称不能为空")
    @Size(max = 128, message = "相册名称最长128个字符")
    private String name;

    private Long coverItemId;

    @Size(max = 1000)
    private String description;

    private List<Long> itemIds;
}
