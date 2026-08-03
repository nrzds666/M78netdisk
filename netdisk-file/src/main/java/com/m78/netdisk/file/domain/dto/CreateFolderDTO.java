package com.m78.netdisk.file.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class CreateFolderDTO {
    /**
     * 父目录ID，传 null 或 0 表示在根目录下创建
     */
    private Long parentId;

    @NotBlank(message = "文件夹名称不能为空")
    private String name;
}