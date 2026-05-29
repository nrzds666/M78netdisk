package com.m78.netdisk.common;

import com.m78.netdisk.common.storage.LocalStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RED test: LocalStorageService null/blank relativeKey validation.
 */
class LocalStorageServiceTest extends BaseTest {

    @Autowired
    private LocalStorageService localStorageService;

    @Test
    void storeByteArray_withNullKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> localStorageService.store(null, new byte[0]));
    }

    @Test
    void storeByteArray_withBlankKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> localStorageService.store("", new byte[0]));
    }

    @Test
    void storeStream_withNullKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> localStorageService.store(null, new java.io.ByteArrayInputStream(new byte[0])));
    }

    @Test
    void storeStream_withBlankKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> localStorageService.store("", new java.io.ByteArrayInputStream(new byte[0])));
    }

    @Test
    void getInputStream_withNullKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> localStorageService.getInputStream(null));
    }

    @Test
    void getInputStream_withBlankKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> localStorageService.getInputStream(""));
    }

    @Test
    void getContentLength_withNullKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> localStorageService.getContentLength(null));
    }

    @Test
    void getContentLength_withBlankKey_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> localStorageService.getContentLength(""));
    }
}
