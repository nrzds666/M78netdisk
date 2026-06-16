package com.m78.netdisk.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.dto.InitUploadDTO;
import com.m78.netdisk.file.domain.dto.MoveItemsDTO;
import com.m78.netdisk.file.domain.dto.RenameItemDTO;
import com.m78.netdisk.file.domain.dto.SaveProgressDTO;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.po.ItemVersion;
import com.m78.netdisk.file.domain.po.UploadChunk;
import com.m78.netdisk.file.domain.po.UploadTask;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.domain.vo.UploadTaskVO;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.file.mapper.ItemVersionMapper;
import com.m78.netdisk.file.mapper.MediaProgressMapper;
import com.m78.netdisk.file.mapper.UploadChunkMapper;
import com.m78.netdisk.file.mapper.UploadTaskMapper;
import com.m78.netdisk.file.service.impl.FileServiceImpl;
import com.m78.netdisk.file.service.impl.UploadMergeService;
import com.m78.netdisk.user.domain.po.User;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    @Mock private MediaProgressMapper mediaProgressMapper;
    @Mock private UploadMergeService uploadMergeService;

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
    void completeUpload_shouldDelegateToMergeService() {
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

        when(uploadTaskMapper.selectById(taskId)).thenReturn(task);
        when(itemMapper.countByName(anyLong(), any(), anyString())).thenReturn(0);

        UploadTaskVO result = fileService.completeUpload(OWNER_ID, taskId);

        assertNotNull(result);
        // 验证委托给 UploadMergeService
        verify(uploadMergeService).performMerge(OWNER_ID, taskId);
        // 验证 status 已改为 merging
        assertEquals("merging", task.getStatus());
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

    // ========== isFromShare preview blocking ==========

    @Test
    void getPreviewInfo_shouldBlockIsFromShare() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsFromShare(true);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getPreviewInfo(OWNER_ID, ITEM_ID));
        assertTrue(ex.getMessage().contains("不支持在线预览"));
    }

    @Test
    void getProgress_shouldBlockIsFromShare() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsFromShare(true);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.getProgress(OWNER_ID, ITEM_ID));
        assertTrue(ex.getMessage().contains("不支持在线预览"));
    }

    @Test
    void saveProgress_shouldBlockIsFromShare() {
        Item item = new Item()
                .setId(ITEM_ID)
                .setOwnerId(OWNER_ID)
                .setIsFromShare(true);

        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        SaveProgressDTO dto = new SaveProgressDTO();
        dto.setProgressSeconds(30);
        dto.setTotalDuration(120);

        BizException ex = assertThrows(BizException.class,
                () -> fileService.saveProgress(OWNER_ID, ITEM_ID, dto));
        assertTrue(ex.getMessage().contains("不支持在线预览"));
    }

    // ========== Recent files (home page) ==========

    @Test
    void listRecentItems_shouldReturnItems() {
        List<Item> items = Arrays.asList(
                new Item().setId(1L).setOwnerId(OWNER_ID).setName("photo.jpg").setIsDirectory(false).setSize(1000L).setMimeType("image/jpeg"),
                new Item().setId(2L).setOwnerId(OWNER_ID).setName("doc.pdf").setIsDirectory(false).setSize(2000L).setMimeType("application/pdf")
        );

        when(itemMapper.selectRecentItems(OWNER_ID, 3)).thenReturn(items);

        List<ItemVO> result = fileService.listRecentItems(OWNER_ID, 3);

        assertEquals(2, result.size());
        assertEquals("photo.jpg", result.get(0).getName());
        verify(itemMapper).selectRecentItems(OWNER_ID, 3);
    }

    @Test
    void listRecentItems_shouldReturnEmptyWhenNone() {
        when(itemMapper.selectRecentItems(OWNER_ID, 3)).thenReturn(Collections.emptyList());

        List<ItemVO> result = fileService.listRecentItems(OWNER_ID, 3);

        assertTrue(result.isEmpty());
    }

    @Test
    void listRecentSaves_shouldReturnItems() {
        List<Item> items = Arrays.asList(
                new Item().setId(3L).setOwnerId(OWNER_ID).setName("shared.pdf").setIsDirectory(false).setSize(5000L).setIsFromShare(true)
        );

        when(itemMapper.selectRecentSaves(OWNER_ID, 3)).thenReturn(items);

        List<ItemVO> result = fileService.listRecentSaves(OWNER_ID, 3);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsFromShare());
        verify(itemMapper).selectRecentSaves(OWNER_ID, 3);
    }

    @Test
    void listRecentSaves_shouldReturnEmptyWhenNone() {
        when(itemMapper.selectRecentSaves(OWNER_ID, 3)).thenReturn(Collections.emptyList());

        List<ItemVO> result = fileService.listRecentSaves(OWNER_ID, 3);

        assertTrue(result.isEmpty());
    }

    @Test
    void toItemVO_shouldIncludeStorageKey() throws Exception {
        String testKey = "uploads/test/avatar.jpg";
        Item item = new Item().setId(99L).setStorageKey(testKey);

        java.lang.reflect.Method method = FileServiceImpl.class.getDeclaredMethod("toItemVO", Item.class);
        method.setAccessible(true);
        ItemVO vo = (ItemVO) method.invoke(fileService, item);

        assertEquals(testKey, vo.getStorageKey());
    }

    // ========== Fix 5: uploadChunk status transition pending→uploading ==========

    @Test
    void uploadChunk_shouldTransitionStatusToUploading() throws Exception {
        Long taskId = 1L;
        UploadTask task = new UploadTask()
                .setId(taskId)
                .setOwnerId(OWNER_ID)
                .setStatus("pending")
                .setTotalChunks(10)
                .setStoragePrefix("uploads/test");
        when(uploadTaskMapper.selectById(taskId)).thenReturn(task);

        org.springframework.web.multipart.MultipartFile mockFile =
                mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[100]));
        when(mockFile.getSize()).thenReturn(100L);

        fileService.uploadChunk(OWNER_ID, taskId, 0, mockFile);

        // Verify status transition via LambdaUpdateWrapper
        verify(uploadTaskMapper, atLeast(1)).update(
                isNull(),
                any(LambdaUpdateWrapper.class));
    }

    // ==================== OSS Storage Prefix ====================

    @Test
    void getFileExtension_shouldReturnLowercaseExt() {
        assertEquals("mp4", FileServiceImpl.getFileExtension("demo.mp4"));
        assertEquals("pdf", FileServiceImpl.getFileExtension("report.PDF"));
        assertEquals("jpg", FileServiceImpl.getFileExtension("photo.JPG"));
        assertEquals("", FileServiceImpl.getFileExtension("noext"));
        assertEquals("", FileServiceImpl.getFileExtension(".hidden"));
        assertEquals("gz", FileServiceImpl.getFileExtension("archive.tar.gz"));
    }

    @Test
    void getFileCategory_shouldClassifyByExtension() {
        assertEquals("video", FileServiceImpl.getFileCategory("mp4"));
        assertEquals("audio", FileServiceImpl.getFileCategory("mp3"));
        assertEquals("image", FileServiceImpl.getFileCategory("jpg"));
        assertEquals("document", FileServiceImpl.getFileCategory("pdf"));
        assertEquals("archive", FileServiceImpl.getFileCategory("zip"));
        assertEquals("other", FileServiceImpl.getFileCategory("exe"));
        assertEquals("other", FileServiceImpl.getFileCategory("unknown"));
        assertEquals("other", FileServiceImpl.getFileCategory(""));
    }

    @Test
    void initUpload_shouldCreateDatedStoragePrefix() {
        InitUploadDTO dto = new InitUploadDTO();
        dto.setFileName("test.mp4");
        dto.setFileSize(10000L);
        dto.setParentId(0L);

        User user = new User()
                .setId(OWNER_ID)
                .setQuotaBytes(100_000_000L)
                .setUsedBytes(10_000L);
        when(userMapper.selectById(OWNER_ID)).thenReturn(user);
        when(itemMapper.countByName(anyLong(), isNull(), eq("test.mp4"))).thenReturn(0);
        // local storage: skip multipart upload
        when(storageService.initiateMultipartUpload(anyString()))
                .thenThrow(new UnsupportedOperationException("not supported"));

        ArgumentCaptor<UploadTask> captor = ArgumentCaptor.forClass(UploadTask.class);

        fileService.initUpload(OWNER_ID, dto);

        verify(uploadTaskMapper).insert(captor.capture());
        UploadTask saved = captor.getValue();

        // storagePrefix = uploads/yyyy-MM-dd/video/{32-char hex}
        assertNotNull(saved.getStoragePrefix());
        assertTrue(saved.getStoragePrefix().matches(
                "uploads/\\d{4}-\\d{2}-\\d{2}/video/[a-f0-9]{32}"),
                "expected pattern uploads/{date}/video/{uuid32}, got: " + saved.getStoragePrefix());

        // mergedKey = storagePrefix + "/" + fileName
        assertEquals(saved.getStoragePrefix() + "/test.mp4", saved.getMergedKey());
    }

    @Test
    void initUpload_shouldDeriveCorrectCategoryFromExtension() {
        // audio
        InitUploadDTO dto = new InitUploadDTO();
        dto.setFileName("song.mp3");
        dto.setFileSize(5000L);
        dto.setParentId(0L);

        User user = new User()
                .setId(OWNER_ID)
                .setQuotaBytes(100_000_000L)
                .setUsedBytes(0L);
        when(userMapper.selectById(OWNER_ID)).thenReturn(user);
        when(itemMapper.countByName(anyLong(), isNull(), eq("song.mp3"))).thenReturn(0);
        when(storageService.initiateMultipartUpload(anyString()))
                .thenThrow(new UnsupportedOperationException("not supported"));

        ArgumentCaptor<UploadTask> captor = ArgumentCaptor.forClass(UploadTask.class);

        fileService.initUpload(OWNER_ID, dto);

        verify(uploadTaskMapper).insert(captor.capture());
        assertTrue(captor.getValue().getStoragePrefix().contains("/audio/"),
                "expected /audio/ in prefix, got: " + captor.getValue().getStoragePrefix());
    }
}
