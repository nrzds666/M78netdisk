package com.m78.netdisk.common;

import com.m78.netdisk.common.storage.LocalStorageService;
import com.m78.netdisk.common.util.FFmpegUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RED test: LocalStorageService.getVideoSnapshot() cache, semaphore, FFmpeg integration.
 */
class LocalStorageServiceVideoSnapshotTest {

    @TempDir
    Path tempDir;

    private LocalStorageService service;
    private FFmpegUtil ffmpegUtil;
    private Path videoFile;
    private static final byte[] FAKE_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, 0x00, 0x10, (byte) 0xFF, (byte) 0xD9};

    @BeforeEach
    void setUp() throws Exception {
        ffmpegUtil = mock(FFmpegUtil.class);
        when(ffmpegUtil.captureFrame(anyString(), anyLong())).thenReturn(Optional.of(FAKE_JPEG));

        // 使用反射创建 LocalStorageService 并注入 mock FFmpegUtil
        service = new LocalStorageService(ffmpegUtil);
        var storePath = com.m78.netdisk.common.storage.LocalStorageService.class
                .getDeclaredField("storagePath");
        storePath.setAccessible(true);
        storePath.set(service, tempDir.toString());
        // 手动调用 @PostConstruct init()
        var init = LocalStorageService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(service);

        // 创建一个测试视频文件
        videoFile = tempDir.resolve("test_video.mp4");
        Files.write(videoFile, new byte[]{0, 0, 0, 0}); // dummy video
    }

    @Test
    void getVideoSnapshot_whenCacheMiss_shouldCallFFmpegAndReturnJPEG() {
        InputStream result = service.getVideoSnapshot("test_video.mp4", 0);

        assertNotNull(result);
        verify(ffmpegUtil).captureFrame(videoFile.toAbsolutePath().toString(), 0);
    }

    @Test
    void getVideoSnapshot_whenCacheHit_shouldNotCallFFmpeg() {
        // 第一次调用：写入缓存
        service.getVideoSnapshot("test_video.mp4", 0);

        // 第二次调用：命中缓存
        InputStream result = service.getVideoSnapshot("test_video.mp4", 0);

        assertNotNull(result);
        verify(ffmpegUtil, times(1)).captureFrame(anyString(), anyLong());
    }

    @Test
    void getVideoSnapshot_whenFFmpegFails_shouldReturnNull() {
        when(ffmpegUtil.captureFrame(anyString(), anyLong())).thenReturn(Optional.empty());

        InputStream result = service.getVideoSnapshot("test_video.mp4", 0);

        assertNull(result);
    }

    @Test
    void getVideoSnapshot_whenFileNotExists_shouldReturnNull() {
        InputStream result = service.getVideoSnapshot("nonexistent.mp4", 0);

        assertNull(result);
        verifyNoInteractions(ffmpegUtil);
    }

    @Test
    void getVideoSnapshot_withNullKey_shouldReturnNull() {
        assertNull(service.getVideoSnapshot(null, 0));
        assertNull(service.getVideoSnapshot("", 0));
        verifyNoInteractions(ffmpegUtil);
    }
}
