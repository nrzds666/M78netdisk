package com.m78.netdisk.share;

import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.po.Share;
import com.m78.netdisk.share.domain.vo.ShareVO;
import com.m78.netdisk.share.mapper.ShareMapper;
import com.m78.netdisk.share.service.impl.ShareServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareServiceImplTest {

    @Mock private ShareMapper shareMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private ShareServiceImpl shareService;

    private static final Long OWNER_ID = 1L;
    private static final Long ITEM_ID = 100L;
    private static final String SHARE_TOKEN = "abcdef1234567890";

    // ========== Fix: createShare ==========

    @Test
    void createShare_shouldCreateShareSuccessfully() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);
        dto.setPermission("download");
        dto.setPassword("1234");

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setName("doc.pdf")
                .setIsDirectory(false);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        ShareVO vo = shareService.createShare(OWNER_ID, dto);

        assertNotNull(vo);
        assertNotNull(vo.getShareToken());
        assertTrue(vo.getHasPassword());
        verify(shareMapper).insert(any(Share.class));
    }

    @Test
    void createShare_shouldRejectNonOwnedItem() {
        CreateShareDTO dto = new CreateShareDTO();
        dto.setItemId(ITEM_ID);

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(999L);  // different owner

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        assertThrows(BizException.class,
                () -> shareService.createShare(OWNER_ID, dto));
    }

    // ========== Fix: Redis rate limiting on accessShare ==========

    @Test
    void accessShare_shouldLockAfterFiveFailedAttempts() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPasswordHash("$2a$10$...")  // BCrypt hash of "wrong"
                .setPermission("view");

        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // First failed attempt
        when(valueOps.increment("share:fail:" + SHARE_TOKEN)).thenReturn(1L);

        assertThrows(BizException.class,
                () -> shareService.accessShare(SHARE_TOKEN, "right"));

        // On 5th failure, lock should be set
        when(valueOps.increment("share:fail:" + SHARE_TOKEN)).thenReturn(5L);
        assertThrows(BizException.class,
                () -> shareService.accessShare(SHARE_TOKEN, "right"));
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

        shareService.accessShare(SHARE_TOKEN, null);  // no password needed

        verify(redisTemplate).delete("share:fail:" + SHARE_TOKEN);
        verify(redisTemplate).delete("share:lock:" + SHARE_TOKEN);
    }

    @Test
    void accessShare_shouldThrowWhenLocked() {
        Share share = new Share()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setItemId(ITEM_ID)
                .setShareToken(SHARE_TOKEN)
                .setPermission("view");
        when(shareMapper.selectValidShare(SHARE_TOKEN)).thenReturn(share);
        when(redisTemplate.hasKey("share:lock:" + SHARE_TOKEN)).thenReturn(true);

        assertThrows(BizException.class,
                () -> shareService.accessShare(SHARE_TOKEN, "any"));
    }

    // ========== Fix: downloadFromShare increments count only on download ==========

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

        // accessShare should NOT increment download count
        verify(shareMapper, never()).incrementDownloadCount(anyLong());
    }
}
