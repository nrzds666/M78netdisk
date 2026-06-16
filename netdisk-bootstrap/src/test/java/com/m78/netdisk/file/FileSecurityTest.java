package com.m78.netdisk.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.dto.*;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.po.ItemVersion;
import com.m78.netdisk.file.domain.po.MediaProgress;
import com.m78.netdisk.file.domain.po.UploadChunk;
import com.m78.netdisk.file.domain.po.UploadTask;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.domain.vo.MediaProgressVO;
import com.m78.netdisk.file.domain.vo.UploadTaskVO;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.file.mapper.ItemVersionMapper;
import com.m78.netdisk.file.mapper.MediaProgressMapper;
import com.m78.netdisk.file.mapper.UploadChunkMapper;
import com.m78.netdisk.file.mapper.UploadTaskMapper;
import com.m78.netdisk.file.service.impl.FileServiceImpl;
import com.m78.netdisk.user.domain.po.User;
import com.m78.netdisk.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileService 安全审查 - TDD: 先 RED 后 GREEN")
class FileSecurityTest {

    @Mock private ItemMapper itemMapper;
    @Mock private UploadTaskMapper uploadTaskMapper;
    @Mock private UploadChunkMapper uploadChunkMapper;
    @Mock private ItemVersionMapper itemVersionMapper;
    @Mock private UserMapper userMapper;
    @Mock private StorageService storageService;
    @Mock private MediaProgressMapper mediaProgressMapper;

    @InjectMocks
    private FileServiceImpl fileService;

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long ITEM_ID = 100L;

    // ========================================================================
    // 安全审查 1: initUpload 文件名校验 (当前缺失)
    // 问题: initUpload 没有校验文件名中的非法字符 (/ \ .. \0)
    // 这些字符会在 completeUpload -> mergeChunks 中用于构造存储路径
    // ========================================================================
    @Nested
    @DisplayName("1. initUpload 文件名安全校验")
    class InitUploadFilenameValidation {

        private InitUploadDTO makeDto(String fileName) {
            InitUploadDTO dto = new InitUploadDTO();
            dto.setFileName(fileName);
            dto.setFileSize(1024L);
            dto.setParentId(0L);
            dto.setMimeType("text/plain");
            dto.setChunkSize(5242880);
            return dto;
        }

        @Test
        @DisplayName("RED: initUpload 应拒绝含 '/' 的文件名")
        void initUpload_shouldRejectFilenameWithSlash() {
            InitUploadDTO dto = makeDto("folder/file.txt");
            assertThrows(BizException.class, () -> fileService.initUpload(OWNER_ID, dto));
        }

        @Test
        @DisplayName("RED: initUpload 应拒绝含 '\\\\' 的文件名")
        void initUpload_shouldRejectFilenameWithBackslash() {
            InitUploadDTO dto = makeDto("folder\\file.txt");
            assertThrows(BizException.class, () -> fileService.initUpload(OWNER_ID, dto));
        }

        @Test
        @DisplayName("RED: initUpload 应拒绝含 '..' 的文件名 (路径穿越)")
        void initUpload_shouldRejectFilenameWithPathTraversal() {
            InitUploadDTO dto = makeDto("../../../etc/passwd");
            assertThrows(BizException.class, () -> fileService.initUpload(OWNER_ID, dto));
        }

        @Test
        @DisplayName("RED: initUpload 应拒绝含 '\\\\0' 的文件名")
        void initUpload_shouldRejectFilenameWithNullByte() {
            InitUploadDTO dto = makeDto("file\0.txt");
            assertThrows(BizException.class, () -> fileService.initUpload(OWNER_ID, dto));
        }

        @Test
        @DisplayName("RED: initUpload 应拒绝文件名为空")
        void initUpload_shouldRejectBlankFilename() {
            InitUploadDTO dto = makeDto("   ");
            assertThrows(BizException.class, () -> fileService.initUpload(OWNER_ID, dto));
        }
    }

    // ========================================================================
    // 安全审查 2: 文件大小校验
    // ========================================================================
    @Nested
    @DisplayName("2. initUpload 文件大小校验")
    class InitUploadFileSizeValidation {

        @Test
        @DisplayName("initUpload 应拒绝文件大小为 null")
        void initUpload_shouldRejectNullFileSize() {
            InitUploadDTO dto = new InitUploadDTO();
            dto.setFileName("test.txt");
            dto.setFileSize(null);
            dto.setParentId(0L);

            assertThrows(BizException.class, () -> fileService.initUpload(OWNER_ID, dto));
        }

        @Test
        @DisplayName("initUpload 应拒绝文件大小 <= 0")
        void initUpload_shouldRejectNonPositiveFileSize() {
            InitUploadDTO dto = new InitUploadDTO();
            dto.setFileName("test.txt");
            dto.setFileSize(0L);
            dto.setParentId(0L);

            assertThrows(BizException.class, () -> fileService.initUpload(OWNER_ID, dto));
        }
    }

    // ========================================================================
    // 安全审查 3: 越权访问 - 下载
    // ========================================================================
    @Nested
    @DisplayName("3. 下载授权校验")
    class DownloadAuthorization {

        @Test
        @DisplayName("getDownloadInfo 应拒绝其他用户的文件")
        void getDownloadInfo_shouldRejectOtherUsersFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OTHER_USER_ID)
                    .setName("secret.docx").setIsDirectory(false)
                    .setIsDeleted(false);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class, () -> fileService.getDownloadInfo(OWNER_ID, ITEM_ID));
        }

        @Test
        @DisplayName("getDownloadInfo 应拒绝已删除的文件")
        void getDownloadInfo_shouldRejectDeletedFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setName("deleted.docx").setIsDirectory(false)
                    .setIsDeleted(true);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class, () -> fileService.getDownloadInfo(OWNER_ID, ITEM_ID));
        }

        @Test
        @DisplayName("getDownloadInfo 应拒绝文件夹")
        void getDownloadInfo_shouldRejectDirectory() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setName("myfolder").setIsDirectory(true)
                    .setIsDeleted(false);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class, () -> fileService.getDownloadInfo(OWNER_ID, ITEM_ID));
        }

        @Test
        @DisplayName("getPreviewInfo 应拒绝其他用户的文件")
        void getPreviewInfo_shouldRejectOtherUsersFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OTHER_USER_ID)
                    .setName("photo.jpg").setIsDirectory(false);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class, () -> fileService.getPreviewInfo(OWNER_ID, ITEM_ID));
        }
    }

    // ========================================================================
    // 安全审查 4: 越权访问 - 回收站/删除
    // ========================================================================
    @Nested
    @DisplayName("4. 回收站操作授权校验")
    class TrashAuthorization {

        @Test
        @DisplayName("deleteToTrash 应跳过其他用户的文件")
        void deleteToTrash_shouldSkipOtherUsersFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OTHER_USER_ID)
                    .setName("notmine.docx").setIsDirectory(false);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            fileService.deleteToTrash(OWNER_ID, Collections.singletonList(ITEM_ID));

            verify(itemMapper, never()).softDelete(anyLong(), anyLong());
        }

        @Test
        @DisplayName("restoreFromTrash 应跳过其他用户的文件")
        void restoreFromTrash_shouldSkipOtherUsersFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OTHER_USER_ID)
                    .setName("notmine.docx");

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            fileService.restoreFromTrash(OWNER_ID, Collections.singletonList(ITEM_ID));

            verify(itemMapper, never()).restore(anyLong(), anyLong());
        }

        @Test
        @DisplayName("permanentlyDelete 应跳过其他用户的文件")
        void permanentlyDelete_shouldSkipOtherUsersFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OTHER_USER_ID)
                    .setName("notmine.docx").setIsDirectory(false);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            fileService.permanentlyDelete(OWNER_ID, Collections.singletonList(ITEM_ID));

            verify(itemMapper, never()).deleteById(anyLong());
            verify(storageService, never()).delete(anyString());
        }
    }

    // ========================================================================
    // 安全审查 5: 分片上传安全
    // ========================================================================
    @Nested
    @DisplayName("5. 分片上传安全校验")
    class ChunkUploadSecurity {

        @Test
        @DisplayName("confirmChunk 应拒绝非法 chunkIndex (< 0)")
        void confirmChunk_shouldRejectInvalidChunkIndex() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OWNER_ID)
                    .setStatus("uploading").setTotalChunks(5);

            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class,
                    () -> fileService.confirmChunk(OWNER_ID, 1L, -1, "key", "etag", 1024));
        }

        @Test
        @DisplayName("confirmChunk 应拒绝超出范围的 chunkIndex")
        void confirmChunk_shouldRejectOutOfRangeChunkIndex() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OWNER_ID)
                    .setStatus("uploading").setTotalChunks(5);

            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class,
                    () -> fileService.confirmChunk(OWNER_ID, 1L, 10, "key", "etag", 1024));
        }

        @Test
        @DisplayName("confirmChunk 应拒绝不属于当前用户的任务")
        void confirmChunk_shouldRejectOtherUsersTask() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OTHER_USER_ID)
                    .setStatus("uploading").setTotalChunks(5);

            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class,
                    () -> fileService.confirmChunk(OWNER_ID, 1L, 1, "key", "etag", 1024));
        }

        @Test
        @DisplayName("confirmChunk 应拒绝状态异常的任务 (completed)")
        void confirmChunk_shouldRejectCompletedTask() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OWNER_ID)
                    .setStatus("completed").setTotalChunks(5);

            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class,
                    () -> fileService.confirmChunk(OWNER_ID, 1L, 1, "key", "etag", 1024));
        }

        @Test
        @DisplayName("confirmChunk 应拒绝状态异常的任务 (canceled)")
        void confirmChunk_shouldRejectCanceledTask() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OWNER_ID)
                    .setStatus("canceled").setTotalChunks(5);

            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class,
                    () -> fileService.confirmChunk(OWNER_ID, 1L, 1, "key", "etag", 1024));
        }
    }

    // ========================================================================
    // 安全审查 6: completeUpload 完整性校验
    // ========================================================================
    @Nested
    @DisplayName("6. completeUpload 安全校验")
    class CompleteUploadSecurity {

        @Test
        @DisplayName("completeUpload 应拒绝不属于当前用户的任务")
        void completeUpload_shouldRejectOtherUsersTask() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OTHER_USER_ID);
            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class,
                    () -> fileService.completeUpload(OWNER_ID, 1L));
        }

        @Test
        @DisplayName("completeUpload 应在分片未全部上传时拒绝")
        void completeUpload_shouldRejectIncompleteChunks() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OWNER_ID)
                    .setTotalChunks(5).setReceivedChunks(3).setStatus("uploading");
            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class,
                    () -> fileService.completeUpload(OWNER_ID, 1L));
        }
    }

    // ========================================================================
    // 安全审查 7: 回收站永久删除只能对已删除文件
    // ========================================================================
    @Nested
    @DisplayName("7. 永久删除校验")
    class PermanentDeleteValidation {

        @Test
        @DisplayName("permanentlyDelete 应拒绝删除不在回收站的文件")
        void permanentlyDelete_shouldRejectActiveItem() {
            // isDeleted is null (active item)
            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setName("active.docx").setIsDirectory(false)
                    .setIsDeleted(null);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class,
                    () -> fileService.permanentlyDelete(OWNER_ID, Collections.singletonList(ITEM_ID)));
        }

        @Test
        @DisplayName("permanentlyDelete 应拒绝 isDeleted=false 的文件")
        void permanentlyDelete_shouldRejectNonDeletedItem() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setName("active.docx").setIsDirectory(false)
                    .setIsDeleted(false);

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class,
                    () -> fileService.permanentlyDelete(OWNER_ID, Collections.singletonList(ITEM_ID)));
        }
    }

    // ========================================================================
    // 安全审查 8: createFile / createFolder 文件名校验
    // ========================================================================
    @Nested
    @DisplayName("8. createFile/createFolder 文件名安全")
    class CreateFileValidation {

        @Test
        @DisplayName("createFile 应拒绝含 '/' 的文件名")
        void createFile_shouldRejectNameWithSlash() {
            assertThrows(BizException.class,
                    () -> fileService.createFile(OWNER_ID, null, "folder/file.txt", 100L, "text/plain", "key"));
        }

        @Test
        @DisplayName("createFile 应拒绝含 '..' 的文件名")
        void createFile_shouldRejectNameWithDotDot() {
            assertThrows(BizException.class,
                    () -> fileService.createFile(OWNER_ID, null, "..\\etc", 100L, "text/plain", "key"));
        }

        @Test
        @DisplayName("[RED] createFile 应接受含内嵌 .. 的文件名（如 2024..2025.pdf）")
        void createFile_shouldAcceptNameContainingDotDot() {
            when(itemMapper.countByName(anyLong(), isNull(), eq("2024..2025.pdf"))).thenReturn(0);
            when(userMapper.tryAddUsedBytes(anyLong(), anyLong())).thenReturn(1);

            assertDoesNotThrow(() ->
                    fileService.createFile(OWNER_ID, null, "2024..2025.pdf", 100L, "text/plain", "key"));
        }

        @Test
        @DisplayName("createFile 应拒绝空文件名")
        void createFile_shouldRejectEmptyName() {
            assertThrows(BizException.class,
                    () -> fileService.createFile(OWNER_ID, null, "  ", 100L, "text/plain", "key"));
        }

        @Test
        @DisplayName("createFile 应拒绝含 '\\0' 的文件名")
        void createFile_shouldRejectNameWithNullByte() {
            assertThrows(BizException.class,
                    () -> fileService.createFile(OWNER_ID, null, "file\0.txt", 100L, "text/plain", "key"));
        }
    }

    // ========================================================================
    // 安全审查 9: 配额检查
    // ========================================================================
    @Nested
    @DisplayName("9. 存储配额校验")
    class QuotaCheck {

        @Test
        @DisplayName("initUpload 应在配额不足时拒绝")
        void initUpload_shouldRejectWhenQuotaExceeded() {
            User user = new User().setId(OWNER_ID).setUsedBytes(9000L).setQuotaBytes(10000L);
            when(userMapper.selectById(OWNER_ID)).thenReturn(user);

            InitUploadDTO dto = new InitUploadDTO();
            dto.setFileName("bigfile.bin");
            dto.setFileSize(2000L); // would exceed 10000 - 9000 = 1000
            dto.setParentId(0L);

            assertThrows(BizException.class, () -> fileService.initUpload(OWNER_ID, dto));
        }

        @Test
        @DisplayName("createFile 应在配额不足时回滚")
        void createFile_shouldRollbackWhenQuotaExceeded() {
            when(itemMapper.countByName(anyLong(), any(), anyString())).thenReturn(0);
            doAnswer(inv -> {
                Item item = inv.getArgument(0);
                item.setId(12345L);
                return 1;
            }).when(itemMapper).insert(any(Item.class));
            doAnswer(inv -> {
                ItemVersion v = inv.getArgument(0);
                v.setId(54321L);
                return 1;
            }).when(itemVersionMapper).insert(any(ItemVersion.class));
            // tryAddUsedBytes returns 0 = quota exceeded
            when(userMapper.tryAddUsedBytes(OWNER_ID, 10000L)).thenReturn(0);

            assertThrows(BizException.class,
                    () -> fileService.createFile(OWNER_ID, null, "test.txt", 10000L, "text/plain", "key"));

            // Verify rollback: item and version records should be deleted
            verify(itemMapper).deleteById(anyLong());
            verify(itemVersionMapper).deleteById(anyLong());
        }
    }

    // ========================================================================
    // 安全审查 10: 分页大小封顶
    // ========================================================================
    @Nested
    @DisplayName("10. 分页 size 封顶")
    class PaginationCap {

        @Test
        @DisplayName("listItems 应将 size 限制在 100 以内")
        void listItems_shouldCapSize() {
            Page<Item> pageArg = new Page<>(1, 100);
            when(itemMapper.selectRootItems(any(), eq(OWNER_ID))).thenReturn(new Page<>(1, 100));

            fileService.listItems(OWNER_ID, null, 1, 500);

            ArgumentCaptor<Page<Item>> pageCaptor = ArgumentCaptor.forClass(Page.class);
            verify(itemMapper).selectRootItems(pageCaptor.capture(), eq(OWNER_ID));
            // The captured page should have size 100, not 500
        }

        @Test
        @DisplayName("listTrash 应将 size 限制在 100 以内")
        void listTrash_shouldCapSize() {
            Page<Item> pageArg = new Page<>(1, 100);
            when(itemMapper.selectTrash(any(), eq(OWNER_ID))).thenReturn(new Page<>(1, 100));

            fileService.listTrash(OWNER_ID, 1, 500);

            ArgumentCaptor<Page<Item>> pageCaptor = ArgumentCaptor.forClass(Page.class);
            verify(itemMapper).selectTrash(pageCaptor.capture(), eq(OWNER_ID));
            assertEquals(100, pageCaptor.getValue().getSize());
        }
    }

    // ========================================================================
    // 安全审查 11: 媒体进度 - 非媒体文件
    // ========================================================================
    @Nested
    @DisplayName("11. 媒体进度仅允许媒体文件")
    class MediaProgressValidation {

        @Test
        @DisplayName("getProgress 应拒绝非媒体文件")
        void getProgress_shouldRejectNonMediaFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setMimeType("application/pdf").setIsFromShare(false);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class,
                    () -> fileService.getProgress(OWNER_ID, ITEM_ID));
        }

        @Test
        @DisplayName("getProgress 应接受视频文件")
        void getProgress_shouldAcceptVideoFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setMimeType("video/mp4").setIsFromShare(false);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            when(mediaProgressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            MediaProgressVO result = fileService.getProgress(OWNER_ID, ITEM_ID);

            assertNotNull(result);
            assertEquals(ITEM_ID, result.getItemId());
            assertEquals(0, result.getProgressSeconds());
        }

        @Test
        @DisplayName("getProgress 应接受音频文件")
        void getProgress_shouldAcceptAudioFile() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setMimeType("audio/mpeg").setIsFromShare(false);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
            when(mediaProgressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            MediaProgressVO result = fileService.getProgress(OWNER_ID, ITEM_ID);
            assertNotNull(result);
        }

        @Test
        @DisplayName("getProgress 应拒绝 null mimeType")
        void getProgress_shouldRejectNullMimeType() {
            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setMimeType(null).setIsFromShare(false);
            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class,
                    () -> fileService.getProgress(OWNER_ID, ITEM_ID));
        }
    }

    // ========================================================================
    // 安全审查 12: 移动操作 - 循环引用检测
    // ========================================================================
    @Nested
    @DisplayName("12. 移动操作循环引用检测")
    class MoveCycleDetection {

        @Test
        @DisplayName("move 应拒绝将文件夹移入自身")
        void move_shouldRejectMoveIntoSelf() {
            MoveItemsDTO dto = new MoveItemsDTO();
            dto.setTargetParentId(ITEM_ID);
            dto.setItemIds(Collections.singletonList(ITEM_ID));

            Item item = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setIsDirectory(true).setName("myfolder").setPath("/myfolder");

            when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

            assertThrows(BizException.class, () -> fileService.move(OWNER_ID, dto));
        }

        @Test
        @DisplayName("move 应拒绝将父文件夹移入子文件夹")
        void move_shouldRejectParentIntoChild() {
            Long parentId = 10L;
            Long childId = 11L;

            MoveItemsDTO dto = new MoveItemsDTO();
            dto.setTargetParentId(childId); // target is child
            dto.setItemIds(Collections.singletonList(parentId)); // moving parent

            Item parent = new Item().setId(parentId).setOwnerId(OWNER_ID)
                    .setParentId(null).setIsDirectory(true).setName("parent").setPath("/parent");

            Item child = new Item().setId(childId).setOwnerId(OWNER_ID)
                    .setParentId(parentId).setIsDirectory(true).setName("child").setPath("/parent/child");

            when(itemMapper.selectById(parentId)).thenReturn(parent);
            when(itemMapper.selectById(childId)).thenReturn(child);

            // isDescendant query: selectById(childId) returns the item that has parentId = parentId
            when(itemMapper.selectById(parentId)).thenReturn(parent);
            // isDescendant: check if childId is descendant of parentId
            // itemMapper.selectById(childId) -> child (parentId = parentId)
            // Then currentId = parentId, which equals parentId -> true

            assertThrows(BizException.class, () -> fileService.move(OWNER_ID, dto));
        }

        @Test
        @DisplayName("move 应拒绝目标目录不存在的情况")
        void move_shouldRejectNonExistentTarget() {
            MoveItemsDTO dto = new MoveItemsDTO();
            dto.setTargetParentId(9999L);
            dto.setItemIds(Collections.singletonList(ITEM_ID));

            when(itemMapper.selectById(9999L)).thenReturn(null);

            assertThrows(BizException.class, () -> fileService.move(OWNER_ID, dto));
        }
    }

    // ========================================================================
    // 安全审查 13: getFolderZip 授权
    // ========================================================================
    @Nested
    @DisplayName("13. 文件夹下载授权")
    class FolderZipAuthorization {

        @Test
        @DisplayName("getFolderZip 应拒绝其他用户的文件夹")
        void getFolderZip_shouldRejectOtherUsersFolder() {
            Item folder = new Item().setId(ITEM_ID).setOwnerId(OTHER_USER_ID)
                    .setIsDirectory(true).setName("secret");

            when(itemMapper.selectById(ITEM_ID)).thenReturn(folder);

            assertThrows(BizException.class, () -> fileService.getFolderZip(OWNER_ID, ITEM_ID));
        }

        @Test
        @DisplayName("getFolderZip 应拒绝非文件夹")
        void getFolderZip_shouldRejectFile() {
            Item file = new Item().setId(ITEM_ID).setOwnerId(OWNER_ID)
                    .setIsDirectory(false).setName("file.txt");

            when(itemMapper.selectById(ITEM_ID)).thenReturn(file);

            assertThrows(BizException.class, () -> fileService.getFolderZip(OWNER_ID, ITEM_ID));
        }
    }

    // ========================================================================
    // 安全审查 14: cancelUpload 授权
    // ========================================================================
    @Nested
    @DisplayName("14. 取消上传授权")
    class CancelUploadAuthorization {

        @Test
        @DisplayName("cancelUpload 应拒绝其他用户的任务")
        void cancelUpload_shouldRejectOtherUsersTask() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OTHER_USER_ID);
            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class, () -> fileService.cancelUpload(OWNER_ID, 1L));
        }

        @Test
        @DisplayName("cancelUpload 应成功取消并清理分片")
        void cancelUpload_shouldCleanupChunks() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OWNER_ID).setStatus("uploading");
            List<UploadChunk> chunks = Arrays.asList(
                    new UploadChunk().setTaskId(1L).setChunkIndex(0).setStorageKey("chunk/0"),
                    new UploadChunk().setTaskId(1L).setChunkIndex(1).setStorageKey("chunk/1")
            );

            when(uploadTaskMapper.selectById(1L)).thenReturn(task);
            when(uploadChunkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(chunks);

            fileService.cancelUpload(OWNER_ID, 1L);

            verify(storageService, times(2)).delete(anyString());
            verify(uploadChunkMapper).delete(any(LambdaQueryWrapper.class));
            verify(uploadTaskMapper).updateById(any(UploadTask.class));
        }
    }

    // ========================================================================
    // 安全审查 15: 回收站递归操作
    // ========================================================================
    @Nested
    @DisplayName("15. 回收站递归操作")
    class TrashRecursiveOperation {

        @Test
        @DisplayName("deleteToTrash 应递归删除文件夹的子文件")
        void deleteToTrash_shouldRecurseIntoFolders() {
            Long folderId = 10L;
            Long childId = 11L;

            Item folder = new Item().setId(folderId).setOwnerId(OWNER_ID)
                    .setIsDirectory(true).setName("myfolder");
            Item child = new Item().setId(childId).setOwnerId(OWNER_ID)
                    .setParentId(folderId).setIsDirectory(false).setName("file.txt");

            when(itemMapper.selectById(folderId)).thenReturn(folder);
            when(itemMapper.softDelete(folderId, OWNER_ID)).thenReturn(1);
            // For recursive call: select children of folder
            when(itemMapper.selectList(argThat(wrapper -> {
                // Check it's the children query
                return true; // accept any wrapper, we control the return
            }))).thenReturn(Collections.singletonList(child));

            // For the inner recursive deleteToTrash call
            when(itemMapper.selectById(childId)).thenReturn(child);
            when(itemMapper.softDelete(childId, OWNER_ID)).thenReturn(1);

            fileService.deleteToTrash(OWNER_ID, Collections.singletonList(folderId));

            verify(itemMapper).softDelete(folderId, OWNER_ID);
            verify(itemMapper).softDelete(childId, OWNER_ID);
        }

        @Test
        @DisplayName("restoreFromTrash 应递归恢复文件夹的子文件")
        void restoreFromTrash_shouldRecurseIntoFolders() {
            Long folderId = 10L;
            Long childId = 11L;

            Item folder = new Item().setId(folderId).setOwnerId(OWNER_ID)
                    .setParentId(null).setName("myfolder")
                    .setIsDirectory(true);
            Item child = new Item().setId(childId).setOwnerId(OWNER_ID)
                    .setParentId(folderId).setIsDirectory(false).setName("file.txt");

            when(itemMapper.selectById(folderId)).thenReturn(folder);
            when(itemMapper.countByName(OWNER_ID, null, "myfolder")).thenReturn(0);
            when(itemMapper.restore(folderId, OWNER_ID)).thenReturn(1);

            // Children query for recursive restore
            when(itemMapper.selectList(argThat(wrapper -> {
                return true;
            }))).thenReturn(Collections.singletonList(child));

            // Inner recursive restore
            when(itemMapper.selectById(childId)).thenReturn(child);
            when(itemMapper.countByName(OWNER_ID, folderId, "file.txt")).thenReturn(0);
            when(itemMapper.restore(childId, OWNER_ID)).thenReturn(1);

            fileService.restoreFromTrash(OWNER_ID, Collections.singletonList(folderId));

            verify(itemMapper).restore(folderId, OWNER_ID);
            verify(itemMapper).restore(childId, OWNER_ID);
        }
    }

    // ========================================================================
    // 安全审查 16: getUploadStatus 授权
    // ========================================================================
    @Nested
    @DisplayName("16. 上传状态查询授权")
    class UploadStatusAuthorization {

        @Test
        @DisplayName("getUploadStatus 应拒绝其他用户的任务")
        void getUploadStatus_shouldRejectOtherUsersTask() {
            UploadTask task = new UploadTask().setId(1L).setOwnerId(OTHER_USER_ID);
            when(uploadTaskMapper.selectById(1L)).thenReturn(task);

            assertThrows(BizException.class, () -> fileService.getUploadStatus(OWNER_ID, 1L));
        }
    }
}
