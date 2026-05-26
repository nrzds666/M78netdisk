package com.m78.netdisk.user.service;

import com.m78.netdisk.user.domain.dto.LoginFormDTO;
import com.m78.netdisk.user.domain.dto.RegisterFormDTO;
import com.m78.netdisk.user.domain.vo.UserInfoVO;
import com.m78.netdisk.user.domain.vo.UserLoginVO;

public interface IUserService {

    UserLoginVO register(RegisterFormDTO formDTO);

    UserLoginVO login(LoginFormDTO formDTO);

    UserLoginVO refreshToken(String refreshToken);

    UserInfoVO getUserInfo(Long userId);

    void updatePassword(Long userId, String oldPassword, String newPassword);

    void updateAvatar(Long userId, String avatarUrl);

    void logout(Long userId);
}