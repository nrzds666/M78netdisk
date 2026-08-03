package com.m78.netdisk.user.controller;

import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.common.utils.CaptchaUtil;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.user.domain.dto.LoginFormDTO;
import com.m78.netdisk.user.domain.dto.RegisterFormDTO;
import com.m78.netdisk.user.domain.vo.UserInfoVO;
import com.m78.netdisk.user.domain.vo.UserLoginVO;
import com.m78.netdisk.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final CaptchaUtil captchaUtil;
    private final StorageService storageService;

    // ========== 验证码 ==========

    @GetMapping("/captcha")
    public R<CaptchaUtil.CaptchaResult> captcha() {
        return R.ok(captchaUtil.generateArithmetic());
    }

    // ========== 认证 ==========

    @PostMapping("/register")
    public R<UserLoginVO> register(@Valid @RequestBody RegisterFormDTO formDTO) {
        if (!captchaUtil.verify(formDTO.getCaptchaKey(), formDTO.getCaptchaCode())) {
            return R.fail(400, "验证码错误或已过期");
        }
        return R.ok(userService.register(formDTO));
    }

    @PostMapping("/login")
    public R<UserLoginVO> login(@Valid @RequestBody LoginFormDTO formDTO) {
        if (!captchaUtil.verify(formDTO.getCaptchaKey(), formDTO.getCaptchaCode())) {
            return R.fail(400, "验证码错误或已过期");
        }
        return R.ok(userService.login(formDTO));
    }

    @PostMapping("/refresh")
    public R<UserLoginVO> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
        return R.ok(userService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    public R<Void> logout() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        userService.logout(userId);
        return R.ok();
    }

    // ========== 用户信息 ==========

    @GetMapping
    public R<UserInfoVO> me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        return R.ok(userService.getUserInfo(userId));
    }

    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestParam String username) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        userService.updateProfile(userId, username);
        return R.ok();
    }

    @PutMapping("/password")
    public R<Void> updatePassword(@RequestParam String oldPassword,
                                   @RequestParam String newPassword) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            return R.fail(400, "原密码不能为空");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 72) {
            return R.fail(400, "新密码长度需在6-72个字符之间");
        }
        userService.updatePassword(userId, oldPassword, newPassword);
        return R.ok();
    }

    @PutMapping("/avatar")
    public R<Void> updateAvatar(@RequestParam String avatarUrl) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return R.fail(400, "头像URL不能为空");
        }
        userService.updateAvatar(userId, avatarUrl);
        return R.ok();
    }

    @PostMapping("/avatar/upload")
    public R<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        if (file.isEmpty()) {
            return R.fail(400, "上传文件不能为空");
        }
        try {
            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String storageKey = "avatars/" + userId + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
            storageService.store(storageKey, file.getBytes());
            String url = storageService.getPublicUrl(storageKey);
            return R.ok(url);
        } catch (java.io.IOException e) {
            log.error("头像上传失败", e);
            return R.fail(500, "头像上传失败");
        }
    }

    @PutMapping("/avatar/by-key")
    public R<Void> updateAvatarByKey(@RequestParam String storageKey) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        if (storageKey == null || storageKey.isBlank()) {
            return R.fail(400, "文件存储键不能为空");
        }
        String url = storageService.getPublicUrl(storageKey);
        userService.updateAvatar(userId, url);
        return R.ok();
    }
}