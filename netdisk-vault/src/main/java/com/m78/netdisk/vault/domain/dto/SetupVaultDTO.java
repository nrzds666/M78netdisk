package com.m78.netdisk.vault.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class SetupVaultDTO {

    @NotBlank(message = "登录密码不能为空")
    private String loginPassword;

    @NotBlank(message = "保险箱密码不能为空")
    @Size(min = 6, max = 32, message = "保险箱密码长度需在6-32位之间")
    private String vaultPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
