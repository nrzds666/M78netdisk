package com.m78.netdisk.file;

import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.file.controller.FileController;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.service.DocumentConversionService;
import com.m78.netdisk.file.service.IFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FileController 上传流式写入测试
 * <p>
 * 验证 uploadFile 使用 InputStream 而非 byte[] 写入存储，
 * 避免大文件加载到内存导致 OOM。
 */
@ExtendWith(MockitoExtension.class)
class FileControllerStreamingTest {

    @Mock private IFileService fileService;
    @Mock private StorageService storageService;
    @Mock private DocumentConversionService documentConversionService;
    @Mock private MultipartFile mockFile;

    private FileController controller;

    @BeforeEach
    void setUp() {
        controller = new FileController(fileService, storageService, documentConversionService);
    }

    @Test
    void uploadFile_shouldUseInputStreamInsteadOfByteArray() throws Exception {
        // Arrange
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn("test.txt");
        when(mockFile.getSize()).thenReturn(100L);
        when(mockFile.getContentType()).thenReturn("text/plain");
        when(mockFile.getInputStream()).thenReturn(new ByteArrayInputStream("hello".getBytes()));

        ItemVO mockVo = new ItemVO();
        when(fileService.createFile(anyLong(), isNull(), eq("test.txt"),
                eq(100L), eq("text/plain"), anyString())).thenReturn(mockVo);

        UserContext.setUserId(1L);
        try {
            // Act
            R<ItemVO> result = controller.uploadFile(mockFile, null);

            // Assert
            assertEquals(200, result.getCode());
            assertSame(mockVo, result.getData());
            // 验证 store() 以 InputStream 方式调用，而非 byte[]
            verify(storageService).store(anyString(), any(InputStream.class));
            verify(storageService, never()).store(anyString(), any(byte[].class));
        } finally {
            UserContext.remove();
        }
    }
}
