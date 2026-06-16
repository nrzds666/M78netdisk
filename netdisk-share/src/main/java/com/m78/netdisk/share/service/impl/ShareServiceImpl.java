package com.m78.netdisk.share.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.enums.ShareExpire;
import com.m78.netdisk.share.domain.po.ReceivedShare;
import com.m78.netdisk.share.domain.po.Share;
import com.m78.netdisk.share.mapper.ReceivedShareMapper;
import com.m78.netdisk.share.domain.vo.ShareVO;
import com.m78.netdisk.share.mapper.ShareMapper;
import com.m78.netdisk.share.service.IShareService;
import com.m78.netdisk.user.domain.po.User;
import com.m78.netdisk.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements IShareService {

    private final ShareMapper shareMapper;
    private final ItemMapper itemMapper;
    private final StringRedisTemplate redisTemplate;
    private final ReceivedShareMapper receivedShareMapper;
    private final StorageService storageService;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public ShareVO createShare(Long ownerId, CreateShareDTO dto) {
        // 校验文件所有权
        Item item = itemMapper.selectById(dto.getItemId());
        if (item == null || !item.getOwnerId().equals(ownerId)) {
            throw new BizException("文件不存在");
        }

        // 禁止分享机密文件箱内的文件
        if (Boolean.TRUE.equals(item.getIsVaulted())) {
            throw new BizException("\u673a\u5bc6\u6587\u4ef6\u7bb1\u4e2d\u7684\u6587\u4ef6\u65e0\u6cd5\u5206\u4eab");
        }

        String shareToken = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // 校验权限值
        String permission = StrUtil.isNotBlank(dto.getPermission()) ? dto.getPermission() : "view";
        Set<String> validPermissions = Set.of("view", "download", "edit");
        if (!validPermissions.contains(permission)) {
            throw new BizException("无效的分享权限: " + permission + "，仅支持 view/download/edit");
        }

        Share share = new Share()
                .setOwnerId(ownerId)
                .setItemId(dto.getItemId())
                .setShareToken(shareToken)
                .setPermission(permission)
                .setMaxDownloads(dto.getMaxDownloads())
                .setDownloadCount(0)
                .setIsCanceled(false);

        if (StrUtil.isNotBlank(dto.getPassword())) {
            share.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }
        // 根据 expireType 计算过期时间
        ShareExpire expire = ShareExpire.fromType(dto.getExpireType());
        if (expire.getHours() != null) {
            share.setExpireAt(LocalDateTime.now().plusHours(expire.getHours()));
        }

        shareMapper.insert(share);
        return toShareVO(share);
    }

    @Override
    @Transactional
    public void cancelShare(Long ownerId, Long shareId) {
        Share share = shareMapper.selectById(shareId);
        if (share == null || !share.getOwnerId().equals(ownerId)) {
            throw new BizException("分享链接不存在");
        }
        share.setIsCanceled(true);
        shareMapper.updateById(share);
    }

    @Override
    public IPage<ShareVO> listMyShares(Long ownerId, Integer pageNum, Integer size) {
        Page<Share> page = new Page<>(pageNum, Math.min(size, 100));
        return shareMapper.selectActiveShares(page, ownerId)
                .convert(this::toShareVO);
    }

    @Override
    @Transactional
    public ShareVO accessShare(String shareToken, String password) {
        Share share = shareMapper.selectValidShare(shareToken);
        if (share == null) {
            throw new BizException("分享链接不存在或已失效");
        }

        // Redis-based rate limiting for password brute force
        String failKey = "share:fail:" + shareToken;
        String lockKey = "share:lock:" + shareToken;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BizException(429, "提取码已锁定，请稍后再试");
        }

        if (StrUtil.isNotBlank(share.getPasswordHash())) {
            if (StrUtil.isBlank(password)) {
                // Return basic info without granting access
                return toShareVO(share, false);
            }
            if (!passwordEncoder.matches(password, share.getPasswordHash())) {
                // Record failed attempt
                Long failCount = redisTemplate.opsForValue().increment(failKey);
                if (failCount != null && failCount == 1) {
                    redisTemplate.expire(failKey, 1, TimeUnit.HOURS);
                }
                if (failCount != null && failCount >= 5) {
                    redisTemplate.opsForValue().set(lockKey, "1", 10, TimeUnit.MINUTES);
                }
                throw new BizException(403, "提取码错误");
            }
        }

        // Clear fail counters on success
        redisTemplate.delete(failKey);
        redisTemplate.delete(lockKey);

        // 如果用户已登录且不是自己的分享，记录接收（幂等）
        Long currentUserId = com.m78.netdisk.common.utils.UserContext.getUserId();
        if (currentUserId != null && !currentUserId.equals(share.getOwnerId())) {
            if (receivedShareMapper.countByUserAndShare(currentUserId, share.getId()) == 0) {
                ReceivedShare rs = new ReceivedShare()
                        .setUserId(currentUserId)
                        .setShareId(share.getId())
                        .setItemId(share.getItemId())
                        .setOwnerId(share.getOwnerId())
                        .setAccessToken(shareToken);
                receivedShareMapper.insert(rs);
            }
        }

        return toShareVO(share);
    }

    @Override
    @Transactional
    public ShareVO downloadFromShare(String shareToken, String password) {
        // First verify access (reuses accessShare logic without manipulating counters)
        ShareVO vo = accessShare(shareToken, password);

        // Only now increment download count
        Share share = shareMapper.selectValidShare(shareToken);
        if (share == null) {
            throw new BizException("分享链接不存在或已失效");
        }
        shareMapper.incrementDownloadCount(share.getId());
        share = shareMapper.selectById(share.getId());

        return toShareVO(share);
    }

    // ==================== Share Item Browsing ====================

    @Override
    public IPage<ItemVO> listShareItems(String shareToken, String password,
                                         Long parentId, Integer pageNum, Integer size) {
        // Verify share access
        ShareVO vo = accessShare(shareToken, password);
        Share share = shareMapper.selectValidShare(shareToken);
        if (share == null) {
            throw new BizException("分享链接不存在或已失效");
        }

        Long ownerId = share.getOwnerId();
        Long sharedItemId = share.getItemId();

        // Get the shared item to check if it's a file or folder
        Item sharedItem = itemMapper.selectById(sharedItemId);
        if (sharedItem == null) {
            throw new BizException("分享的文件不存在");
        }

        Page<Item> page = new Page<>(pageNum, Math.min(size, 100));

        if (!sharedItem.getIsDirectory()) {
            // Single file share — return just that file
            IPage<Item> singleResult = new Page<>(1, 1);
            List<Item> items = new ArrayList<>();
            items.add(sharedItem);
            singleResult.setRecords(items);
            singleResult.setTotal(1);
            return singleResult.convert(this::itemToItemVO);
        }

        // Folder share — list children of the shared folder or subfolder
        Long effectiveParentId = (parentId != null && parentId > 0) ? parentId : sharedItemId;

        IPage<Item> itemPage = itemMapper.selectChildrenByOwnerId(page, ownerId, effectiveParentId);
        return itemPage.convert(this::itemToItemVO);
    }

    // ==================== Share File Download ====================

    @Override
    public FileDownloadVO getShareDownloadInfo(String shareToken, String password, Long itemId) {
        // Verify share access
        accessShare(shareToken, password);
        Share share = shareMapper.selectValidShare(shareToken);
        if (share == null) {
            throw new BizException("分享链接不存在或已失效");
        }

        // Check permission: must allow download
        if (!"download".equals(share.getPermission()) && !"edit".equals(share.getPermission())) {
            throw new BizException("该分享链接不允许下载");
        }

        // Validate the item belongs to the share owner and is part of this share
        Item item = itemMapper.selectById(itemId);
        if (item == null || !item.getOwnerId().equals(share.getOwnerId())) {
            throw new BizException("文件不存在");
        }
        if (item.getIsDirectory()) {
            throw new BizException("不支持下载文件夹");
        }
        if (item.getIsDeleted() != null && item.getIsDeleted()) {
            throw new BizException("文件已被删除");
        }

        // Verify item is a descendant of the shared folder (or is the shared file itself)
        if (!isDescendantOf(share.getOwnerId(), itemId, share.getItemId())) {
            throw new BizException("文件不属于该分享");
        }

        // Increment download count
        shareMapper.incrementDownloadCount(share.getId());

        return FileDownloadVO.builder()
                .storageKey(item.getStorageKey())
                .fileName(item.getName())
                .mimeType(item.getMimeType() != null ? item.getMimeType() : "application/octet-stream")
                .fileSize(item.getSize())
                .build();
    }

    // ==================== Save Shared Files ====================

    @Override
    @Transactional
    public List<ItemVO> saveShareFiles(String shareToken, String password, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            throw new BizException("请选择要保存的文件");
        }

        // Verify share access
        accessShare(shareToken, password);
        Share share = shareMapper.selectValidShare(shareToken);
        if (share == null) {
            throw new BizException("分享链接不存在或已失效");
        }

        Long currentUserId = com.m78.netdisk.common.utils.UserContext.getUserId();
        if (currentUserId == null) {
            throw new BizException("用户未登录");
        }

        List<ItemVO> result = new ArrayList<>();

        for (Long itemId : itemIds) {
            Item original = itemMapper.selectById(itemId);
            if (original == null || !original.getOwnerId().equals(share.getOwnerId())) {
                throw new BizException("文件不存在: " + itemId);
            }
            if (original.getIsDirectory()) {
                throw new BizException("暂不支持保存文件夹，请选择具体文件: " + original.getName());
            }
            if (original.getIsDeleted() != null && original.getIsDeleted()) {
                throw new BizException("文件已被删除: " + original.getName());
            }

            // Verify item is part of this share
            if (!isDescendantOf(share.getOwnerId(), itemId, share.getItemId())) {
                throw new BizException("文件不属于该分享: " + original.getName());
            }

            // Copy the file content to a new storage key
            String newStorageKey = "saves/" + UUID.randomUUID().toString().replace("-", "")
                    + "/" + original.getName();

            try (InputStream in = storageService.getInputStream(original.getStorageKey())) {
                storageService.store(newStorageKey, in);
            } catch (Exception e) {
                throw new BizException("保存文件失败: " + original.getName());
            }

            // Create a new Item record for the current user
            Item newItem = new Item()
                    .setOwnerId(currentUserId)
                    .setParentId(null) // saved to root
                    .setName(original.getName())
                    .setIsDirectory(false)
                    .setSize(original.getSize())
                    .setMimeType(original.getMimeType())
                    .setStorageKey(newStorageKey)
                    .setPath("/" + original.getName())
                    .setVersion(1)
                    .setIsFromShare(true);

            itemMapper.insert(newItem);
            result.add(itemToItemVO(newItem));
        }

        return result;
    }

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public IPage<ShareVO> listReceivedShares(Long userId, Integer pageNum, Integer size) {
        Page<ReceivedShare> page = new Page<>(pageNum, Math.min(size, 100));
        return receivedShareMapper.selectByUserId(page, userId)
                .convert(rs -> {
                    Share share = shareMapper.selectById(rs.getShareId());
                    ShareVO vo = toShareVO(share);
                    if (vo != null) {
                        vo.setIsReceived(true);
                    }
                    return vo;
                });
    }

    private ShareVO toShareVO(Share share, boolean accessGranted) {
        if (share == null) return null;

        // 查文件信息
        String fileName = null;
        Boolean isDirectory = null;
        Long fileSize = null;
        String mimeType = null;
        if (share.getItemId() != null) {
            Item item = itemMapper.selectById(share.getItemId());
            if (item != null) {
                fileName = item.getName();
                isDirectory = item.getIsDirectory();
                fileSize = item.getSize();
                mimeType = item.getMimeType();
            }
        }

        return ShareVO.builder()
                .id(share.getId())
                .ownerId(share.getOwnerId())
                .shareToken(share.getShareToken())
                .permission(share.getPermission())
                .hasPassword(StrUtil.isNotBlank(share.getPasswordHash()))
                .expireAt(share.getExpireAt() != null ? share.getExpireAt().format(ISO_FMT) : null)
                .maxDownloads(share.getMaxDownloads())
                .downloadCount(share.getDownloadCount())
                .isCanceled(share.getIsCanceled())
                .createdAt(share.getCreatedAt() != null ? share.getCreatedAt().format(ISO_FMT) : null)
                .fileName(fileName)
                .isDirectory(isDirectory)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .expireLabel(determineExpireLabel(share.getExpireAt()))
                .isReceived(false)
                .accessGranted(accessGranted)
                .ownerName(lookupOwnerName(share.getOwnerId()))
                .ownerAvatar(lookupOwnerAvatar(share.getOwnerId()))
                .build();
    }

    private ShareVO toShareVO(Share share) {
        return toShareVO(share, true);
    }

    private String determineExpireLabel(LocalDateTime expireAt) {
        if (expireAt == null) return "永久";
        long hours = java.time.Duration.between(LocalDateTime.now(), expireAt).toHours();
        if (hours <= 24) return "一天";
        if (hours <= 168) return "一周";
        return "一个月";
    }

    private String lookupOwnerName(Long ownerId) {
        if (ownerId == null) return null;
        User user = userMapper.selectById(ownerId);
        return user != null ? user.getUsername() : null;
    }

    private String lookupOwnerAvatar(Long ownerId) {
        if (ownerId == null) return null;
        User user = userMapper.selectById(ownerId);
        return user != null ? user.getAvatarUrl() : null;
    }

    private ItemVO itemToItemVO(Item item) {
        if (item == null) return null;
        return ItemVO.builder()
                .id(item.getId()).ownerId(item.getOwnerId()).parentId(item.getParentId())
                .name(item.getName()).isDirectory(item.getIsDirectory()).size(item.getSize())
                .mimeType(item.getMimeType()).etag(item.getEtag()).thumbnailKey(item.getThumbnailKey())
                .path(item.getPath()).version(item.getVersion()).isFromShare(item.getIsFromShare())
                .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toString() : null)
                .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : null)
                .build();
    }

    /**
     * Check if potentialChildId is a descendant of (or equal to) ancestorId,
     * where all items belong to the same ownerId.
     */
    private boolean isDescendantOf(Long ownerId, Long potentialChildId, Long ancestorId) {
        if (potentialChildId.equals(ancestorId)) return true;
        Long currentId = potentialChildId;
        while (currentId != null) {
            if (currentId.equals(ancestorId)) return true;
            Item current = itemMapper.selectById(currentId);
            if (current == null || current.getParentId() == null) break;
            if (!current.getOwnerId().equals(ownerId)) return false;
            currentId = current.getParentId();
        }
        return false;
    }
}