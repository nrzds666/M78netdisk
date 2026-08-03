package com.m78.netdisk.share.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class CreateShareDTO {
    @NotNull(message = "文件/文件夹ID不能为空")
    private Long itemId;

    @Size(min = 4, max = 32, message = "提取码长度需在4-32位之间")
    private String password;

    private String permission = "view";

    private String expireType = "PERMANENT";

    @Positive(message = "最大下载次数必须为正数")
    private Integer maxDownloads;
}