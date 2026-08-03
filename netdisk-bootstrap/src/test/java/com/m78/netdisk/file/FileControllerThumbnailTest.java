package com.m78.netdisk.file;

import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.file.controller.FileController;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.service.DocumentConversionService;
import com.m78.netdisk.file.service.IFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RED test: GET /api/files/thumbnail/{id} endpoint.
 */
@ExtendWith(MockitoExtension.class)
class FileControllerThumbnailTest {

    @Mock private IFileService fileService;
    @Mock private StorageService storageService;
    @Mock private DocumentConversionService documentConversionService;

    private FileController controller;

    private static final byte[] FAKE_JPEG = new byte[]{
            (byte) 0xFF, (byte) 0xD8, 0x00, 0x10, (byte) 0xFF, (byte) 0xD9};

    @BeforeEach
    void setUp() {
        controller = new FileController(fileService, storageService, documentConversionService);
    }

    @Test
    void getThumbnail_whenFileExists_shouldReturnJPEG() throws Exception {
        FileDownloadVO info = FileDownloadVO.builder()
                .storageKey("uploads/test.mp4").fileName("test.mp4")
                .mimeType("video/mp4").fileSize(1000L).build();
        when(fileService.getPreviewInfo(eq(1L), eq(42L))).thenReturn(info);
        when(storageService.getInputStream("thumbnails/42.jpg"))
                .thenReturn(new ByteArrayInputStream(FAKE_JPEG));
        UserContext.setUserId(1L);
        try {
            MockHttpServletResponse response = new MockHttpServletResponse();
            controller.getThumbnail(42L, response);

            assertEquals(200, response.getStatus());
            assertEquals("image/jpeg", response.getContentType());
            assertArrayEquals(FAKE_JPEG, response.getContentAsByteArray());
        } finally {
            UserContext.remove();
        }
    }

    @Test
    void getThumbnail_whenFileNotFound_shouldReturn404() throws Exception {
        when(fileService.getPreviewInfo(anyLong(), eq(99L)))
                .thenThrow(new com.m78.netdisk.common.exception.BizException("文件不存在"));
        UserContext.setUserId(1L);
        try {
            MockHttpServletResponse response = new MockHttpServletResponse();
            controller.getThumbnail(99L, response);

            assertEquals(404, response.getStatus());
        } finally {
            UserContext.remove();
        }
    }
}
