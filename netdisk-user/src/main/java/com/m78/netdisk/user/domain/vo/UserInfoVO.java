package com.m78.netdisk.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoVO {
    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private Integer status;
    private Long quotaBytes;
    private Long usedBytes;
    private String role;
    private String createdAt;
}