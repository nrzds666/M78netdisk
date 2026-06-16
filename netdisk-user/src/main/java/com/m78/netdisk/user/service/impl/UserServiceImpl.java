package com.m78.netdisk.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.utils.JwtTool;
import com.m78.netdisk.user.domain.dto.LoginFormDTO;
import com.m78.netdisk.user.domain.dto.RegisterFormDTO;
import com.m78.netdisk.user.domain.po.User;
import com.m78.netdisk.user.domain.vo.UserInfoVO;
import com.m78.netdisk.user.domain.vo.UserLoginVO;
import com.m78.netdisk.user.mapper.UserMapper;
import com.m78.netdisk.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements IUserService {

    private final UserMapper userMapper;
    private final JwtTool jwtTool;
    private final StringRedisTemplate redisTemplate;

    private static final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${netdisk.jwt.access-token-expiration:86400000}")
    private long accessTokenExpMs;

    @Value("${netdisk.default-quota:10737418240}")
    private long defaultQuota;


    @Override
    @Transactional
    public UserLoginVO register(RegisterFormDTO formDTO) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, formDTO.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }

        User user = new User()
                .setUsername(formDTO.getUsername())
                .setPasswordHash(passwordEncoder.encode(formDTO.getPassword()))
                .setEmail(formDTO.getEmail())
                .setStatus(1)
                .setQuotaBytes(defaultQuota)
                .setUsedBytes(0L);

        userMapper.insert(user);
        return buildLoginVO(user);
    }

    @Override
    @Transactional
    public UserLoginVO login(LoginFormDTO formDTO) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, formDTO.getUsername()));
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BizException("账号已被禁用或冻结");
        }
        if (!passwordEncoder.matches(formDTO.getPassword(), user.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }

        return buildLoginVO(user);
    }

    @Override
    @Transactional
    public UserLoginVO refreshToken(String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) {
            throw new BizException(401, "刷新令牌不能为空");
        }

        Long userId = jwtTool.parseToken(refreshToken);

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BizException(401, "用户不存在或已被禁用");
        }

        // 旧 refresh token 会被新生成的覆盖，access token 自然过期
        return buildLoginVO(user);
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .quotaBytes(user.getQuotaBytes())
                .usedBytes(user.getUsedBytes())
                .createdAt(user.getCreatedAt().toString())
                .build();
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException("原密码错误");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BizException("新密码不能与当前密码相同");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        // 强制所有设备重新登录
        jwtTool.logout(userId);
    }

    @Override
    @Transactional
    public void updateAvatar(Long userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setAvatarUrl(avatarUrl);
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        jwtTool.logout(userId);
    }

    @Override
    @Transactional
    public void updateProfile(Long userId, String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new BizException("用户名不能为空");
        }
        if (username.length() < 2 || username.length() > 32) {
            throw new BizException("用户名长度需在2-32个字符之间");
        }
        // 检查重名
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .ne(User::getId, userId));
        if (existing != null) {
            throw new BizException("用户名已被使用");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        user.setUsername(username.trim());
        userMapper.updateById(user);
    }

    // 重置 token：注册/登录时调用，自动签发全新的 access + refresh token
    private UserLoginVO buildLoginVO(User user) {
        return buildLoginVO(user, null);
    }

    // 重置 token：可传入已有的 refreshToken 复用（/refresh 场景）
    private UserLoginVO buildLoginVO(User user, String refreshToken) {
        String accessToken = jwtTool.createAccessToken(user.getId());
        if (StrUtil.isBlank(refreshToken)) {
            refreshToken = jwtTool.createRefreshToken(user.getId());
        }
        return UserLoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenExpMs / 1000)
                .build();
    }
}