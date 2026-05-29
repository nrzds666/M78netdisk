package com.m78.netdisk.common;

import com.aliyun.oss.OSS;
import com.m78.netdisk.common.storage.OssStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RED test: OssStorageService should validate null/blank relativeKey.
 */
class OssStorageServiceTest {

    private OssStorageService service;
    private OSS mockOssClient;

    @BeforeEach
    void setUp() throws Exception {
        service = new OssStorageService();
        mockOssClient = Mockito.mock(OSS.class);

        // Use reflection to inject the mock OSS client and bucket name
        java.lang.reflect.Field ossField = OssStorageService.class.getDeclaredField("ossClient");
        ossField.setAccessible(true);
        ossField.set(service, mockOssClient);

        java.lang.reflect.Field bucketField = OssStorageService.class.getDeclaredField("bucketName");
        bucketField.setAccessible(true);
        bucketField.set(service, "test-bucket");
    }

    @Test
    void store_withNullByteData_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.store(null, new byte[0]));
    }

    @Test
    void store_withBlankByteData_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.store("  ", new byte[0]));
    }

    @Test
    void store_withNullStream_shouldThrowException() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> service.store(null, stream));
    }

    @Test
    void store_withBlankStream_shouldThrowException() {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> service.store("  ", stream));
    }

    @Test
    void getInputStream_withNullKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getInputStream(null));
    }

    @Test
    void getInputStream_withBlankKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getInputStream(""));
    }

    @Test
    void getContentLength_withNullKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getContentLength(null));
    }

    @Test
    void getContentLength_withBlankKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getContentLength(""));
    }
}
