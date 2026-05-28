package com.m78.netdisk.vault.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultStatusVO {
    private Boolean enabled;
    private Boolean unlocked;
}
