package com.m78.netdisk.share.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.enums.ShareExpire;
import com.m78.netdisk.share.domain.po.ReceivedShare;
import com.m78.netdisk.share.domain.po.Share;
import com.m78.netdisk.share.mapper.ReceivedShareMapper;
import com.m78.netdisk.share.domain.vo.ShareVO;
import com.m78.netdisk.share.mapper.ShareMapper;
import com.m78.netdisk.share.service.IShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
                throw new BizException(403, "需要提取码");
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

    private ShareVO toShareVO(Share share) {
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
                .build();
    }

    private String determineExpireLabel(LocalDateTime expireAt) {
        if (expireAt == null) return "\u6c38\u4e45";
        long hours = java.time.Duration.between(LocalDateTime.now(), expireAt).toHours();
        if (hours <= 24) return "\u4e00\u5929";
        if (hours <= 168) return "\u4e00\u5468";
        return "\u4e00\u4e2a\u6708";
    }
}