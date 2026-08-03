package com.m78.netdisk.vault.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class SetupVaultDTO {

    @NotBlank(message = "登录密码不能为空")
    private String loginPassword;

    @NotBlank(message = "保险箱密码不能为空")
    @Size(min = 6, max = 72, message = "保险箱密码长度需在6-72位之间")
    private String vaultPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
