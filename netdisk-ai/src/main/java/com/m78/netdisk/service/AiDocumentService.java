package com.m78.netdisk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.m78.netdisk.common.config.DocGeneratorProperties;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.domain.ChatRequest.DocContext;
import com.m78.netdisk.domain.TempDocumentVO;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.service.IFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AiDocumentService {

    private final IFileService fileService;
    private final StorageService storageService;
    private final RestTemplate docGenRestTemplate;
    private final DocGeneratorProperties docGenProps;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_KEY_PREFIX = "temp:doc:";
    private static final Duration REDIS_TTL = Duration.ofMinutes(30);

    public AiDocumentService(IFileService fileService,
                             StorageService storageService,
                             @Qualifier("docGeneratorRestTemplate") RestTemplate docGenRestTemplate,
                             DocGeneratorProperties docGenProps,
                             StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper) {
        this.fileService = fileService;
        this.storageService = storageService;
        this.docGenRestTemplate = docGenRestTemplate;
        this.docGenProps = docGenProps;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ==================== 临时文档生成（调 Python 服务） ====================

    /**
     * 调用 Python 文档生成服务，生成临时文件，返回元数据。
     * docContext != null 时覆盖同一 tempFileId。
     */
    public TempDocumentVO generateTempDocument(Long userId, String content,
                                                String fileName, String fileType,
                                                DocContext docContext) {
        if (content == null || content.isBlank()) {
            throw new BizException("文档内容不能为空");
        }

        // 1. 确定 tempFileId（修改模式复用旧的）
        String tempFileId;
        if (docContext != null && docContext.getTempFileId() != null) {
            tempFileId = docContext.getTempFileId();
        } else {
            tempFileId = UUID.randomUUID().toString().replace("-", "");
        }

        // 2. 构建请求调 Python 服务
        String safeName = sanitizeFileName(fileName);
        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("content", content);
        reqBody.put("format", fileType);
        reqBody.put("fileName", safeName);

        Map<String, Object> resp;
        try {
            resp = docGenRestTemplate.postForObject(
                    docGenProps.getUrl() + "/generate", reqBody, Map.class);
        } catch (Exception e) {
            log.error("调 Python 文档生成服务失败: {}", e.getMessage());
            throw new BizException("文档生成服务暂时不可用，请稍后重试");
        }

        if (resp == null || resp.get("filePath") == null) {
            throw new BizException("文档生成失败，返回结果为空");
        }

        String filePath = resp.get("filePath").toString();
        Long fileSize = resp.containsKey("fileSize")
                ? ((Number) resp.get("fileSize")).longValue() : 0L;

        // 3. 写入 Redis
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("userId", userId);
        meta.put("filePath", filePath);
        meta.put("fileName", safeName + "." + fileType);
        meta.put("fileSize", fileSize);
        meta.put("fileType", fileType);
        meta.put("originalText", content);
        meta.put("createdAt", System.currentTimeMillis());
        meta.put("round", docContext != null ? docContext.getRound() : 0);

        try {
            String json = objectMapper.writeValueAsString(meta);
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + tempFileId, json, REDIS_TTL);
        } catch (Exception e) {
            log.error("Redis 写入临时文档元数据失败: {}", e.getMessage());
            throw new BizException("临时文档存储失败");
        }

        int round = docContext != null ? docContext.getRound() : 0;
        TempDocumentVO vo = new TempDocumentVO(tempFileId, safeName + "." + fileType,
                fileSize, fileType, round, filePath);
        log.info("生成临时文档: tempFileId={}, file={}, size={}, round={}",
                tempFileId, vo.getFileName(), fileSize, round);
        return vo;
    }

    // ==================== 读 Redis 原始文本（修改文档用） ====================

    public String readTempDocContent(String tempFileId, Long userId) {
        String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + tempFileId);
        if (json == null) return null;
        try {
            Map<String, Object> meta = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            if (!userIdMatches(userId, meta)) return null;
            return (String) meta.get("originalText");
        } catch (Exception e) {
            log.error("读取 Redis 临时文档失败: {}", e.getMessage());
            return null;
        }
    }

    // ==================== 确认保存到网盘 ====================

    public ItemVO confirmTempDocument(Long userId, String tempFileId, Long parentId) {
        String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + tempFileId);
        if (json == null) throw new BizException("临时文档不存在或已过期");

        Map<String, Object> meta;
        try {
            meta = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException("临时文档数据损坏");
        }

        if (!userIdMatches(userId, meta)) {
            throw new BizException("无权操作此文档");
        }

        String filePath = (String) meta.get("filePath");
        String fileName = (String) meta.get("fileName");
        String fileType = (String) meta.get("fileType");
        Long fileSize = meta.get("fileSize") != null
                ? ((Number) meta.get("fileSize")).longValue() : 0L;

        // 读取临时文件内容 → 存储到 OSS/本地
        byte[] fileBytes;
        try {
            fileBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(filePath));
        } catch (Exception e) {
            throw new BizException("读取临时文件失败: " + e.getMessage());
        }

        String mimeType = resolveMimeType(fileName);
        String storageKey = "ai-generated/" + UUID.randomUUID().toString().replace("-", "") + "." + fileType;
        storageService.store(storageKey, fileBytes);

        // 创建网盘文件记录
        ItemVO item = fileService.createFile(userId, parentId, fileName,
                (long) fileBytes.length, mimeType, storageKey);

        // 清理临时文件和 Redis
        try {
            java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(filePath));
        } catch (Exception ignored) {}
        redisTemplate.delete(REDIS_KEY_PREFIX + tempFileId);

        log.info("临时文档确认保存: tempFileId={}, itemId={}, name={}", tempFileId, item.getId(), fileName);
        return item;
    }

    // ==================== 获取临时文件下载信息 ====================

    public TempDocumentVO getTempDocumentInfo(String tempFileId, Long userId) {
        String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + tempFileId);
        if (json == null) throw new BizException("临时文档不存在或已过期");

        Map<String, Object> meta;
        try {
            meta = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new BizException("临时文档数据损坏");
        }

        if (!userIdMatches(userId, meta)) {
            throw new BizException("无权访问此文档");
        }

        return new TempDocumentVO(
                tempFileId,
                (String) meta.get("fileName"),
                meta.get("fileSize") != null ? ((Number) meta.get("fileSize")).longValue() : 0L,
                (String) meta.get("fileType"),
                meta.get("round") != null ? ((Number) meta.get("round")).intValue() : 0,
                (String) meta.get("filePath")
        );
    }

    // ==================== 旧版保存（保留兼容，已改用 Python 服务） ====================

    public ItemVO saveDocument(Long userId, String content, String fileName, Long parentId) {
        // 旧版保存：通过 Python 服务生成文件后保存到网盘（不经过临时文档流程）
        if (content == null || content.isBlank()) {
            throw new BizException("文档内容不能为空");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new BizException("文件名不能为空");
        }

        String ext = getFileExtension(fileName);
        if (ext.isEmpty()) {
            throw new BizException("无法识别文件类型，请指定扩展名");
        }

        // 调 Python 服务生成文件
        TempDocumentVO temp = generateTempDocument(userId, content,
                fileName.replaceAll("\\.[^.]+$", ""), ext, null);

        // 读文件字节 → 存 OSS/本地
        byte[] fileBytes;
        try {
            fileBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(temp.getFilePath()));
        } catch (Exception e) {
            throw new BizException("读取生成文件失败: " + e.getMessage());
        }

        String mimeType = resolveMimeType(fileName);
        String storageKey = "ai-generated/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        storageService.store(storageKey, fileBytes);

        ItemVO item = fileService.createFile(userId, parentId, fileName,
                (long) fileBytes.length, mimeType, storageKey);

        // 清理
        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(temp.getFilePath())); } catch (Exception ignored) {}
        redisTemplate.delete(REDIS_KEY_PREFIX + temp.getTempFileId());

        return item;
    }

    // ==================== 文件搜索 & 文档读取（不变） ====================

    public List<ItemVO> searchFiles(Long userId, String query, int limit) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        try {
            var page = fileService.listItems(userId, null, 1, Math.min(limit, 50), query, null, null, null);
            return page != null ? page.getRecords() : Collections.emptyList();
        } catch (Exception e) {
            log.warn("文件搜索失败: userId={}, query={}, error={}", userId, query, e.getMessage());
            return Collections.emptyList();
        }
    }

    public DocumentContent readDocument(Long userId, Long fileId) {
        FileDownloadVO info = fileService.getDownloadInfo(userId, fileId);
        try (var inputStream = storageService.getInputStream(info.getStorageKey())) {
            String ext = getFileExtension(info.getFileName());
            String text = com.m78.netdisk.util.DocumentGenerator.extractText(inputStream, ext);
            return new DocumentContent(fileId, info.getFileName(), info.getFileSize(), text);
        } catch (Exception e) {
            log.error("读取文档失败: fileId={}, error={}", fileId, e.getMessage());
            throw new BizException("读取文档失败: " + e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private String getFileExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot <= 0) ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private String resolveMimeType(String fileName) {
        String ext = getFileExtension(fileName);
        return switch (ext) {
            case "md" -> "text/markdown";
            case "txt" -> "text/plain";
            case "html" -> "text/html";
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    /** 文件名安全清洗：去除 Windows 非法字符，防路径穿越 */
    static String sanitizeFileName(String title) {
        if (title == null || title.isBlank()) return "未命名文档";
        String safe = title
                .replaceAll("[/\\\\:*?\"<>|]", "_")
                .replaceAll("\\.\\.", "_")
                .trim();
        if (safe.length() > 100) safe = safe.substring(0, 100);
        if (safe.isEmpty()) safe = "未命名文档";
        return safe;
    }

    /** 安全比较 Redis 反序列化的 userId（Integer vs Long 问题） */
    private static boolean userIdMatches(Long userId, Map<String, Object> meta) {
        Object stored = meta.get("userId");
        if (stored instanceof Number n) {
            return userId != null && userId.longValue() == n.longValue();
        }
        return false;
    }

    public record DocumentContent(Long fileId, String fileName, Long fileSize, String content) {}
}
