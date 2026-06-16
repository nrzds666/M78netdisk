package com.m78.netdisk.share;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.enums.ShareExpire;
import com.m78.netdisk.share.domain.po.ReceivedShare;
import com.m78.netdisk.share.domain.po.Share;
import com.m78.netdisk.share.domain.vo.ShareVO;
import com.m78.netdisk.share.mapper.ReceivedShareMapper;
import com.m78.netdisk.share.mapper.ShareMapper;
import com.m78.netdisk.share.service.impl.ShareServiceImpl;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareServiceImplTest {

    @Mock private ShareMapper shareMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ReceivedShareMapper receivedShareMapper;
    @Mock private StorageService storageService;
    @Mock private com.m78.netdisk.user.mapper.UserMapper userMapper;

    @InjectMocks
    private ShareServiceImpl shareService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ITEM_ID = 100L;
    private static final String SHARE_TOKEN = "abcdef1234567890";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    // ==================== createShare ====================

    @Test
    void createShare_shouldCreateWithDefaultPermanentExpiry() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        ShareVO vo = shareService.createShare(OWNER_ID, dto);

        assertNotNull(vo);
        assertNotNull(vo.getShareToken());
        assertEquals("永久", vo.getExpireLabel());
        assertNull(vo.getExpireAt()); // PERMANENT → expireAt = null
        verify(shareMapper).insert(any(Share.class));
    }

    @Test
    void createShare_shouldSetOneDayExpiry() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);
        dto.setExpireType("ONE_DAY");

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        ShareVO vo = shareService.createShare(OWNER_ID, dto);

        assertNotNull(vo);
        assertEquals("一天", vo.getExpireLabel());
        assertNotNull(vo.getExpireAt());
    }

    @Test
    void createShare_shouldSetOneWeekExpiry() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);
        dto.setExpireType("ONE_WEEK");

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        ShareVO vo = shareService.createShare(OWNER_ID, dto);

        assertNotNull(vo);
        assertEquals("一周", vo.getExpireLabel());
        assertNotNull(vo.getExpireAt());
    }

    @Test
    void createShare_shouldSetOneMonthExpiry() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);
        dto.setExpireType("ONE_MONTH");

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        ShareVO vo = shareService.createShare(OWNER_ID, dto);

        assertNotNull(vo);
        assertEquals("一个月", vo.getExpireLabel());
        assertNotNull(vo.getExpireAt());
    }

    @Test
    void createShare_shouldEncodePassword() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);
        dto.setPassword("1234");

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        ShareVO vo = shareService.createShare(OWNER_ID, dto);

        assertNotNull(vo);
        assertTrue(vo.getHasPassword());
        verify(shareMapper).insert(any(Share.class));
    }

    @Test
    void createShare_shouldRejectNonOwnedItem() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(999L); // different owner

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        assertThrows(BizException.class,
                () -> shareService.createShare(OWNER_ID, dto));
    }

    @Test
    void createShare_shouldRejectInvalidPermission() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);
        dto.setPermission("admin");

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        assertThrows(BizException.class,
                () -> shareService.createShare(OWNER_ID, dto));
    }

    @Test
    void createShare_shouldRejectVaultedItem() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsVaulted(true) // vaulted file
                .setIsDirectory(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        assertThrows(BizException.class,
                () -> shareService.createShare(OWNER_ID, dto));
    }

    // ==================== ShareExpire enum ====================

    @Test
    void shareExpire_shouldParseValidTypes() {
        assertEquals(ShareExpire.ONE_DAY, ShareExpire.fromType("ONE_DAY"));
        assertEquals(ShareExpire.ONE_WEEK, ShareExpire.fromType("one_week"));
        assertEquals(ShareExpire.ONE_MONTH, ShareExpire.fromType("One_Month"));
        assertEquals(ShareExpire.PERMANENT, ShareExpire.fromType("PERMANENT"));
    }

    @Test
    void shareExpire_shouldDefaultToPermanentForInvalidType() {
        assertEquals(ShareExpire.PERMANENT, ShareExpire.fromType("INVALID"));
        assertEquals(ShareExpire.PERMANENT, ShareExpire.fromType(null));
        assertEquals(ShareExpire.PERMANENT, ShareExpire.fromType(""));
    }

    @Test
    void shareExpire_shouldHaveCorrectHourValues() {
        assertEquals(Long.valueOf(24), ShareExpire.ONE_DAY.getHours());
        assertEquals(Long.valueOf(168), ShareExpire.ONE_WEEK.getHours());
        assertEquals(Long.valueOf(720), ShareExpire.ONE_MONTH.getHours());
        assertNull(ShareExpire.PERMANENT.getHours());
    }

    // ==================== cancelShare ====================

    @Test
    void cancelShare_shouldCancel() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setIsCanceled(false);

        when(shareMapper.selectById(1L)).thenReturn(share);

        shareService.cancelShare(OWNER_ID, 1L);

        assertTrue(share.getIsCanceled());
        verify(shareMapper).updateById(share);
    }

    @Test
    void cancelShare_shouldRejectNonOwner() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID);

        when(shareMapper.selectById(1L)).thenReturn(share);

        assertThrows(BizException.class,
                () -> shareService.cancelShare(999L, 1L));
    }

    @Test
    void cancelShare_shouldRejectMissingShare() {
        when(shareMapper.selectById(1L)).thenReturn(null);

        assertThrows(BizException.class,
                () -> shareService.cancelShare(OWNER_ID, 1L));
    }

    // ==================== accessShare ====================

    @Test
    void accessShare_shouldReturnShareVO() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);

        ShareVO vo = shareService.accessShare(SHARE_TOKEN, null);

        assertNotNull(vo);
        assertEquals("view", vo.getPermission());
        assertFalse(vo.getHasPassword());
        assertEquals("永久", vo.getExpireLabel());
        assertFalse(vo.getIsReceived());
    }

    @Test
    void accessShare_shouldThrowWhenShareNotFound() {
        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(null);

        assertThrows(BizException.class,
                () -> shareService.accessShare(SHARE_TOKEN, null));
    }

    @Test
    void accessShare_shouldThrowWhenLocked() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN);

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(true);

        assertThrows(BizException.class,
                () -> shareService.accessShare(SHARE_TOKEN, null));
    }

    @Test
    void accessShare_shouldReturnPartialInfoWhenPasswordNotProvided() {
        String realHash = encoder.encode("secret");

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPasswordHash(realHash);

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setName("secret.pdf")
                .setIsDirectory(false);

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);

        ShareVO vo = shareService.accessShare(SHARE_TOKEN, null);

        assertNotNull(vo);
        assertEquals(SHARE_TOKEN, vo.getShareToken());
        assertTrue(vo.getHasPassword());
        assertFalse(vo.getAccessGranted());
        assertEquals("secret.pdf", vo.getFileName());
        assertFalse(vo.getIsDirectory());
    }

    @Test
    void accessShare_shouldReturnOwnerName() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN);

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setName("secret.pdf")
                .setIsDirectory(false);

        com.m78.netdisk.user.domain.po.User owner = new com.m78.netdisk.user.domain.po.User()
                .setId(OWNER_ID)
                .setUsername("张三")
                .setAvatarUrl("https://example.com/avatar.png");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(userMapper.selectById(OWNER_ID)).thenReturn(owner);

        ShareVO vo = shareService.accessShare(SHARE_TOKEN, null);

        assertNotNull(vo);
        assertEquals("张三", vo.getOwnerName());
        assertEquals("https://example.com/avatar.png", vo.getOwnerAvatar());
    }

    @Test
    void accessShare_shouldRejectWrongPassword() {
        String realHash = encoder.encode("secret");

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPasswordHash(realHash);

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(valueOps.increment("share:fail:" + SHARE_TOKEN)).thenReturn(1L);

        assertThrows(BizException.class,
                () -> shareService.accessShare(SHARE_TOKEN, "wrong"));
    }

    @Test
    void accessShare_shouldAcceptCorrectPassword() {
        String realHash = encoder.encode("secret");

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPasswordHash(realHash)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);

        shareService.accessShare(SHARE_TOKEN, "secret");

        verify(redisTemplate).delete("share:fail:" + SHARE_TOKEN);
        verify(redisTemplate).delete("share:lock:" + SHARE_TOKEN);
    }

    @Test
    void accessShare_shouldLockAfterFiveFailedAttempts() {
        String realHash = encoder.encode("secret");

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPasswordHash(realHash);

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        // Simulate 5th failure
        when(valueOps.increment("share:fail:" + SHARE_TOKEN)).thenReturn(5L);

        assertThrows(BizException.class,
                () -> shareService.accessShare(SHARE_TOKEN, "wrong"));

        verify(redisTemplate.opsForValue()).set("share:lock:" + SHARE_TOKEN, "1", 10, TimeUnit.MINUTES);
    }

    @Test
    void accessShare_shouldClearFailCountersOnSuccess() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);

        shareService.accessShare(SHARE_TOKEN, null);

        verify(redisTemplate).delete("share:fail:" + SHARE_TOKEN);
        verify(redisTemplate).delete("share:lock:" + SHARE_TOKEN);
    }

    // ==================== accessShare + received recording ====================

    @Test
    void accessShare_shouldRecordReceivedShareWhenLoggedIn() {
        UserContext.setUserId(OTHER_USER_ID);

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID) // different from OTHER_USER_ID
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(receivedShareMapper.countByUserAndShare(OTHER_USER_ID, 1L)).thenReturn(0);

        shareService.accessShare(SHARE_TOKEN, null);

        verify(receivedShareMapper).insert(any(ReceivedShare.class));
    }

    @Test
    void accessShare_shouldNotRecordWhenAccessingOwnShare() {
        UserContext.setUserId(OWNER_ID);

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID) // same as logged-in user
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);

        shareService.accessShare(SHARE_TOKEN, null);

        verify(receivedShareMapper, never()).insert((ReceivedShare) any());
        verify(receivedShareMapper, never()).countByUserAndShare(anyLong(), anyLong());
    }

    @Test
    void accessShare_shouldNotRecordWhenNotLoggedIn() {
        // UserContext.getUserId() returns null (not set)
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);

        shareService.accessShare(SHARE_TOKEN, null);

        verify(receivedShareMapper, never()).insert((ReceivedShare) any());
        verify(receivedShareMapper, never()).countByUserAndShare(anyLong(), anyLong());
    }

    @Test
    void accessShare_shouldNotDuplicateReceivedRecord() {
        UserContext.setUserId(OTHER_USER_ID);

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        // Already recorded
        when(receivedShareMapper.countByUserAndShare(OTHER_USER_ID, 1L)).thenReturn(1);

        shareService.accessShare(SHARE_TOKEN, null);

        verify(receivedShareMapper, never()).insert((ReceivedShare) any());
    }

    // ==================== downloadFromShare ====================

    @Test
    void downloadFromShare_shouldIncrementDownloadCount() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("download")
                .setDownloadCount(0);

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(shareMapper.selectById(1L)).thenReturn(share);

        shareService.downloadFromShare(SHARE_TOKEN, null);

        verify(shareMapper).incrementDownloadCount(1L);
    }

    @Test
    void accessShare_shouldNotIncrementDownloadCount() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);

        shareService.accessShare(SHARE_TOKEN, null);

        verify(shareMapper, never()).incrementDownloadCount(anyLong());
    }

    // ==================== listMyShares ====================

    @Test
    void listMyShares_shouldReturnReceivedFalse() {
        com.baomidou.mybatisplus.core.metadata.IPage<Share> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        Share share = new Share().setId(1L).setOwnerId(OWNER_ID).setItemId(ITEM_ID);
        page.setRecords(java.util.List.of(share));
        page.setTotal(1);

        when(shareMapper.selectActiveShares(any(), eq(OWNER_ID))).thenReturn(page);

        java.util.List<ShareVO> vos = shareService.listMyShares(OWNER_ID, 1, 20).getRecords();

        assertEquals(1, vos.size());
        assertFalse(vos.get(0).getIsReceived());
    }

    // ==================== listReceivedShares ====================

    @Test
    void listReceivedShares_shouldReturnReceivedTrue() {
        ReceivedShare rs = new ReceivedShare()
                .setUserId(OWNER_ID)
                .setShareId(1L)
                .setItemId(ITEM_ID)
                .setOwnerId(OTHER_USER_ID)
                .setAccessToken(SHARE_TOKEN);

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OTHER_USER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(receivedShareMapper.selectByUserId(any(), eq(OWNER_ID)))
                .thenAnswer(invocation -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<ReceivedShare> p = invocation.getArgument(0);
                    p.setRecords(java.util.List.of(rs));
                    p.setTotal(1);
                    return p;
                });
        when(shareMapper.selectById(1L)).thenReturn(share);

        java.util.List<ShareVO> vos = shareService.listReceivedShares(OWNER_ID, 1, 20).getRecords();

        assertEquals(1, vos.size());
        assertTrue(vos.get(0).getIsReceived());
    }

    @Test
    void listReceivedShares_shouldHandleDeletedShare() {
        ReceivedShare rs = new ReceivedShare()
                .setUserId(OWNER_ID)
                .setShareId(1L)
                .setItemId(ITEM_ID)
                .setOwnerId(OTHER_USER_ID)
                .setAccessToken(SHARE_TOKEN);

        when(receivedShareMapper.selectByUserId(any(), eq(OWNER_ID)))
                .thenAnswer(invocation -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<ReceivedShare> p = invocation.getArgument(0);
                    p.setRecords(java.util.List.of(rs));
                    p.setTotal(1);
                    return p;
                });
        // Share was deleted — mapper returns null
        when(shareMapper.selectById(1L)).thenReturn(null);

        java.util.List<ShareVO> vos = shareService.listReceivedShares(OWNER_ID, 1, 20).getRecords();

        // Should handle gracefully: vo is null, filtered by Page.convert
        assertEquals(1, vos.size());
        assertNull(vos.get(0));
    }

    // ==================== listShareItems ====================

    @Test
    void listShareItems_shouldListFolderContents() {
        UserContext.setUserId(OTHER_USER_ID);

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        Item sharedItem = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(true);

        Item child1 = new Item().setId(101L).setOwnerId(OWNER_ID).setParentId(ITEM_ID).setName("doc.pdf").setIsDirectory(false);
        Item child2 = new Item().setId(102L).setOwnerId(OWNER_ID).setParentId(ITEM_ID).setName("photo.jpg").setIsDirectory(false);
        Item child3 = new Item().setId(103L).setOwnerId(OWNER_ID).setParentId(ITEM_ID).setName("subfolder").setIsDirectory(true);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Item> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(java.util.List.of(child1, child2, child3));
        page.setTotal(3);

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(sharedItem);
        when(itemMapper.selectChildrenByOwnerId(any(), eq(OWNER_ID), eq(ITEM_ID))).thenReturn(page);

        IPage<ItemVO> result = shareService.listShareItems(SHARE_TOKEN, null, null, 1, 20);

        assertEquals(3, result.getRecords().size());
        UserContext.remove();
    }

    @Test
    void listShareItems_shouldReturnSingleFile() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        Item sharedItem = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(false)
                .setName("single.pdf");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(sharedItem);

        IPage<ItemVO> result = shareService.listShareItems(SHARE_TOKEN, null, null, 1, 20);

        assertEquals(1, result.getRecords().size());
    }

    @Test
    void listShareItems_shouldThrowForInvalidShare() {
        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(null);

        assertThrows(BizException.class,
                () -> shareService.listShareItems(SHARE_TOKEN, null, null, 1, 20));
    }

    // ==================== getShareDownloadInfo ====================

    @Test
    void getShareDownloadInfo_shouldReturnDownloadInfo() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("download");

        Item folderItem = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(true);

        Item targetItem = new Item()
                .setId(200L)
                .setOwnerId(OWNER_ID)
                .setParentId(ITEM_ID)
                .setIsDirectory(false)
                .setName("report.pdf")
                .setStorageKey("uploads/report.pdf")
                .setMimeType("application/pdf")
                .setSize(50000L);

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(itemMapper.selectById(200L)).thenReturn(targetItem);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(folderItem);
        when(shareMapper.incrementDownloadCount(1L)).thenReturn(1);

        FileDownloadVO result = shareService.getShareDownloadInfo(SHARE_TOKEN, null, 200L);

        assertNotNull(result);
        assertEquals("report.pdf", result.getFileName());
        assertEquals("application/pdf", result.getMimeType());
        assertEquals(Long.valueOf(50000), result.getFileSize());
        assertEquals("uploads/report.pdf", result.getStorageKey());
        verify(shareMapper).incrementDownloadCount(1L);
    }

    @Test
    void getShareDownloadInfo_shouldRejectViewOnlyPermission() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);

        BizException ex = assertThrows(BizException.class,
                () -> shareService.getShareDownloadInfo(SHARE_TOKEN, null, 200L));
        assertTrue(ex.getMessage().contains("不允许下载"));
    }

    // ==================== saveShareFiles ====================

    @Test
    void saveShareFiles_shouldSaveWithIsFromShare() {
        UserContext.setUserId(OTHER_USER_ID);

        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("download");

        Item folderItem = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(true);

        Item fileItem = new Item()
                .setId(200L)
                .setOwnerId(OWNER_ID)
                .setParentId(ITEM_ID)
                .setName("photo.jpg")
                .setMimeType("image/jpeg")
                .setSize(50000L)
                .setStorageKey("uploads/abc/photo.jpg")
                .setIsDeleted(false)
                .setIsDirectory(false);

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(itemMapper.selectById(200L)).thenReturn(fileItem);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(folderItem);
        when(storageService.getInputStream(anyString())).thenReturn(new java.io.ByteArrayInputStream("test".getBytes()));
        doNothing().when(storageService).store(anyString(), any(java.io.InputStream.class));
        when(itemMapper.insert(any(Item.class))).thenReturn(1);

        java.util.List<ItemVO> result = shareService.saveShareFiles(SHARE_TOKEN, null, java.util.List.of(200L));

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsFromShare());
        UserContext.remove();
    }

    @Test
    void saveShareFiles_shouldRejectEmptyList() {
        BizException ex = assertThrows(BizException.class,
                () -> shareService.saveShareFiles(SHARE_TOKEN, null, java.util.Collections.emptyList()));
        assertTrue(ex.getMessage().contains("请选择要保存的文件"));
    }
}
