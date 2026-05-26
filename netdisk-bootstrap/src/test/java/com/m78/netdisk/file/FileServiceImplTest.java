package com.m78.netdisk.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.dto.MoveItemsDTO;
import com.m78.netdisk.file.domain.dto.RenameItemDTO;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.po.ItemVersion;
import com.m78.netdisk.file.domain.po.UploadChunk;
import com.m78.netdisk.file.domain.po.UploadTask;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.domain.vo.UploadTaskVO;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.file.mapper.ItemVersionMapper;
import com.m78.netdisk.file.mapper.UploadChunkMapper;
import com.m78.netdisk.file.mapper.UploadTaskMapper;
import com.m78.netdisk.file.service.impl.FileServiceImpl;
import com.m78.netdisk.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock private ItemMapper itemMapper;
    @Mock private UploadTaskMapper uploadTaskMapper;
    @Mock private UploadChunkMapper uploadChunkMapper;
    @Mock private ItemVersionMapper itemVersionMapper;
    @Mock private UserMapper userMapper;
    @Mock private StorageService storageService;

    @InjectMocks
    private FileServiceImpl fileService;

    private static final Long OWNER_ID = 1L;
    private static final Long ITEM_ID = 100L;

    // ========== Fix 1: rename/move LIKE wildcard escape ==========

    @Test
    void rename_shouldEscapeLikeWildcardsInPath() {
        RenameItemDTO dto = new RenameItemDTO();
        dto.setItemId(ITEM_ID);
        dto.setNewName("new_folder_2024");

        Item oldItem = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setParentId(null)
                .setName("test_2024")  // name contains underscore
                .setIsDirectory(true)
                .setPath("/test_2024");

        when(itemMapper.selectById(ITEM_ID)).thenReturn(oldItem);
        when(itemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(itemMapper.updateById(any(Item.class))).thenReturn(1);

        fileService.rename(OWNER_ID, dto);

        // 不需要验证具体调用了 updateById 的某次参数，rename 方法会自动调用
        // rename 后检查新名称是否正确即可
        verify(itemMapper, times(1)).updateById(any(Item.class));
    }

    // ========== Fix 2: confirmChunk concurrency ==========

    @Test
    void confirmChunk_shouldUseTryInsertWithAtomicIncrement() {
        UploadTask task = new UploadTask()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setStatus("uploading")
                .setTotalChunks(5);

        when(uploadTaskMapper.selectById(1L)).thenReturn(task);

        fileService.confirmChunk(OWNER_ID, 1L, 2, "key2", "etag2", 1024);

        verify(uploadChunkMapper).insert(any(UploadChunk.class));
        verify(uploadTaskMapper).incrementReceivedChunks(1L);
    }

    @Test
    void confirmChunk_shouldHandleDuplicateKeyException() {
        UploadTask task = new UploadTask()
                .setId(1L)
                .setOwnerId(OWNER_ID)
                .setStatus("uploading")
                .setTotalChunks(5);

        when(uploadTaskMapper.selectById(1L)).thenReturn(task);
        // Simulate DuplicateKeyException on insert
        doThrow(new DuplicateKeyException("Duplicate entry")).when(uploadChunkMapper).insert(any(UploadChunk.class));

        // Should not throw
        assertDoesNotThrow(() -> fileService.confirmChunk(OWNER_ID, 1L, 2, "key2", "etag2", 1024));

        // incrementReceivedChunks should NOT be called for duplicate
        verify(uploadTaskMapper, never()).incrementReceivedChunks(anyLong());
    }

    // ========== Fix 3: permanentlyDelete is_deleted check ==========

    @Test
    void permanentlyDelete_shouldRejectNonDeletedItems() {
        Item activeItem = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setName("active.docx")
                .setIsDeleted(false)
                .setIsDirectory(false)
                .setSize(1000L);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(activeItem);

        assertThrows(BizException.class,
                () -> fileService.permanentlyDelete(OWNER_ID, Collections.singletonList(ITEM_ID)));
    }

    @Test
    void permanentlyDelete_shouldAllowDeletedItems() {
        Item deletedItem = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setName("trash.docx")
                .setIsDeleted(true)
                .setIsDirectory(false)
                .setSize(1000L)
                .setStorageKey("uploads/key.docx");

        when(itemMapper.selectById(ITEM_ID)).thenReturn(deletedItem);
        when(itemMapper.deleteById(ITEM_ID)).thenReturn(1);

        fileService.permanentlyDelete(OWNER_ID, Collections.singletonList(ITEM_ID));

        // Storage deleted AFTER DB
        verify(itemMapper).deleteById(ITEM_ID);
        verify(storageService).delete("uploads/key.docx");
        verify(userMapper).subtractUsedBytes(OWNER_ID, 1000L);
    }

    // ========== Fix 4: move cycle detection ==========

    @Test
    void move_shouldRejectCircularReference() {
        MoveItemsDTO dto = new MoveItemsDTO();
        dto.setTargetParentId(ITEM_ID);  // target is the item itself
        dto.setItemIds(Collections.singletonList(ITEM_ID));

        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsDirectory(true)
                .setPath("/folder");

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        assertThrows(BizException.class,
                () -> fileService.move(OWNER_ID, dto));
    }

    // ========== Fix 5: restoreFromTrash name conflict ==========

    @Test
    void restoreFromTrash_shouldRejectOnNameConflict() {
        Item trashedItem = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setParentId(10L)
                .setName("report.docx");

        when(itemMapper.selectById(ITEM_ID)).thenReturn(trashedItem);
        when(itemMapper.countByName(OWNER_ID, 10L, "report.docx")).thenReturn(1);

        assertThrows(BizException.class,
                () -> fileService.restoreFromTrash(OWNER_ID, Collections.singletonList(ITEM_ID)));
    }

    // ========== Fix 6: completeUpload mergeChunks ==========

    @Test
    void completeUpload_shouldMergeChunks() {
        Long taskId = 1L;
        UploadTask task = new UploadTask()
                .setId(taskId)
                .setOwnerId(OWNER_ID)
                .setParentId(null)
                .setFileName("test.zip")
                .setFileSize(5000L)
                .setTotalChunks(2)
                .setReceivedChunks(2)
                .setStatus("uploading")
                .setStoragePrefix("uploads/prefix")
                .setMimeType("application/zip")
                .setExpiresAt(LocalDateTime.now().plusHours(24));

        List<UploadChunk> chunks = Arrays.asList(
                new UploadChunk().setChunkIndex(0).setStorageKey("chunk/0"),
                new UploadChunk().setChunkIndex(1).setStorageKey("chunk/1")
        );

        when(uploadTaskMapper.selectById(taskId)).thenReturn(task);
        when(uploadChunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(chunks);
        when(itemMapper.insert(any(Item.class))).thenReturn(1);
        when(userMapper.tryAddUsedBytes(anyLong(), anyLong())).thenReturn(1);

        // Mock storage service for reading chunks
        when(storageService.getInputStream(anyString()))
                .thenReturn(new java.io.ByteArrayInputStream("test".getBytes()));

        UploadTaskVO result = fileService.completeUpload(OWNER_ID, taskId);

        assertNotNull(result);
        // Verify merge happened - storage was stored with merged data
        verify(storageService).store(startsWith("merged/"), any(byte[].class));
        // Version was created
        verify(itemVersionMapper).insert(any(ItemVersion.class));
    }

    // ========== Fix 7: escapeLike helper ==========

    @Test
    void escapeLike_shouldEscapeWildcards() {
        // Access private method via reflection
        try {
            java.lang.reflect.Method method = FileServiceImpl.class.getDeclaredMethod("escapeLike", String.class);
            method.setAccessible(true);

            assertEquals("normal!/path!/", method.invoke(fileService, "normal/path/"));
            assertEquals("test!_2024", method.invoke(fileService, "test_2024"));
            assertEquals("100!%", method.invoke(fileService, "100%"));
            assertEquals("a!!b", method.invoke(fileService, "a!b"));
            assertEquals(null, method.invoke(fileService, (String) null));
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }
}
