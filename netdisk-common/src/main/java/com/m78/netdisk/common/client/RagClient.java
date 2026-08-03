package com.m78.netdisk.common.client;

import com.m78.netdisk.common.config.RagServiceProperties;
import com.m78.netdisk.common.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
public class RagClient {

    private final RestTemplate restTemplate;
    private final StorageService storageService;
    private final RagServiceProperties properties;

    public RagClient(@Qualifier("ragRestTemplate") RestTemplate restTemplate, StorageService storageService, RagServiceProperties properties) {
        this.restTemplate = restTemplate;
        this.storageService = storageService;
        this.properties = properties;
    }

    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // 100MB

    /**
     * 从存储后端下载文件内容。
     */
    public byte[] downloadFromStorage(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        try (InputStream is = storageService.getInputStream(storageKey);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = is.read(buf)) != -1) {
                total += n;
                if (total > MAX_FILE_SIZE) {
                    log.warn("文件过大，跳过 RAG 索引: storageKey={}, size={} bytes", storageKey, total);
                    return null;
                }
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("下载文件失败: storageKey={}, error={}", storageKey, e.getMessage());
            return null;
        }
    }

    /**
     * 上传文件到 RAG 知识库进行索引。
     */
    public void indexFile(String fileName, byte[] fileBytes, Long userId) {
        if (!properties.isEnabled()) {
            log.debug("RAG 服务未启用，跳过文件索引: {}", fileName);
            return;
        }

        String url = properties.getUrl() + "/upload";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }

            public String getContentType() throws java.io.IOException {
                try {
                    String mimeType = Files.probeContentType(Paths.get(fileName));
                    if (mimeType != null) {
                        return mimeType;
                    }
                } catch (Exception e) {
                    // Fall through to default
                }
                return MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);
        body.add("user_id", String.valueOf(userId));

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            if (response != null && "success".equals(response.get("status"))) {
                log.info("文件索引到 RAG 成功: {}, chunks={}", fileName, response.get("chunks"));
            } else {
                log.warn("RAG 索引失败: {}, response={}", fileName, response);
            }
        } catch (Exception e) {
            log.error("RAG 索引异常: url={}, fileName={}, error={}", url, fileName, e.getMessage());
        }
    }

    /**
     * 查询 RAG 知识库。
     */
    public List<Map<String, Object>> query(String queryText, int topK, Long userId) {
        if (!properties.isEnabled()) {
            return Collections.emptyList();
        }

        String url = properties.getUrl() + "/query";

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("query_text", queryText);
            form.add("top_k", String.valueOf(topK));
            form.add("enable_rewrite", String.valueOf(properties.isEnableRewrite()));
            form.add("enable_hybrid", String.valueOf(properties.isEnableHybrid()));
            form.add("enable_rerank", String.valueOf(properties.isEnableRerank()));
            form.add("enable_compress", String.valueOf(properties.isEnableCompress()));
            form.add("retrieval_multiplier", String.valueOf(properties.getRetrievalMultiplier()));
            form.add("compress_context_window", String.valueOf(properties.getCompressContextWindow()));
            form.add("user_id", String.valueOf(userId));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response == null) {
                return Collections.emptyList();
            }

            Object resultsObj = response.get("results");
            if (resultsObj instanceof List) {
                return (List<Map<String, Object>>) resultsObj;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("RAG 查询异常: url={}, error={}", url, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 RAG 知识库删除文档（通过文件名查找 docId 后删除）。
     */
    public void deleteDocumentByFileName(String fileName) {
        if (!properties.isEnabled()) {
            return;
        }

        String listUrl = properties.getUrl() + "/documents";
        try {
            Map<String, Object> response = restTemplate.getForObject(listUrl, Map.class);
            if (response == null) return;

            Object docsObj = response.get("documents");
            if (!(docsObj instanceof List)) return;
            @SuppressWarnings("unchecked")
            List<?> docs = (List<?>) docsObj;

            boolean found = false;
            for (Object doc : docs) {
                if (doc instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<?, ?> docMap = (Map<?, ?>) doc;
                    String docName = String.valueOf(docMap.get("name"));
                    if (docName.equals(fileName)) {
                        String docId = String.valueOf(docMap.get("id"));
                        deleteDocument(docId);
                        found = true;
                    }
                }
            }
            if (!found) {
                log.debug("RAG 中未找到匹配文档: {}", fileName);
            }
        } catch (Exception e) {
            log.warn("RAG 按文件名删除文档失败: fileName={}, error={}", fileName, e.getMessage());
        }
    }

    /**
     * 从 RAG 知识库删除文档（通过 docId）。
     */
    public void deleteDocument(String docId) {
        if (!properties.isEnabled()) {
            return;
        }

        String url = properties.getUrl() + "/documents/" + docId;

        try {
            restTemplate.delete(url);
            log.info("RAG 文档已删除: {}", docId);
        } catch (Exception e) {
            log.warn("RAG 删除文档失败: docId={}, error={}", docId, e.getMessage());
        }
    }
}
