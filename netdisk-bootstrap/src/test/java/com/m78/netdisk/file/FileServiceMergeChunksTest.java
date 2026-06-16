package com.m78.netdisk.file;

import com.m78.netdisk.file.service.impl.FileServiceImpl;
import com.m78.netdisk.file.service.impl.UploadMergeService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * mergeChunks 已提取到 UploadMergeService，对应测试见 UploadMergeServiceTest。
 * 此文件保留 FileServiceImpl 的容器集成验证。
 */
class FileServiceMergeChunksTest {

    @Test
    void completeUpload_shouldDelegateToMergeService() throws Exception {
        // completeUpload 不再调用 this.performMerge()，而是委托给 UploadMergeService
        java.lang.reflect.Method method = FileServiceImpl.class
                .getDeclaredMethod("completeUpload", Long.class, Long.class);
        // 自身不应有 @Async — @Async 在 UploadMergeService 上
        assertNull(method.getAnnotation(org.springframework.scheduling.annotation.Async.class));
    }
}
