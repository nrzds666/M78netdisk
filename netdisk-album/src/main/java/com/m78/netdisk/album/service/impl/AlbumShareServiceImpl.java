package com.m78.netdisk.album.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.album.domain.po.Album;
import com.m78.netdisk.album.domain.po.AlbumItem;
import com.m78.netdisk.album.domain.po.AlbumShare;
import com.m78.netdisk.album.domain.vo.AlbumItemVO;
import com.m78.netdisk.album.domain.vo.AlbumShareVO;
import com.m78.netdisk.album.domain.vo.AlbumVO;
import com.m78.netdisk.album.mapper.AlbumItemMapper;
import com.m78.netdisk.album.mapper.AlbumMapper;
import com.m78.netdisk.album.mapper.AlbumShareMapper;
import com.m78.netdisk.album.service.IAlbumShareService;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumShareServiceImpl implements IAlbumShareService {

    private final AlbumMapper albumMapper;
    private final AlbumShareMapper albumShareMapper;
    private final AlbumItemMapper albumItemMapper;
    private final ItemMapper itemMapper;
    private final StorageService storageService;

    @Override
    @Transactional
    public AlbumShareVO createShare(Long userId, Long albumId, Integer expireDays) {
        // Verify ownership
        Album album = albumMapper.selectById(albumId);
        if (album == null) {
            throw new BizException("相册不存在");
        }
        if (!album.getUserId().equals(userId)) {
            throw new BizException("无权操作此相册");
        }

        // Generate unique token
        String token = UUID.randomUUID().toString().replace("-", "");

        // Calculate expiry
        LocalDateTime expireAt = null;
        if (expireDays != null && expireDays > 0) {
            expireAt = LocalDateTime.now().plusDays(expireDays);
        }

        AlbumShare share = new AlbumShare()
                .setAlbumId(albumId)
                .setUserId(userId)
                .setShareToken(token)
                .setExpireAt(expireAt)
                .setIsActive(true);
        albumShareMapper.insert(share);

        log.info("相册分享已创建: albumId={}, token={}, expireDays={}", albumId, token, expireDays);

        return AlbumShareVO.builder()
                .id(share.getId())
                .albumId(albumId)
                .shareToken(token)
                .shareUrl("/album-share/" + token)
                .expireAt(expireAt != null ? expireAt.toString() : null)
                .createdAt(share.getCreatedAt() != null ? share.getCreatedAt().toString() : null)
                .build();
    }

    @Override
    public AlbumVO getSharedAlbum(String token) {
        AlbumShare share = albumShareMapper.selectByActiveToken(token);
        if (share == null) {
            throw new BizException("分享链接不存在或已过期");
        }

        Album album = albumMapper.selectById(share.getAlbumId());
        if (album == null) {
            throw new BizException("相册不存在");
        }

        // Get all items from the album
        List<AlbumItem> albumItems = albumItemMapper.selectList(
                new LambdaQueryWrapper<AlbumItem>()
                        .eq(AlbumItem::getAlbumId, share.getAlbumId())
                        .orderByDesc(AlbumItem::getAddedAt));

        List<Long> itemIds = albumItems.stream()
                .map(AlbumItem::getItemId)
                .collect(Collectors.toList());

        List<AlbumItemVO> items = new ArrayList<>();
        if (!itemIds.isEmpty()) {
            List<Item> itemList = itemMapper.selectBatchIds(itemIds);
            Map<Long, Item> itemMap = itemList.stream()
                    .collect(Collectors.toMap(Item::getId, i -> i));
            for (AlbumItem ai : albumItems) {
                Item item = itemMap.get(ai.getItemId());
                if (item != null && !item.getIsDeleted()) {
                    items.add(AlbumItemVO.builder()
                            .itemId(item.getId())
                            .name(item.getName())
                            .mimeType(item.getMimeType())
                            .size(item.getSize())
                            .thumbnailKey(mapThumbnailUrl(item.getThumbnailKey(), item.getId()))
                            .addedAt(ai.getAddedAt() != null ? ai.getAddedAt().toString() : null)
                            .build());
                }
            }
        }

        // Build VO without cover info (public viewer doesn't need full album details)
        return AlbumVO.builder()
                .id(album.getId())
                .name(album.getName())
                .description(album.getDescription())
                .itemCount(items.size())
                .items(items)
                .build();
    }

    @Override
    public void streamSharedAlbumFile(String token, Long itemId,
                                       jakarta.servlet.http.HttpServletRequest request,
                                       jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        AlbumShare share = albumShareMapper.selectByActiveToken(token);
        if (share == null) {
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND, "分享链接不存在或已过期");
            return;
        }

        // Verify item belongs to the shared album
        Long count = albumItemMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlbumItem>()
                        .eq(AlbumItem::getAlbumId, share.getAlbumId())
                        .eq(AlbumItem::getItemId, itemId));
        if (count == null || count == 0) {
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN, "文件不属于该相册");
            return;
        }

        // Get file info
        Item item = itemMapper.selectById(itemId);
        if (item == null || Boolean.TRUE.equals(item.getIsDeleted())) {
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND, "文件不存在或已被删除");
            return;
        }

        // Stream the file
        String encodedName = URLEncoder.encode(item.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "inline; filename=\"" + encodedName + "\"");
        response.setContentType(item.getMimeType() != null ? item.getMimeType() : "application/octet-stream");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentLengthLong(item.getSize());

        try (InputStream is = storageService.getInputStream(item.getStorageKey())) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                response.getOutputStream().write(buf, 0, len);
            }
            response.getOutputStream().flush();
        } catch (Exception e) {
            log.error("流式输出分享相册文件失败: token={}, itemId={}", token, itemId, e);
            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件读取失败");
        }
    }

    private String mapThumbnailUrl(String rawKey, Long itemId) {
        if (rawKey == null) return null;
        String publicUrl = storageService.getPublicUrl(rawKey);
        if (publicUrl != null) return publicUrl;
        if (itemId != null) return "/api/files/thumbnail/" + itemId;
        return null;
    }
}
