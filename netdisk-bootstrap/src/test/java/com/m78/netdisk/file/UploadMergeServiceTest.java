package com.m78.netdisk.file;

import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.po.UploadChunk;
import com.m78.netdisk.file.domain.po.UploadTask;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.file.mapper.ItemVersionMapper;
import com.m78.netdisk.file.mapper.UploadChunkMapper;
import com.m78.netdisk.file.mapper.UploadTaskMapper;
import com.m78.netdisk.file.service.impl.UploadMergeService;
import com.m78.netdisk.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test that UploadMergeService merges chunks via InputStream (not byte[]).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadMergeServiceTest {

    @Mock private ItemMapper itemMapper;
    @Mock private ItemVersionMapper itemVersionMapper;
    @Mock private UploadTaskMapper uploadTaskMapper;
    @Mock private UploadChunkMapper uploadChunkMapper;
    @Mock private StorageService storageService;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private UploadMergeService mergeService;

    @Test
    void red_performMerge_shouldBeAsyncAnnotated() throws Exception {
        java.lang.reflect.Method method = UploadMergeService.class
                .getMethod("performMerge", Long.class, Long.class);
        org.springframework.scheduling.annotation.Async ann =
                method.getAnnotation(org.springframework.scheduling.annotation.Async.class);
        assert ann != null : "performMerge should be @Async";
    }

    @Test
    void red_completeUploadInFileServiceImpl_shouldNotHaveAsync() throws Exception {
        // Verify the self-invocation bug pattern is removed:
        // completeUpload delegates to UploadMergeService, not @Async on itself
        com.m78.netdisk.file.service.impl.FileServiceImpl fs =
                new com.m78.netdisk.file.service.impl.FileServiceImpl(
                        itemMapper, null, uploadTaskMapper,
                        uploadChunkMapper, storageService, userMapper, null, mergeService);

        java.lang.reflect.Method method = com.m78.netdisk.file.service.impl.FileServiceImpl.class
                .getMethod("completeUpload", Long.class, Long.class);
        org.springframework.scheduling.annotation.Async ann =
                method.getAnnotation(org.springframework.scheduling.annotation.Async.class);
        assert ann == null : "completeUpload should NOT be @Async";
    }

    @Test
    void mergeChunks_shouldStoreWithInputStream() throws Exception {
        // Arrange
        UploadChunk chunk1 = new UploadChunk().setStorageKey("chunk_0").setChunkIndex(0);
        UploadChunk chunk2 = new UploadChunk().setStorageKey("chunk_1").setChunkIndex(1);
        List<UploadChunk> chunks = Arrays.asList(chunk1, chunk2);

        UploadTask task = new UploadTask()
                .setId(1L).setFileName("merged.txt").setFileSize(200L)
                .setTotalChunks(2).setReceivedChunks(2);
        when(uploadTaskMapper.selectById(1L)).thenReturn(task);
        when(uploadChunkMapper.selectList(any())).thenReturn(chunks);
        when(storageService.getInputStream("chunk_0")).thenReturn(new ByteArrayInputStream("hello".getBytes()));
        when(storageService.getInputStream("chunk_1")).thenReturn(new ByteArrayInputStream("world".getBytes()));

        // Act
        mergeService.performMerge(1L, 1L);

        // Verify: store() was called with InputStream, NOT byte[]
        verify(storageService).store(anyString(), any(InputStream.class));
        verify(storageService, never()).store(anyString(), any(byte[].class));
    }
}
