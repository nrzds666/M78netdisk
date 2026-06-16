package com.m78.netdisk.file;

import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.file.domain.po.UploadTask;
import com.m78.netdisk.file.mapper.UploadTaskMapper;
import com.m78.netdisk.file.service.impl.FileServiceImpl;
import com.m78.netdisk.file.service.impl.UploadMergeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UploadServicePauseTest {

    @Mock private UploadTaskMapper uploadTaskMapper;
    @Mock private UploadMergeService uploadMergeService;

    @InjectMocks
    private FileServiceImpl fileService;

    private static final Long OWNER_ID = 1L;
    private static final Long TASK_ID = 10L;

    @Test
    void pauseUpload_shouldSetStatusToPaused() {
        UploadTask task = new UploadTask()
                .setId(TASK_ID).setOwnerId(OWNER_ID)
                .setFileName("test.zip").setStatus("uploading");
        when(uploadTaskMapper.selectById(TASK_ID)).thenReturn(task);

        fileService.pauseUpload(OWNER_ID, TASK_ID);

        assertEquals("paused", task.getStatus());
        verify(uploadTaskMapper).updateById(task);
    }

    @Test
    void pauseUpload_shouldThrowWhenTaskNotFound() {
        when(uploadTaskMapper.selectById(TASK_ID)).thenReturn(null);
        assertThrows(BizException.class,
                () -> fileService.pauseUpload(OWNER_ID, TASK_ID));
    }

    @Test
    void pauseUpload_shouldThrowWhenOwnerMismatch() {
        UploadTask task = new UploadTask()
                .setId(TASK_ID).setOwnerId(999L);
        when(uploadTaskMapper.selectById(TASK_ID)).thenReturn(task);
        assertThrows(BizException.class,
                () -> fileService.pauseUpload(OWNER_ID, TASK_ID));
    }
}
