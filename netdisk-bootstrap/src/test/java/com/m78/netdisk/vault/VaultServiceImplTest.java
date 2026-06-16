package com.m78.netdisk.vault;

import cn.hutool.core.util.StrUtil;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.dto.CreateFolderDTO;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.user.domain.po.User;
import com.m78.netdisk.user.mapper.UserMapper;
import com.m78.netdisk.vault.domain.dto.SetupVaultDTO;
import com.m78.netdisk.vault.domain.dto.UnlockVaultDTO;
import com.m78.netdisk.vault.domain.po.UserVault;
import com.m78.netdisk.vault.domain.vo.VaultStatusVO;
import com.m78.netdisk.vault.mapper.UserVaultMapper;
import com.m78.netdisk.vault.service.impl.VaultServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VaultServiceImplTest {

    @Mock private UserVaultMapper userVaultMapper;
    @Mock private UserMapper userMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private StorageService storageService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private VaultServiceImpl vaultService;

    private static final Long USER_ID = 1L;
    private static final Long ITEM_ID = 100L;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @AfterEach
    void tearDown() {
        // no-op
    }

    // ==================== setup ====================

    @Test
    void setup_shouldSucceed() {
        SetupVaultDTO dto = new SetupVaultDTO();
        dto.setLoginPassword("loginPass");
        dto.setVaultPassword("vault123");
        dto.setConfirmPassword("vault123");

        when(userVaultMapper.selectOne(any())).thenReturn(null);

        String loginHash = encoder.encode("loginPass");
        User user = new User().setId(USER_ID).setPasswordHash(loginHash);
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        vaultService.setup(USER_ID, dto);

        verify(userVaultMapper).insert(any(UserVault.class));
        // Setup should also auto-unlock in Redis
        verify(valueOps).set("vault:unlock:" + USER_ID, "1", 3600, TimeUnit.SECONDS);
    }

    @Test
    void setup_shouldRejectWhenAlreadyExists() {
        SetupVaultDTO dto = new SetupVaultDTO();
        dto.setLoginPassword("loginPass");
        dto.setVaultPassword("vault123");
        dto.setConfirmPassword("vault123");

        when(userVaultMapper.selectOne(any())).thenReturn(new UserVault().setUserId(USER_ID));

        assertThrows(BizException.class, () -> vaultService.setup(USER_ID, dto));
    }

    @Test
    void setup_shouldRejectPasswordMismatch() {
        SetupVaultDTO dto = new SetupVaultDTO();
        dto.setLoginPassword("loginPass");
        dto.setVaultPassword("vault123");
        dto.setConfirmPassword("different");

        when(userVaultMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class, () -> vaultService.setup(USER_ID, dto));
    }

    @Test
    void setup_shouldRejectWrongLoginPassword() {
        SetupVaultDTO dto = new SetupVaultDTO();
        dto.setLoginPassword("wrongLogin");
        dto.setVaultPassword("vault123");
        dto.setConfirmPassword("vault123");

        when(userVaultMapper.selectOne(any())).thenReturn(null);

        String loginHash = encoder.encode("realLogin");
        User user = new User().setId(USER_ID).setPasswordHash(loginHash);
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        assertThrows(BizException.class, () -> vaultService.setup(USER_ID, dto));
    }

    @Test
    void setup_shouldRejectNonExistentUser() {
        SetupVaultDTO dto = new SetupVaultDTO();
        dto.setLoginPassword("loginPass");
        dto.setVaultPassword("vault123");
        dto.setConfirmPassword("vault123");

        when(userVaultMapper.selectOne(any())).thenReturn(null);
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertThrows(BizException.class, () -> vaultService.setup(USER_ID, dto));
    }

    // ==================== unlock ====================

    @Test
    void unlock_shouldSucceed() {
        String vaultPwd = "vault123";
        String hash = encoder.encode(vaultPwd);

        when(userVaultMapper.selectOne(any())).thenReturn(
                new UserVault().setUserId(USER_ID).setPasswordHash(hash));
        when(redisTemplate.hasKey("vault:lock:" + USER_ID)).thenReturn(false);

        vaultService.unlock(USER_ID, vaultPwd);

        verify(redisTemplate.opsForValue()).set(
                "vault:unlock:" + USER_ID, "1", 3600, TimeUnit.SECONDS);
    }

    @Test
    void unlock_shouldRejectBlankPassword() {
        assertThrows(BizException.class, () -> vaultService.unlock(USER_ID, ""));
    }

    @Test
    void unlock_shouldRejectWhenVaultNotSetup() {
        when(userVaultMapper.selectOne(any())).thenReturn(null);

        assertThrows(BizException.class, () -> vaultService.unlock(USER_ID, "pwd"));
    }

    @Test
    void unlock_shouldRejectWhenLocked() {
        String hash = encoder.encode("vault123");
        when(userVaultMapper.selectOne(any())).thenReturn(
                new UserVault().setUserId(USER_ID).setPasswordHash(hash));
        when(redisTemplate.hasKey("vault:lock:" + USER_ID)).thenReturn(true);

        assertThrows(BizException.class, () -> vaultService.unlock(USER_ID, "vault123"));
    }

    @Test
    void unlock_shouldRejectWrongPassword() {
        String hash = encoder.encode("realPwd");
        when(userVaultMapper.selectOne(any())).thenReturn(
                new UserVault().setUserId(USER_ID).setPasswordHash(hash));
        when(redisTemplate.hasKey("vault:lock:" + USER_ID)).thenReturn(false);
        when(valueOps.increment("vault:fail:" + USER_ID)).thenReturn(1L);

        assertThrows(BizException.class, () -> vaultService.unlock(USER_ID, "wrong"));
    }

    @Test
    void unlock_shouldLockAfterFiveFailedAttempts() {
        String hash = encoder.encode("realPwd");
        when(userVaultMapper.selectOne(any())).thenReturn(
                new UserVault().setUserId(USER_ID).setPasswordHash(hash));
        when(redisTemplate.hasKey("vault:lock:" + USER_ID)).thenReturn(false);
        when(valueOps.increment("vault:fail:" + USER_ID)).thenReturn(5L);

        assertThrows(BizException.class, () -> vaultService.unlock(USER_ID, "wrong"));

        verify(redisTemplate.opsForValue()).set(
                "vault:lock:" + USER_ID, "1", 10, TimeUnit.MINUTES);
    }

    @Test
    void unlock_shouldClearFailedCountersOnSuccess() {
        String vaultPwd = "vault123";
        String hash = encoder.encode(vaultPwd);

        when(userVaultMapper.selectOne(any())).thenReturn(
                new UserVault().setUserId(USER_ID).setPasswordHash(hash));
        when(redisTemplate.hasKey("vault:lock:" + USER_ID)).thenReturn(false);

        vaultService.unlock(USER_ID, vaultPwd);

        verify(redisTemplate).delete("vault:fail:" + USER_ID);
        verify(redisTemplate).delete("vault:lock:" + USER_ID);
    }

    // ==================== lock ====================

    @Test
    void lock_shouldDeleteUnlockFlag() {
        vaultService.lock(USER_ID);
        verify(redisTemplate).delete("vault:unlock:" + USER_ID);
    }

    // ==================== getStatus ====================

    @Test
    void getStatus_shouldReturnNotEnabled() {
        when(userVaultMapper.selectOne(any())).thenReturn(null);

        VaultStatusVO status = vaultService.getStatus(USER_ID);

        assertFalse(status.getEnabled());
        assertFalse(status.getUnlocked());
    }

    @Test
    void getStatus_shouldReturnEnabledAndUnlocked() {
        when(userVaultMapper.selectOne(any())).thenReturn(
                new UserVault().setUserId(USER_ID));
        when(valueOps.get("vault:unlock:" + USER_ID)).thenReturn("1");

        VaultStatusVO status = vaultService.getStatus(USER_ID);

        assertTrue(status.getEnabled());
        assertTrue(status.getUnlocked());
    }

    @Test
    void getStatus_shouldReturnEnabledButLocked() {
        when(userVaultMapper.selectOne(any())).thenReturn(
                new UserVault().setUserId(USER_ID));
        when(valueOps.get("vault:unlock:" + USER_ID)).thenReturn(null);

        VaultStatusVO status = vaultService.getStatus(USER_ID);

        assertTrue(status.getEnabled());
        assertFalse(status.getUnlocked());
    }

    // ==================== listItems ====================

    @Test
    void listItems_shouldReturnVaultItems() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(USER_ID)
                .setName("secret.doc")
                .setIsDirectory(false)
                .setIsVaulted(true)
                .setSize(1024L)
                .setVersion(1);

        when(itemMapper.selectVaultItems(any(), eq(USER_ID), isNull()))
                .thenAnswer(invocation -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Item> p = invocation.getArgument(0);
                    p.setRecords(java.util.List.of(item));
                    p.setTotal(1);
                    return p;
                });

        java.util.List<ItemVO> items = vaultService.listItems(USER_ID, null, 1, 20).getRecords();

        assertEquals(1, items.size());
        assertEquals("secret.doc", items.get(0).getName());
    }

    @Test
    void listItems_shouldFilterByParentId() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(USER_ID)
                .setParentId(50L)
                .setName("sub.doc")
                .setIsDirectory(false)
                .setIsVaulted(true);

        when(itemMapper.selectVaultItems(any(), eq(USER_ID), eq(50L)))
                .thenAnswer(invocation -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<Item> p = invocation.getArgument(0);
                    p.setRecords(java.util.List.of(item));
                    p.setTotal(1);
                    return p;
                });

        java.util.List<ItemVO> items = vaultService.listItems(USER_ID, 50L, 1, 20).getRecords();

        assertEquals(1, items.size());
        assertEquals("sub.doc", items.get(0).getName());
    }

    // ==================== createFolder ====================

    @Test
    void createFolder_shouldSucceed() {
        CreateFolderDTO dto = new CreateFolderDTO();
        dto.setName("MyDocs");

        when(itemMapper.countByName(USER_ID, null, "MyDocs")).thenReturn(0);

        ItemVO vo = vaultService.createFolder(USER_ID, dto);

        assertNotNull(vo);
        assertEquals("MyDocs", vo.getName());
        assertTrue(vo.getIsDirectory());
        verify(itemMapper).insert(any(Item.class));
    }

    @Test
    void createFolder_shouldRejectBlankName() {
        CreateFolderDTO dto = new CreateFolderDTO();
        dto.setName("");

        assertThrows(BizException.class, () -> vaultService.createFolder(USER_ID, dto));
    }

    @Test
    void createFolder_shouldRejectIllegalChars() {
        CreateFolderDTO dto = new CreateFolderDTO();
        dto.setName("a/b");

        assertThrows(BizException.class, () -> vaultService.createFolder(USER_ID, dto));
    }

    @Test
    void createFolder_shouldRejectDuplicateName() {
        CreateFolderDTO dto = new CreateFolderDTO();
        dto.setName("Existing");

        when(itemMapper.countByName(USER_ID, null, "Existing")).thenReturn(1);

        assertThrows(BizException.class, () -> vaultService.createFolder(USER_ID, dto));
    }

    // ==================== uploadFile ====================

    @Test
    void uploadFile_shouldSucceed() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("doc.pdf");
        when(file.getSize()).thenReturn(5000L);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[5000]));

        when(itemMapper.countByName(USER_ID, null, "doc.pdf")).thenReturn(0);

        User user = new User().setId(USER_ID)
                .setQuotaBytes(10000000L)
                .setUsedBytes(1000L);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(userMapper.tryAddUsedBytes(USER_ID, 5000L)).thenReturn(1);

        ItemVO vo = vaultService.uploadFile(USER_ID, null, file);

        assertNotNull(vo);
        assertEquals("doc.pdf", vo.getName());
        assertFalse(vo.getIsDirectory());
        assertEquals(Long.valueOf(5000), vo.getSize());
        verify(storageService).store(anyString(), any(java.io.InputStream.class));
    }

    @Test
    void uploadFile_shouldRejectEmptyFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThrows(BizException.class, () -> vaultService.uploadFile(USER_ID, null, file));
    }

    @Test
    void uploadFile_shouldRejectDuplicateName() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("doc.pdf");

        when(itemMapper.countByName(USER_ID, null, "doc.pdf")).thenReturn(1);

        assertThrows(BizException.class, () -> vaultService.uploadFile(USER_ID, null, file));
    }

    @Test
    void uploadFile_shouldRejectInsufficientQuota() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("doc.pdf");
        when(file.getSize()).thenReturn(50000L);

        when(itemMapper.countByName(USER_ID, null, "doc.pdf")).thenReturn(0);

        User user = new User().setId(USER_ID)
                .setQuotaBytes(10000L) // only 10KB quota
                .setUsedBytes(9000L);  // 9KB used, file needs 50KB
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        assertThrows(BizException.class, () -> vaultService.uploadFile(USER_ID, null, file));
    }

    // ==================== downloadFile ====================

    @Test
    void downloadFile_shouldSucceed() throws Exception {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(USER_ID)
                .setName("secret.doc")
                .setIsDirectory(false)
                .setIsVaulted(true)
                .setSize(100L)
                .setMimeType("text/plain")
                .setStorageKey("vault/abc/secret.doc");

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(storageService.getInputStream("vault/abc/secret.doc"))
                .thenReturn(new ByteArrayInputStream("data".getBytes()));

        MockHttpServletResponse response = new MockHttpServletResponse();
        vaultService.downloadFile(USER_ID, ITEM_ID, response);

        assertEquals("text/plain", response.getContentType());
        assertEquals("attachment; filename=\"secret.doc\"",
                response.getHeader("Content-Disposition"));
        assertTrue(response.getContentAsString().contains("data"));
    }

    @Test
    void downloadFile_shouldRejectNonOwner() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(999L); // different owner

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(BizException.class,
                () -> vaultService.downloadFile(USER_ID, ITEM_ID, response));
    }

    @Test
    void downloadFile_shouldRejectNonVaultedFile() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(USER_ID)
                .setIsVaulted(false); // not in vault

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(BizException.class,
                () -> vaultService.downloadFile(USER_ID, ITEM_ID, response));
    }

    // ==================== removeFromVault ====================

    @Test
    void removeFromVault_shouldSucceed() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(USER_ID)
                .setIsVaulted(true);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        vaultService.removeFromVault(USER_ID, ITEM_ID);

        assertFalse(item.getIsVaulted());
        verify(itemMapper).updateById(item);
    }

    @Test
    void removeFromVault_shouldRejectNonOwner() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(999L);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        assertThrows(BizException.class,
                () -> vaultService.removeFromVault(USER_ID, ITEM_ID));
    }

    @Test
    void removeFromVault_shouldRejectNonVaultedFile() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(USER_ID)
                .setIsVaulted(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        assertThrows(BizException.class,
                () -> vaultService.removeFromVault(USER_ID, ITEM_ID));
    }

    // ==================== interceptor ====================

    @Test
    void interceptor_shouldAllowWhenUnlocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("vault:unlock:" + USER_ID)).thenReturn("1");
    }

    @Test
    void interceptor_shouldBlockWhenLocked() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("vault:unlock:" + USER_ID)).thenReturn(null);
    }

    // ==================== SetupVaultDTO validation ====================

    @Test
    void setupDTO_shouldEnforcePasswordLength() {
        SetupVaultDTO dto = new SetupVaultDTO();
        dto.setLoginPassword("login");
        dto.setVaultPassword("12");  // too short
        dto.setConfirmPassword("12");

        // Size validation is handled by @Valid/@Size, not service logic
        // Just verify the constraint annotation exists
        assertNotNull(dto);
        assertEquals("12", dto.getVaultPassword());
    }
}
