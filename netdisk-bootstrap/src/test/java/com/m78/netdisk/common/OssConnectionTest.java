package com.m78.netdisk.common;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阿里云 OSS 真实连接测试。
 * 需要在运行前确保 Maven 依赖中包含 aliyun-sdk-oss。
 */
public class OssConnectionTest {

    static final String ENDPOINT = "https://oss-cn-beijing.aliyuncs.com";
    static final String ACCESS_KEY_ID = "LTAI5tRxr2ZBjDExX8KjkAk6";
    static final String ACCESS_KEY_SECRET = "LzZ7FOIJA1q7LkMW71DGz0P9tJiUzo";
    static final String BUCKET_NAME = "dkd-nrzds";

    @Test
    void testOssConnection() {
        OSS ossClient = null;
        try {
            System.out.println("正在连接 OSS...");
            System.out.println("  Endpoint: " + ENDPOINT);
            System.out.println("  Bucket:   " + BUCKET_NAME);

            ossClient = new OSSClientBuilder().build(ENDPOINT, ACCESS_KEY_ID, ACCESS_KEY_SECRET);

            boolean exists = ossClient.doesBucketExist(BUCKET_NAME);
            System.out.println("  Bucket 存在: " + exists);
            assertTrue(exists, "Bucket " + BUCKET_NAME + " 不存在，请检查配置");

            // 列出文件
            var objects = ossClient.listObjects(BUCKET_NAME);
            System.out.println("  文件总数: " + objects.getObjectSummaries().size());
            objects.getObjectSummaries().forEach(obj ->
                System.out.println("    - " + obj.getKey() + " (" + obj.getSize() + " bytes)"));

            System.out.println("\n✅ OSS 连接测试通过!");
        } catch (Exception e) {
            System.err.println("\n❌ OSS 连接失败: " + e.getMessage());
            throw new RuntimeException("OSS 连接测试失败", e);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}
