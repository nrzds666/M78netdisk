package com.m78.netdisk.user;

import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.utils.JwtTool;
import com.m78.netdisk.user.domain.dto.LoginFormDTO;
import com.m78.netdisk.user.domain.dto.RegisterFormDTO;
import com.m78.netdisk.user.domain.po.User;
import com.m78.netdisk.user.domain.vo.UserInfoVO;
import com.m78.netdisk.user.domain.vo.UserLoginVO;
import com.m78.netdisk.user.mapper.UserMapper;
import com.m78.netdisk.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl — 安全性与正确性测试")
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtTool jwtTool;
    @Mock
    private StringRedisTemplate redisTemplate;

    @InjectMocks
    private UserServiceImpl userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private static final Long USER_ID = 1L;
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "accessTokenExpMs", 86400000L);
        ReflectionTestUtils.setField(userService, "defaultQuota", 10737418240L);
    }

    // ========================================================================
    // 注册流程 — 安全性检查
    // ========================================================================
    @Nested
    @DisplayName("注册流程")
    class RegisterTests {

        @Test
        @DisplayName("用户名重复时应拒绝注册")
        void register_shouldRejectDuplicateUsername() {
            RegisterFormDTO dto = new RegisterFormDTO();
            dto.setUsername("existing_user");
            dto.setPassword("password123");

            when(userMapper.selectCount(any())).thenReturn(1L);

            BizException ex = assertThrows(BizException.class, () -> userService.register(dto));
            assertTrue(ex.getMessage().contains("已存在"));
            verify(userMapper, never()).insert(any(com.m78.netdisk.user.domain.po.User.class));
        }

        @Test
        @DisplayName("注册成功时应使用BCrypt加密密码，不得明文存储")
        void register_shouldEncryptPasswordWithBCrypt() {
            String rawPassword = "SecurePass123!";
            RegisterFormDTO dto = new RegisterFormDTO();
            dto.setUsername("newuser");
            dto.setPassword(rawPassword);
            dto.setEmail("new@test.com");

            when(userMapper.selectCount(any())).thenReturn(0L);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(jwtTool.createAccessToken(anyLong())).thenReturn("access-token");
            when(jwtTool.createRefreshToken(anyLong())).thenReturn("refresh-token");

            userService.register(dto);

            verify(userMapper).insert(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            // 密码不得是明文
            assertNotNull(savedUser.getPasswordHash());
            assertNotEquals(rawPassword, savedUser.getPasswordHash());
            // 必须是 BCrypt 格式 ($2a$/$2b$/$2y$)
            assertTrue(savedUser.getPasswordHash().startsWith("$2"));
            // BCrypt 验证应通过
            assertTrue(encoder.matches(rawPassword, savedUser.getPasswordHash()));
        }

        @Test
        @DisplayName("注册成功时应设置默认配额10GB")
        void register_shouldSetDefaultQuota() {
            RegisterFormDTO dto = new RegisterFormDTO();
            dto.setUsername("newuser");
            dto.setPassword("password123");

            when(userMapper.selectCount(any())).thenReturn(0L);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(jwtTool.createAccessToken(anyLong())).thenReturn("access-token");
            when(jwtTool.createRefreshToken(anyLong())).thenReturn("refresh-token");

            userService.register(dto);

            verify(userMapper).insert(userCaptor.capture());
            User savedUser = userCaptor.getValue();

            assertEquals(10737418240L, savedUser.getQuotaBytes());
            assertEquals(0L, savedUser.getUsedBytes());
            assertEquals(1, savedUser.getStatus());
        }

        @Test
        @DisplayName("注册成功后应签发token")
        void register_shouldIssueTokens() {
            RegisterFormDTO dto = new RegisterFormDTO();
            dto.setUsername("newuser");
            dto.setPassword("password123");

            when(userMapper.selectCount(any())).thenReturn(0L);
            when(userMapper.insert(any(User.class))).thenReturn(1);
            when(jwtTool.createAccessToken(USER_ID)).thenReturn("access-token");
            when(jwtTool.createRefreshToken(USER_ID)).thenReturn("refresh-token");

            // 模拟 insert 后 user 有 id
            doAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(USER_ID);
                return 1;
            }).when(userMapper).insert(any(User.class));

            UserLoginVO result = userService.register(dto);

            assertNotNull(result);
            assertEquals(USER_ID, result.getUserId());
            assertEquals("access-token", result.getAccessToken());
            assertEquals("refresh-token", result.getRefreshToken());
            assertNotNull(result.getExpiresIn());
            verify(jwtTool).createAccessToken(USER_ID);
            verify(jwtTool).createRefreshToken(USER_ID);
        }
    }

    // ========================================================================
    // 登录流程 — 安全性检查
    // ========================================================================
    @Nested
    @DisplayName("登录流程")
    class LoginTests {

        @Test
        @DisplayName("不存在的用户名应返回模糊错误信息")
        void login_shouldRejectNonExistentUser() {
            LoginFormDTO dto = new LoginFormDTO();
            dto.setUsername("nonexistent");
            dto.setPassword("password123");

            when(userMapper.selectOne(any())).thenReturn(null);

            BizException ex = assertThrows(BizException.class, () -> userService.login(dto));
            assertEquals("用户名或密码错误", ex.getMessage());
        }

        @Test
        @DisplayName("被禁用的用户应拒绝登录")
        void login_shouldRejectDisabledUser() {
            LoginFormDTO dto = new LoginFormDTO();
            dto.setUsername("disabled_user");
            dto.setPassword("password123");

            User disabledUser = new User()
                    .setId(USER_ID)
                    .setUsername("disabled_user")
                    .setStatus(0) // disabled
                    .setPasswordHash(encoder.encode("password123"));

            when(userMapper.selectOne(any())).thenReturn(disabledUser);

            BizException ex = assertThrows(BizException.class, () -> userService.login(dto));
            assertTrue(ex.getMessage().contains("禁用") || ex.getMessage().contains("冻结"));
        }

        @Test
        @DisplayName("密码错误应返回模糊错误信息，不泄露是用户名还是密码错误")
        void login_shouldRejectWrongPassword() {
            LoginFormDTO dto = new LoginFormDTO();
            dto.setUsername("testuser");
            dto.setPassword("wrongpassword");

            User user = new User()
                    .setId(USER_ID)
                    .setUsername("testuser")
                    .setStatus(1)
                    .setPasswordHash(encoder.encode("correctpassword"));

            when(userMapper.selectOne(any())).thenReturn(user);

            BizException ex = assertThrows(BizException.class, () -> userService.login(dto));
            assertEquals("用户名或密码错误", ex.getMessage());
        }

        @Test
        @DisplayName("有效的用户名密码登录应成功")
        void login_shouldSucceedWithValidCredentials() {
            String password = "correctpassword";
            LoginFormDTO dto = new LoginFormDTO();
            dto.setUsername("testuser");
            dto.setPassword(password);

            User user = new User()
                    .setId(USER_ID)
                    .setUsername("testuser")
                    .setEmail("test@test.com")
                    .setStatus(1)
                    .setPasswordHash(encoder.encode(password));

            when(userMapper.selectOne(any())).thenReturn(user);
            when(jwtTool.createAccessToken(USER_ID)).thenReturn("access-token");
            when(jwtTool.createRefreshToken(USER_ID)).thenReturn("refresh-token");

            UserLoginVO result = userService.login(dto);

            assertNotNull(result);
            assertEquals(USER_ID, result.getUserId());
            assertEquals("access-token", result.getAccessToken());
            assertEquals("refresh-token", result.getRefreshToken());
            verify(jwtTool).createAccessToken(USER_ID);
            verify(jwtTool).createRefreshToken(USER_ID);
        }

        @Test
        @DisplayName("登录时不应调用logout（不强制下线其他设备）")
        void login_shouldNotLogoutOtherDevices() {
            String password = "password123";
            LoginFormDTO dto = new LoginFormDTO();
            dto.setUsername("testuser");
            dto.setPassword(password);

            User user = new User()
                    .setId(USER_ID)
                    .setUsername("testuser")
                    .setStatus(1)
                    .setPasswordHash(encoder.encode(password));

            when(userMapper.selectOne(any())).thenReturn(user);
            when(jwtTool.createAccessToken(USER_ID)).thenReturn("access-token");
            when(jwtTool.createRefreshToken(USER_ID)).thenReturn("refresh-token");

            userService.login(dto);

            verify(jwtTool, never()).logout(anyLong());
        }
    }

    // ========================================================================
    // 刷新 Token — 安全性检查
    // ========================================================================
    @Nested
    @DisplayName("刷新Token流程")
    class RefreshTokenTests {

        @Test
        @DisplayName("有效的refresh token应签发新的token对")
        void refreshToken_shouldIssueNewTokens() {
            User user = new User()
                    .setId(USER_ID)
                    .setUsername("test")
                    .setStatus(1);

            when(jwtTool.parseToken("valid-refresh-token")).thenReturn(USER_ID);
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(jwtTool.createAccessToken(USER_ID)).thenReturn("new-access");
            when(jwtTool.createRefreshToken(USER_ID)).thenReturn("new-refresh");

            UserLoginVO result = userService.refreshToken("valid-refresh-token");

            assertNotNull(result);
            assertEquals("new-access", result.getAccessToken());
            assertEquals("new-refresh", result.getRefreshToken());

            // 验证旧 refresh token 被轮换（新 token 覆盖 Redis 中的旧 token）
            verify(jwtTool).createRefreshToken(USER_ID);
            // logout 不应被调用（不强制下线其他设备）
            verify(jwtTool, never()).logout(anyLong());
        }

        @Test
        @DisplayName("空的refresh token应拒绝")
        void refreshToken_shouldRejectNullToken() {
            BizException ex = assertThrows(BizException.class,
                    () -> userService.refreshToken(null));
            assertEquals(401, ex.getCode());
            assertTrue(ex.getMessage().contains("不能为空"));

            ex = assertThrows(BizException.class,
                    () -> userService.refreshToken(""));
            assertEquals(401, ex.getCode());

            ex = assertThrows(BizException.class,
                    () -> userService.refreshToken("   "));
            assertEquals(401, ex.getCode());
        }

        @Test
        @DisplayName("已禁用用户的refresh token应拒绝")
        void refreshToken_shouldRejectDisabledUser() {
            when(jwtTool.parseToken("some-token")).thenReturn(USER_ID);

            User disabledUser = new User()
                    .setId(USER_ID)
                    .setUsername("test")
                    .setStatus(0);
            when(userMapper.selectById(USER_ID)).thenReturn(disabledUser);

            BizException ex = assertThrows(BizException.class,
                    () -> userService.refreshToken("some-token"));
            assertEquals(401, ex.getCode());
        }

        @Test
        @DisplayName("不存在的用户刷新token应拒绝")
        void refreshToken_shouldRejectNonExistentUser() {
            when(jwtTool.parseToken("some-token")).thenReturn(USER_ID);
            when(userMapper.selectById(USER_ID)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> userService.refreshToken("some-token"));
            assertEquals(401, ex.getCode());
        }
    }

    // ========================================================================
    // 修改密码 — 安全性检查
    // ========================================================================
    @Nested
    @DisplayName("修改密码流程")
    class UpdatePasswordTests {

        @Test
        @DisplayName("修改密码后应强制所有设备下线")
        void updatePassword_shouldLogoutAllDevices() {
            String oldPassword = "oldPassword123";
            String newPassword = "newPassword456";
            User user = new User()
                    .setId(USER_ID)
                    .setPasswordHash(encoder.encode(oldPassword));

            when(userMapper.selectById(USER_ID)).thenReturn(user);
            // updateById should succeed
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            userService.updatePassword(USER_ID, oldPassword, newPassword);

            // 验证所有 token 被清除
            verify(jwtTool).logout(USER_ID);
        }

        @Test
        @DisplayName("原密码错误应拒绝修改")
        void updatePassword_shouldRejectWrongOldPassword() {
            User user = new User()
                    .setId(USER_ID)
                    .setPasswordHash(encoder.encode("correctOldPassword"));

            when(userMapper.selectById(USER_ID)).thenReturn(user);

            BizException ex = assertThrows(BizException.class,
                    () -> userService.updatePassword(USER_ID, "wrongOldPassword", "newPassword"));
            assertTrue(ex.getMessage().contains("原密码错误"));
            verify(jwtTool, never()).logout(anyLong());
        }

        @Test
        @DisplayName("修改密码后存储的新密码应使用BCrypt加密")
        void updatePassword_shouldEncryptNewPassword() {
            String oldPassword = "oldPassword123";
            String newPassword = "newPassword456";
            User user = new User()
                    .setId(USER_ID)
                    .setPasswordHash(encoder.encode(oldPassword));

            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            userService.updatePassword(USER_ID, oldPassword, newPassword);

            verify(userMapper).updateById(userCaptor.capture());
            User updatedUser = userCaptor.getValue();

            // 新密码应被 BCrypt 加密
            assertTrue(updatedUser.getPasswordHash().startsWith("$2"));
            assertTrue(encoder.matches(newPassword, updatedUser.getPasswordHash()));
            assertFalse(encoder.matches(oldPassword, updatedUser.getPasswordHash()));
        }
    }

    // ========================================================================
    // 用户信息 — 正确性检查
    // ========================================================================
    @Nested
    @DisplayName("用户信息查询")
    class GetUserInfoTests {

        @Test
        @DisplayName("存在的用户应返回完整信息")
        void getUserInfo_shouldReturnCorrectInfo() {
            User user = new User()
                    .setId(USER_ID)
                    .setUsername("testuser")
                    .setEmail("test@test.com")
                    .setAvatarUrl("http://avatar.url")
                    .setStatus(1)
                    .setQuotaBytes(10737418240L)
                    .setUsedBytes(500L);

            when(userMapper.selectById(USER_ID)).thenReturn(user);

            UserInfoVO result = userService.getUserInfo(USER_ID);

            assertNotNull(result);
            assertEquals(USER_ID, result.getId());
            assertEquals("testuser", result.getUsername());
            assertEquals("test@test.com", result.getEmail());
            assertEquals(1, result.getStatus());
            assertEquals(10737418240L, result.getQuotaBytes());
            assertEquals(500L, result.getUsedBytes());
        }

        @Test
        @DisplayName("不存在的用户应抛出异常")
        void getUserInfo_shouldThrowForNonExistentUser() {
            when(userMapper.selectById(999L)).thenReturn(null);

            BizException ex = assertThrows(BizException.class,
                    () -> userService.getUserInfo(999L));
            assertTrue(ex.getMessage().contains("不存在"));
        }
    }

    // ========================================================================
    // 退出登录 — 安全性检查
    // ========================================================================
    @Nested
    @DisplayName("退出登录")
    class LogoutTests {

        @Test
        @DisplayName("退出登录应清除Redis中的token")
        void logout_shouldClearTokens() {
            userService.logout(USER_ID);
            verify(jwtTool).logout(USER_ID);
        }
    }

    // ========================================================================
    // 敏感信息泄露检查
    // ========================================================================
    @Nested
    @DisplayName("敏感信息保护")
    class SensitiveInfoTests {

        @Test
        @DisplayName("登录响应不应泄露密码哈希")
        void loginResponse_shouldNotContainPasswordHash() {
            String password = "password123";
            LoginFormDTO dto = new LoginFormDTO();
            dto.setUsername("testuser");
            dto.setPassword(password);

            User user = new User()
                    .setId(USER_ID)
                    .setUsername("testuser")
                    .setStatus(1)
                    .setPasswordHash(encoder.encode(password));

            when(userMapper.selectOne(any())).thenReturn(user);
            when(jwtTool.createAccessToken(USER_ID)).thenReturn("access-token");
            when(jwtTool.createRefreshToken(USER_ID)).thenReturn("refresh-token");

            UserLoginVO result = userService.login(dto);

            assertNotNull(result);
            // UserLoginVO 没有 passwordHash 字段，确保不会意外暴露
            assertDoesNotThrow(() -> {
                var fields = UserLoginVO.class.getDeclaredFields();
                for (var f : fields) {
                    assertFalse(f.getName().toLowerCase().contains("password"),
                            "UserLoginVO 不应包含密码相关字段: " + f.getName());
                }
            });
        }

        @Test
        @DisplayName("用户信息响应不应泄露密码哈希")
        void userInfoResponse_shouldNotContainPasswordHash() {
            User user = new User()
                    .setId(USER_ID)
                    .setUsername("testuser")
                    .setPasswordHash(encoder.encode("secret"));

            when(userMapper.selectById(USER_ID)).thenReturn(user);

            UserInfoVO result = userService.getUserInfo(USER_ID);

            // UserInfoVO 没有 passwordHash 字段，确保不会意外暴露
            assertDoesNotThrow(() -> {
                var fields = UserInfoVO.class.getDeclaredFields();
                for (var f : fields) {
                    assertFalse(f.getName().toLowerCase().contains("password"),
                            "UserInfoVO 不应包含密码相关字段: " + f.getName());
                }
            });
        }
    }
}
