package com.m78.netdisk.file;

import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.common.util.FFmpegUtil;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.file.service.impl.MediaProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RED test: MediaProcessingService thumbnail generation.
 */
@ExtendWith(MockitoExtension.class)
class MediaProcessingServiceTest {

    @Mock private StorageService storageService;
    @Mock private ItemMapper itemMapper;
    @Mock private FFmpegUtil ffmpegUtil;

    @Captor private ArgumentCaptor<byte[]> bytesCaptor;

    private MediaProcessingService service;

    @BeforeEach
    void setUp() {
        service = new MediaProcessingService(storageService, itemMapper, ffmpegUtil);
    }

    // ─── Image thumbnail ───

    @Test
    void generateThumbnail_forImage_shouldResizeAndStore() throws Exception {
        Item item = new Item()
                .setId(1L).setStorageKey("uploads/test.jpg")
                .setMimeType("image/jpeg").setSize(1_000_000L);
        byte[] testImage = createTestImage(800, 600);
        when(storageService.getInputStream("uploads/test.jpg")).thenReturn(new ByteArrayInputStream(testImage));

        service.generateThumbnail(item);

        verify(storageService).store(eq("thumbnails/1.jpg"), bytesCaptor.capture());
        byte[] stored = bytesCaptor.getValue();

        // Verify it's a valid JPEG and 300px wide
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(stored));
        assertNotNull(result);
        assertEquals(300, result.getWidth());
        assertEquals(225, result.getHeight()); // 600 * 300/800
    }

    @Test
    void generateThumbnail_forVideo_shouldCaptureFrameAndStore() throws Exception {
        byte[] fakeJpeg = createTestImage(300, 200);
        when(storageService.getVideoSnapshot("uploads/video.mp4", 0))
                .thenReturn(new ByteArrayInputStream(fakeJpeg));
        Item item = new Item()
                .setId(2L).setStorageKey("uploads/video.mp4")
                .setMimeType("video/mp4");

        service.generateThumbnail(item);

        verify(storageService).store(eq("thumbnails/2.jpg"), bytesCaptor.capture());
        assertTrue(bytesCaptor.getValue().length > 0);
    }

    // ─── Skip cases ───

    @Test
    void generateThumbnail_forDirectory_shouldSkip() {
        Item item = new Item().setIsDirectory(true).setMimeType("image/jpeg");

        service.generateThumbnail(item);

        verifyNoInteractions(storageService, itemMapper);
    }

    @Test
    void generateThumbnail_withNullMimeType_shouldSkip() {
        Item item = new Item().setStorageKey("x").setMimeType(null);

        service.generateThumbnail(item);

        verifyNoInteractions(storageService, itemMapper);
    }

    @Test
    void generateThumbnail_forUnsupportedFormat_shouldSkip() {
        Item item = new Item().setId(3L).setStorageKey("x.webp")
                .setMimeType("image/webp").setSize(100L);

        service.generateThumbnail(item);

        verify(storageService, never()).store(eq("thumbnails/3.jpg"), Mockito.<byte[]>any());
    }

    @Test
    void generateThumbnail_forLargeImage_shouldSkip() {
        Item item = new Item().setId(4L).setStorageKey("large.tif")
                .setMimeType("image/tiff").setSize(30_000_000L); // > 20MB

        service.generateThumbnail(item);

        verify(storageService, never()).store(eq("thumbnails/4.jpg"), Mockito.<byte[]>any());
    }

    // ─── helper ───

    private byte[] createTestImage(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        img.getGraphics().fillRect(0, 0, width, height);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }
}
