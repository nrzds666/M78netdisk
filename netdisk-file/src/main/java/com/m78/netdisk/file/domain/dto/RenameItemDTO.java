package com.m78.netdisk.file.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class RenameItemDTO {
    @NotNull(message = "文件/文件夹ID不能为空")
    private Long itemId;

    @NotBlank(message = "新名称不能为空")
    private String newName;
}