package com.m78.netdisk.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.common.util.FFmpegUtil;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 媒体文件缩略图生成服务。
 * 支持图片（Java ImageIO resize）和视频（FFmpeg/OSS 截帧）的缩略图生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private final StorageService storageService;
    private final ItemMapper itemMapper;
    private final FFmpegUtil ffmpegUtil;

    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024; // 20MB
    private static final int THUMBNAIL_WIDTH = 300;

    /**
     * 为文件生成缩略图并更新 DB。
     * <p>
     * 图片：从 StorageService 读取原图 → ImageIO resize 至 300px 宽 → JPEG quality 0.7
     * 视频：通过 StorageService.getVideoSnapshot() 截取首帧
     * <p>
     * 跳过条件：目录、null mimeType、不支持的图片格式、超过 20MB 的大图
     */
    public void generateThumbnail(Item item) {
        if (Boolean.TRUE.equals(item.getIsDirectory())) return;
        if (item.getMimeType() == null) return;

        try {
            byte[] thumbnailBytes = null;

            if (item.getMimeType().startsWith("image/")) {
                thumbnailBytes = generateImageThumbnail(item);
            } else if (item.getMimeType().startsWith("video/")) {
                thumbnailBytes = generateVideoThumbnail(item);
            }

            if (thumbnailBytes == null) return;

            String thumbKey = "thumbnails/" + item.getId() + ".jpg";
            storageService.store(thumbKey, thumbnailBytes);

            // 绕过 version 乐观锁，只更新 thumbnailKey
            itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, item.getId())
                    .set(Item::getThumbnailKey, thumbKey));

            log.debug("缩略图生成完成: itemId={}", item.getId());
        } catch (Exception e) {
            log.warn("缩略图生成失败: itemId={}, mime={}", item.getId(), item.getMimeType(), e);
        }
    }

    private byte[] generateImageThumbnail(Item item) throws Exception {
        // 格式检查：ImageIO 是否支持该 mimeType
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(item.getMimeType());
        if (readers == null || !readers.hasNext()) {
            log.warn("不支持的图片格式，跳过缩略图: mime={}, itemId={}", item.getMimeType(), item.getId());
            return null;
        }

        // 大图跳过
        if (item.getSize() != null && item.getSize() > MAX_IMAGE_SIZE) {
            log.warn("图片过大 ({}), 跳过缩略图: itemId={}", item.getSize(), item.getId());
            return null;
        }

        BufferedImage original;
        try (InputStream in = storageService.getInputStream(item.getStorageKey())) {
            if (in == null) return null;
            original = ImageIO.read(in);
        }

        if (original == null) return null;

        // 原图已经比缩略图小？不缩小，直接存原尺寸
        if (original.getWidth() <= THUMBNAIL_WIDTH) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(original, "jpg", baos);
            return baos.toByteArray();
        }

        // Resize to 300px width
        int newW = THUMBNAIL_WIDTH;
        int newH = (int) (original.getHeight() * ((double) THUMBNAIL_WIDTH / original.getWidth()));
        if (newH < 1) newH = 1;

        BufferedImage thumbnail = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnail.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newW, newH, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(thumbnail, "jpg", baos);
        return baos.toByteArray();
    }

    private byte[] generateVideoThumbnail(Item item) throws Exception {
        try (InputStream in = storageService.getVideoSnapshot(item.getStorageKey(), 0)) {
            if (in == null) return null;
            return in.readAllBytes();
        }
    }
}
