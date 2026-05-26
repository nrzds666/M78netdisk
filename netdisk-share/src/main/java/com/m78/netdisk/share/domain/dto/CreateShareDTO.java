package com.m78.netdisk.share.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

@Data
public class CreateShareDTO {
    @NotNull(message = "文件/文件夹ID不能为空")
    private Long itemId;

    @Size(min = 4, max = 32, message = "提取码长度需在4-32位之间")
    private String password;

    private String permission = "view";

    @Positive(message = "过期时间必须为正数")
    private Long expireHours;

    @Positive(message = "最大下载次数必须为正数")
    private Integer maxDownloads;
}