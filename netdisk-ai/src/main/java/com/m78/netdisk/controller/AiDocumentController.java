package com.m78.netdisk.controller;

import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.domain.TempDocumentVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.service.DocumentConversionService;
import com.m78.netdisk.service.AiDocumentService;
import com.m78.netdisk.service.AiDocumentService.DocumentContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Tag(name = "AI 助手(文档操作)", description = "保存文档、搜索文件、读取文档、临时文档管理")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class AiDocumentController {

    private final AiDocumentService aiDocumentService;
    private final DocumentConversionService documentConversionService;

    // ==================== 旧版保存（保留兼容） ====================

    @Operation(summary = "保存 AI 生成的内容为网盘文件")
    @PostMapping("/save-document")
    public R<SaveDocumentResult> saveDocument(@RequestBody SaveDocumentRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.unauthorized("请先登录");

        try {
            var result = aiDocumentService.saveDocument(userId, request.getContent(), request.getFileName(), request.getParentId());
            return R.ok(new SaveDocumentResult(result.getId(), result.getName(), result.getSize()));
        } catch (Exception e) {
            log.warn("保存文档失败: error={}", e.getMessage());
            return R.fail(400, e.getMessage());
        }
    }

    // ==================== 临时文档管理 ====================

    @Operation(summary = "确认保存临时文档到网盘")
    @PostMapping("/confirm-temp-document")
    public R<SaveDocumentResult> confirmTempDocument(
            @RequestParam String tempFileId,
            @RequestParam(required = false) Long parentId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.unauthorized("请先登录");

        try {
            ItemVO item = aiDocumentService.confirmTempDocument(userId, tempFileId, parentId);
            return R.ok(new SaveDocumentResult(item.getId(), item.getName(), item.getSize()));
        } catch (Exception e) {
            log.warn("确认保存临时文档失败: tempFileId={}, error={}", tempFileId, e.getMessage());
            return R.fail(400, e.getMessage());
        }
    }

    @Operation(summary = "下载临时文档")
    @GetMapping("/download-temp/{tempFileId}")
    public void downloadTempDocument(@PathVariable String tempFileId,
                                      HttpServletResponse response) throws IOException {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            TempDocumentVO doc = aiDocumentService.getTempDocumentInfo(tempFileId, userId);
            Path filePath = Path.of(doc.getFilePath());
            if (!Files.exists(filePath)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            streamTempFile(filePath, doc.getFileName(), "attachment", response);
        } catch (Exception e) {
            log.warn("下载临时文档失败: tempFileId={}, error={}", tempFileId, e.getMessage());
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    @Operation(summary = "预览临时文档（Office 文档自动转 PDF）")
    @GetMapping("/preview-temp/{tempFileId}")
    public void previewTempDocument(@PathVariable String tempFileId,
                                     HttpServletResponse response) throws IOException {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            TempDocumentVO doc = aiDocumentService.getTempDocumentInfo(tempFileId, userId);
            Path filePath = Path.of(doc.getFilePath());
            if (!Files.exists(filePath)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Office 文档 → PDF 转换后内联预览
            if (documentConversionService.isOfficeFile(doc.getFileName())) {
                try (InputStream is = Files.newInputStream(filePath)) {
                    byte[] pdfBytes = documentConversionService.convertToPdf(is, doc.getFileName());
                    String pdfName = doc.getFileName().replaceAll("\\.[^.]+$", "") + ".pdf";
                    String encodedName = URLEncoder.encode(pdfName, StandardCharsets.UTF_8)
                            .replace("+", "%20");
                    response.setContentType("application/pdf");
                    response.setHeader("Content-Disposition", "inline; filename=\"" + encodedName + "\"");
                    response.setContentLengthLong(pdfBytes.length);
                    response.getOutputStream().write(pdfBytes);
                    response.getOutputStream().flush();
                } catch (Exception e) {
                    log.warn("Office 预览转换失败，回退到原始文件: {}", doc.getFileName(), e);
                    streamTempFile(filePath, doc.getFileName(), "inline", response);
                }
                return;
            }

            // 非 Office 文件直接返回
            streamTempFile(filePath, doc.getFileName(), "inline", response);
        } catch (Exception e) {
            log.warn("预览临时文档失败: tempFileId={}, error={}", tempFileId, e.getMessage());
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        }
    }

    // ==================== 文件搜索 & 文档读取 ====================

    @Operation(summary = "搜索当前用户的文件")
    @GetMapping("/search-files")
    public R<List<SearchFileResult>> searchFiles(@RequestParam String query, @RequestParam(defaultValue = "10") int limit) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.unauthorized("请先登录");

        List<ItemVO> items = aiDocumentService.searchFiles(userId, query, limit);
        return R.ok(items.stream().map(i -> new SearchFileResult(i.getId(), i.getName(), i.getSize(), i.getUpdatedAt())).toList());
    }

    @Operation(summary = "读取文档全文")
    @GetMapping("/read-document/{fileId}")
    public R<DocumentContent> readDocument(@PathVariable Long fileId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return R.unauthorized("请先登录");

        try {
            return R.ok(aiDocumentService.readDocument(userId, fileId));
        } catch (Exception e) {
            log.warn("读取文档失败: fileId={}, error={}", fileId, e.getMessage());
            return R.fail(400, e.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 流式输出临时文件到 HTTP 响应。
     */
    private void streamTempFile(Path filePath, String fileName,
                                 String disposition, HttpServletResponse response) throws IOException {
        String mimeType = resolveMimeType(fileName);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType(mimeType);
        response.setHeader("Content-Disposition", disposition + "; filename=\"" + encodedName + "\"");
        response.setContentLengthLong(Files.size(filePath));
        Files.copy(filePath, response.getOutputStream());
        response.getOutputStream().flush();
    }

    private String resolveMimeType(String fileName) {
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) ext = fileName.substring(dot + 1).toLowerCase();
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

    // ==================== DTO ====================

    @Data
    public static class SaveDocumentRequest {
        private String content;
        private String fileName;
        private Long parentId;
    }

    @Data
    @AllArgsConstructor
    public static class SaveDocumentResult {
        private Long fileId;
        private String fileName;
        private Long fileSize;
    }

    @Data
    @AllArgsConstructor
    public static class SearchFileResult {
        private Long fileId;
        private String fileName;
        private Long fileSize;
        private String updatedAt;
    }
}
