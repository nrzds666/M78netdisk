package com.m78.netdisk.vault.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UnlockVaultDTO {

    @NotBlank(message = "保险箱密码不能为空")
    private String password;
}
