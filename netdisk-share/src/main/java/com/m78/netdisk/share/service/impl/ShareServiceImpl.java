package com.m78.netdisk.share.service.impl;

import cn.hutool.core.util.StrUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.po.Share;
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public ShareVO createShare(Long ownerId, CreateShareDTO dto) {
        // 校验文件所有权
        Item item = itemMapper.selectById(dto.getItemId());
        if (item == null || !item.getOwnerId().equals(ownerId)) {
            throw new BizException("文件不存在");
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
        if (dto.getExpireHours() != null && dto.getExpireHours() > 0) {
            share.setExpireAt(LocalDateTime.now().plusHours(dto.getExpireHours()));
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
                .build();
    }
}