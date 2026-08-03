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
import org.springframework.dao.DuplicateKeyException;
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
                .setRole("user")
                .setQuotaBytes(defaultQuota)
                .setUsedBytes(0L);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册时另一个请求可能已经插入了同名用户
            throw new BizException("用户名已存在");
        }
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

        // 兼容旧数据：role 为 null 时默认设为 user
        if (user.getRole() == null) {
            user.setRole("user");
            userMapper.updateById(user);
        }

        return buildLoginVO(user);
    }

    @Override
    @Transactional
    public UserLoginVO refreshToken(String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) {
            throw new BizException(401, "刷新令牌不能为空");
        }

        JwtTool.TokenPayload payload = jwtTool.parseToken(refreshToken);
        Long userId = payload.getUserId();

        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BizException(401, "用户不存在或已被禁用");
        }

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
                .role(user.getRole())
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

    private UserLoginVO buildLoginVO(User user) {
        return buildLoginVO(user, null);
    }

    private UserLoginVO buildLoginVO(User user, String refreshToken) {
        String accessToken = jwtTool.createAccessToken(user.getId(), user.getRole());
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
