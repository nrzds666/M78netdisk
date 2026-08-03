package com.m78.netdisk.album.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.album.domain.dto.AddItemsDTO;
import com.m78.netdisk.album.domain.dto.CreateAlbumDTO;
import com.m78.netdisk.album.domain.dto.UpdateAlbumDTO;
import com.m78.netdisk.album.domain.po.Album;
import com.m78.netdisk.album.domain.po.AlbumItem;
import com.m78.netdisk.album.domain.vo.AlbumItemVO;
import com.m78.netdisk.album.domain.vo.AlbumVO;
import com.m78.netdisk.album.mapper.AlbumItemMapper;
import com.m78.netdisk.album.mapper.AlbumMapper;
import com.m78.netdisk.album.service.IAlbumService;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumServiceImpl implements IAlbumService {

    private final AlbumMapper albumMapper;
    private final AlbumItemMapper albumItemMapper;
    private final ItemMapper itemMapper;
    private final StorageService storageService;

    @Override
    @Transactional
    public AlbumVO createAlbum(Long userId, CreateAlbumDTO dto) {
        Album album = new Album()
                .setUserId(userId)
                .setName(dto.getName().trim())
                .setCoverItemId(dto.getCoverItemId())
                .setDescription(dto.getDescription());
        albumMapper.insert(album);

        // Optionally add initial items
        if (dto.getItemIds() != null && !dto.getItemIds().isEmpty()) {
            addItemAssociations(userId, album.getId(), dto.getItemIds());
            // Auto-set cover to first item if no cover explicitly set
            if (dto.getCoverItemId() == null) {
                Long firstId = albumItemMapper.selectLatestItemId(album.getId());
                if (firstId != null) {
                    album.setCoverItemId(firstId);
                    albumMapper.updateById(album);
                }
            }
        }

        return toAlbumVO(album);
    }

    @Override
    @Transactional
    public void deleteAlbum(Long userId, Long albumId) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);
        albumMapper.deleteById(albumId);
        // album_items cascade deleted by FK
        log.info("相册已删除: userId={}, albumId={}", userId, albumId);
    }

    @Override
    @Transactional
    public AlbumVO updateAlbum(Long userId, Long albumId, UpdateAlbumDTO dto) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);

        if (dto.getName() != null) {
            album.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            album.setDescription(dto.getDescription());
        }
        if (dto.getCoverItemId() != null) {
            // Verify the item exists and belongs to user
            Item coverItem = itemMapper.selectById(dto.getCoverItemId());
            if (coverItem == null || !coverItem.getOwnerId().equals(userId)) {
                throw new BizException("封面图片不存在");
            }
            album.setCoverItemId(dto.getCoverItemId());
        }
        if (dto.getSortOrder() != null) {
            album.setSortOrder(dto.getSortOrder());
        }

        albumMapper.updateById(album);
        return toAlbumVO(album);
    }

    @Override
    public IPage<AlbumVO> listAlbums(Long userId, Integer pageNum, Integer size) {
        Page<Album> page = new Page<>(pageNum, Math.min(size, 100));
        IPage<Album> albumPage = albumMapper.selectByUserId(page, userId);
        return albumPage.convert(this::toAlbumVO);
    }

    @Override
    public AlbumVO getAlbumDetail(Long userId, Long albumId, Integer pageNum, Integer size) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);

        // Get paginated item IDs from the album
        Page<AlbumItem> itemPage = new Page<>(pageNum, Math.min(size, 100));
        IPage<AlbumItem> aiPage = albumItemMapper.selectPage(itemPage,
                new LambdaQueryWrapper<AlbumItem>()
                        .eq(AlbumItem::getAlbumId, albumId)
                        .orderByDesc(AlbumItem::getAddedAt));

        // Fetch item details
        List<Long> itemIds = aiPage.getRecords().stream()
                .map(AlbumItem::getItemId)
                .collect(Collectors.toList());

        List<AlbumItemVO> items = new ArrayList<>();
        if (!itemIds.isEmpty()) {
            List<Item> itemList = itemMapper.selectBatchIds(itemIds);
            Map<Long, Item> itemMap = itemList.stream()
                    .collect(Collectors.toMap(Item::getId, i -> i));
            for (AlbumItem ai : aiPage.getRecords()) {
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

        AlbumVO vo = toAlbumVO(album);
        vo.setItems(items);
        // Override itemCount with actual page total
        vo.setItemCount((int) aiPage.getTotal());
        return vo;
    }

    @Override
    @Transactional
    public void addItems(Long userId, Long albumId, AddItemsDTO dto) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);
        addItemAssociations(userId, albumId, dto.getItemIds());

        // Auto-set cover if not set
        if (album.getCoverItemId() == null) {
            Long latestId = albumItemMapper.selectLatestItemId(albumId);
            if (latestId != null) {
                album.setCoverItemId(latestId);
                albumMapper.updateById(album);
            }
        }
    }

    @Override
    @Transactional
    public void removeItems(Long userId, Long albumId, List<Long> itemIds) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);

        albumItemMapper.delete(new LambdaQueryWrapper<AlbumItem>()
                .eq(AlbumItem::getAlbumId, albumId)
                .in(AlbumItem::getItemId, itemIds));

        // If removed item was the cover, clear or auto-set
        if (album.getCoverItemId() != null && itemIds.contains(album.getCoverItemId())) {
            Long latestId = albumItemMapper.selectLatestItemId(albumId);
            album.setCoverItemId(latestId);
            albumMapper.updateById(album);
        }
    }

    @Override
    @Transactional
    public AlbumVO setCover(Long userId, Long albumId, Long itemId) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);

        // Verify item belongs to this album
        Long count = albumItemMapper.selectCount(
                new LambdaQueryWrapper<AlbumItem>()
                        .eq(AlbumItem::getAlbumId, albumId)
                        .eq(AlbumItem::getItemId, itemId));
        if (count == 0) {
            throw new BizException("该文件不在相册中");
        }

        album.setCoverItemId(itemId);
        albumMapper.updateById(album);
        return toAlbumVO(album);
    }

    // ==================== Private helpers ====================

    private void validateOwnership(Album album, Long userId) {
        if (album == null) {
            throw new BizException("相册不存在");
        }
        if (!album.getUserId().equals(userId)) {
            throw new BizException("无权操作此相册");
        }
    }

    private void addItemAssociations(Long userId, Long albumId, List<Long> itemIds) {
        // Validate each item: exists, belongs to user, is image/video, not deleted
        List<Item> items = itemMapper.selectBatchIds(itemIds);
        Map<Long, Item> itemMap = items.stream()
                .collect(Collectors.toMap(Item::getId, i -> i));

        for (Long itemId : itemIds) {
            Item item = itemMap.get(itemId);
            if (item == null || !item.getOwnerId().equals(userId)) {
                throw new BizException("文件不存在: id=" + itemId);
            }
            if (item.getIsDeleted()) {
                throw new BizException("文件已被删除: " + item.getName());
            }
            String mime = item.getMimeType();
            if (mime == null || !(mime.startsWith("image/") || mime.startsWith("video/"))) {
                throw new BizException("只能添加图片或视频文件: " + item.getName());
            }
        }

        // Batch insert (ignore duplicates)
        for (Long itemId : itemIds) {
            try {
                AlbumItem ai = new AlbumItem()
                        .setAlbumId(albumId)
                        .setItemId(itemId);
                albumItemMapper.insert(ai);
            } catch (DuplicateKeyException e) {
                // Skip duplicates silently
                log.debug("文件已在相册中: itemId={}, albumId={}", itemId, albumId);
            }
        }
    }

    private String mapThumbnailUrl(String rawKey, Long itemId) {
        if (rawKey == null) return null;
        String publicUrl = storageService.getPublicUrl(rawKey);
        if (publicUrl != null) return publicUrl;
        if (itemId != null) return "/api/files/thumbnail/" + itemId;
        return null;
    }

    private AlbumVO toAlbumVO(Album album) {
        if (album == null) return null;

        // Get cover thumbnail
        String coverThumbnailKey = null;
        Long coverItemId = album.getCoverItemId();
        String rawKey = null;
        Long mapItemId = null;
        if (coverItemId != null) {
            Item coverItem = itemMapper.selectById(coverItemId);
            if (coverItem != null) {
                rawKey = coverItem.getThumbnailKey();
                mapItemId = coverItem.getId();
            }
        } else {
            // Auto-pick: get latest item's thumbnail with its itemId
            Long latestId = albumItemMapper.selectLatestItemId(album.getId());
            if (latestId != null) {
                Item latestItem = itemMapper.selectById(latestId);
                if (latestItem != null) {
                    rawKey = latestItem.getThumbnailKey();
                    mapItemId = latestItem.getId();
                }
            }
        }
        coverThumbnailKey = mapThumbnailUrl(rawKey, mapItemId);

        // Get item count
        int itemCount = albumMapper.countItems(album.getId());

        return AlbumVO.builder()
                .id(album.getId())
                .name(album.getName())
                .coverItemId(album.getCoverItemId())
                .coverThumbnailKey(coverThumbnailKey)
                .description(album.getDescription())
                .itemCount(itemCount)
                .sortOrder(album.getSortOrder())
                .createdAt(album.getCreatedAt() != null ? album.getCreatedAt().toString() : null)
                .updatedAt(album.getUpdatedAt() != null ? album.getUpdatedAt().toString() : null)
                .build();
    }
}
