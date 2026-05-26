package com.m78.netdisk.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO {
    private Long userId;
    private String username;
    private String avatarUrl;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
}