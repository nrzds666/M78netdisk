package com.m78.netdisk.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.dto.*;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.domain.vo.MediaProgressVO;
import com.m78.netdisk.file.domain.vo.UploadTaskVO;
import com.m78.netdisk.file.domain.vo.ZipResult;
import com.m78.netdisk.file.service.DocumentConversionService;
import com.m78.netdisk.file.service.IFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final IFileService fileService;
    private final StorageService storageService;
    private final DocumentConversionService documentConversionService;

    @GetMapping("/list")
    public R<IPage<ItemVO>> listItems(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        // 转换 type 筛选为 mime 条件
        String mimePrefix = null;
        List<String> mimeTypes = null;
        String excludePrefix = null;
        if (type != null && !type.isEmpty()) {
            switch (type) {
                case "image": mimePrefix = "image/"; break;
                case "video": mimePrefix = "video/"; break;
                case "audio": mimePrefix = "audio/"; break;
                case "document":
                    mimeTypes = java.util.Arrays.asList(
                            "application/pdf",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/vnd.ms-excel",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-powerpoint",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            "text/plain", "text/html", "text/css", "text/javascript",
                            "application/json", "application/xml");
                    break;
                case "archive":
                    mimeTypes = java.util.Arrays.asList(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/x-rar-compressed",
                            "application/x-7z-compressed",
                            "application/gzip",
                            "application/x-tar",
                            "application/x-bzip2",
                            "application/x-bzip");
                    break;
                case "other":
                    excludePrefix = "image/";  // exclude known types
                    // 排除所有已知类型：在 SQL 中通过多个 excludePrefix 处理
                    // 使用空 mimeTypes 和非空 excludePrefix 实现"其他"
                    break;
                default: break;
            }
        }
        return R.ok(fileService.listItems(UserContext.getUserId(), parentId, page, size,
                query, mimePrefix, mimeTypes, excludePrefix, dateFrom, dateTo));
    }

    @PostMapping("/folder")
    public R<ItemVO> createFolder(@Valid @RequestBody CreateFolderDTO dto) {
        return R.ok(fileService.createFolder(UserContext.getUserId(), dto));
    }

    @PutMapping("/rename")
    public R<ItemVO> rename(@Valid @RequestBody RenameItemDTO dto) {
        return R.ok(fileService.rename(UserContext.getUserId(), dto));
    }

    @PutMapping("/move")
    public R<Void> move(@Valid @RequestBody MoveItemsDTO dto) {
        fileService.move(UserContext.getUserId(), dto);
        return R.ok();
    }

    @GetMapping("/download/batch")
    public void batchDownload(@RequestParam List<Long> ids,
                               HttpServletResponse response) throws IOException {
        ZipResult zip = fileService.getBatchZip(UserContext.getUserId(), ids);

        if (zip.getZipError() != null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "ZIP打包失败: " + zip.getZipError().getMessage());
            return;
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(zip.getZipFileName(), StandardCharsets.UTF_8)
                        .replace("+", "%20") + "\"");
        response.setHeader("Accept-Ranges", "none");

        try (InputStream in = zip.getInputStream();
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
    }

    @DeleteMapping("/trash")
    public R<Void> deleteToTrash(@RequestParam List<Long> ids) {
        fileService.deleteToTrash(UserContext.getUserId(), ids);
        return R.ok();
    }

    @PostMapping("/restore")
    public R<Void> restoreFromTrash(@RequestParam List<Long> ids) {
        fileService.restoreFromTrash(UserContext.getUserId(), ids);
        return R.ok();
    }

    @DeleteMapping("/permanent")
    public R<Void> permanentlyDelete(@RequestParam List<Long> ids) {
        fileService.permanentlyDelete(UserContext.getUserId(), ids);
        return R.ok();
    }

    @GetMapping("/trash")
    public R<IPage<ItemVO>> listTrash(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(fileService.listTrash(UserContext.getUserId(), page, size));
    }

    @GetMapping("/recent")
    public R<List<ItemVO>> listRecentItems(@RequestParam(defaultValue = "3") Integer days) {
        return R.ok(fileService.listRecentItems(UserContext.getUserId(), days));
    }

    @GetMapping("/recent-saves")
    public R<List<ItemVO>> listRecentSaves(@RequestParam(defaultValue = "3") Integer days) {
        return R.ok(fileService.listRecentSaves(UserContext.getUserId(), days));
    }

    @PostMapping("/upload/init")
    public R<UploadTaskVO> initUpload(@Valid @RequestBody InitUploadDTO dto) {
        return R.ok(fileService.initUpload(UserContext.getUserId(), dto));
    }

    @PostMapping("/upload/chunk")
    public R<Void> confirmChunk(@RequestParam Long taskId,
                                 @RequestParam Integer chunkIndex,
                                 @RequestParam String storageKey,
                                 @RequestParam String etag,
                                 @RequestParam Integer size) {
        fileService.confirmChunk(UserContext.getUserId(), taskId, chunkIndex, storageKey, etag, size);
        return R.ok();
    }

    /**
     * 上传单个分片（分片上传使用）
     */
    @PostMapping("/upload/{taskId}/chunk/{index}")
    public R<Void> uploadChunk(@PathVariable Long taskId,
                                @PathVariable Integer index,
                                @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        fileService.uploadChunk(UserContext.getUserId(), taskId, index, file);
        return R.ok();
    }

    @PostMapping("/upload/complete")
    public R<UploadTaskVO> completeUpload(@RequestParam Long taskId) {
        return R.ok(fileService.completeUpload(UserContext.getUserId(), taskId));
    }

    @PostMapping("/upload/{taskId}/pause")
    public R<Void> pauseUpload(@PathVariable Long taskId) {
        fileService.pauseUpload(UserContext.getUserId(), taskId);
        return R.ok();
    }

    @PostMapping("/upload/cancel")
    public R<Void> cancelUpload(@RequestParam Long taskId) {
        fileService.cancelUpload(UserContext.getUserId(), taskId);
        return R.ok();
    }

    @GetMapping("/upload/status")
    public R<UploadTaskVO> getUploadStatus(@RequestParam Long taskId) {
        return R.ok(fileService.getUploadStatus(UserContext.getUserId(), taskId));
    }

    /**
     * 获取未完成的上传任务列表（用于断点续传）
     */
    @GetMapping("/upload/tasks")
    public R<List<UploadTaskVO>> listUnfinishedTasks() {
        return R.ok(fileService.listUnfinishedTasks(UserContext.getUserId()));
    }

    /**
     * 获取已完成的分片索引列表（用于断点续传跳过已上传分片）
     */
    @GetMapping("/upload/tasks/{taskId}/chunks")
    public R<List<Integer>> getCompletedChunks(@PathVariable Long taskId) {
        return R.ok(fileService.getCompletedChunks(UserContext.getUserId(), taskId));
    }

    /**
     * 删除上传任务及所有分片（存储 + DB）
     */
    @DeleteMapping("/upload/tasks/{taskId}")
    public R<Void> deleteUploadTask(@PathVariable Long taskId) {
        fileService.deleteUploadTask(UserContext.getUserId(), taskId);
        return R.ok();
    }

    /**
     * 单文件上传（非分片），直接存储到本地磁盘
     *
     * @param file     上传的文件
     * @param parentId 目标目录ID，传 0 或 null 表示根目录
     * @return 文件信息
     */
    @PostMapping("/upload")
    public R<ItemVO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long parentId) {
        if (file.isEmpty()) {
            return R.fail(400, "上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return R.fail(400, "文件名不能为空");
        }

        // 生成存储路径：uploads/{uuid}/{originalName}
        String storageKey = "uploads/" + UUID.randomUUID().toString().replace("-", "")
                + "/" + originalName;

        try {
            storageService.store(storageKey, file.getInputStream());
            ItemVO vo = fileService.createFile(
                    UserContext.getUserId(), parentId, originalName,
                    file.getSize(), file.getContentType(), storageKey);
            return R.ok(vo);
        } catch (java.io.IOException e) {
            log.error("文件上传写入失败", e);
            return R.fail(500, "文件上传失败");
        }
    }

    /**
     * 单文件下载（支持 HTTP Range 断点续传）
     */
    @GetMapping("/download/{id}")
    public void downloadFile(@PathVariable Long id,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        FileDownloadVO info = fileService.getDownloadInfo(UserContext.getUserId(), id);
        streamFile(info, request, response, "attachment");
    }

    /**
     * 文件预览（浏览器内联展示）。
     * 对 Office 文档（doc/docx/xls/xlsx/ppt/pptx）自动转为 PDF 后再展示。
     */
    @GetMapping("/preview/{id}")
    public void previewFile(@PathVariable Long id,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        FileDownloadVO info = fileService.getPreviewInfo(UserContext.getUserId(), id);

        // Office 文档：转为 PDF 后再返回
        if (documentConversionService.isOfficeFile(info.getFileName())) {
            try (InputStream is = storageService.getInputStream(info.getStorageKey())) {
                byte[] pdfBytes = documentConversionService.convertToPdf(is, info.getFileName());
                String encodedName = URLEncoder.encode(
                        info.getFileName().replaceAll("\\.[^.]+$", "") + ".pdf",
                        StandardCharsets.UTF_8).replace("+", "%20");
                response.setHeader("Content-Disposition", "inline; filename=\"" + encodedName + "\"");
                response.setContentType("application/pdf");
                response.setContentLengthLong(pdfBytes.length);
                response.getOutputStream().write(pdfBytes);
                response.getOutputStream().flush();
            } catch (Exception e) {
                log.warn("Office 预览转换失败，回退到原始文件: {}", info.getFileName(), e);
                streamFile(info, request, response, "inline");
            }
            return;
        }

        streamFile(info, request, response, "inline");
    }

    /**
     * 获取视频首帧截图（poster），供前端 <video poster> 使用
     * 通过 OSS video/snapshot 处理实时截取第 0 帧，返回 JPEG
     */
    @GetMapping("/preview/{id}/poster")
    public void getVideoPoster(@PathVariable Long id,
                                HttpServletResponse response) throws IOException {
        FileDownloadVO info = fileService.getPreviewInfo(UserContext.getUserId(), id);

        if (info.getMimeType() == null || !info.getMimeType().startsWith("video/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Not a video file");
            return;
        }

        try (InputStream in = storageService.getVideoSnapshot(info.getStorageKey(), 0)) {
            if (in == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Poster not available");
                return;
            }
            response.setContentType("image/jpeg");
            response.setHeader("Cache-Control", "max-age=3600");
            copy(in, response.getOutputStream(), Long.MAX_VALUE);
        } catch (Exception e) {
            log.warn("视频海报生成失败: id={}, storageKey={}", id, info.getStorageKey(), e);
            if (!response.isCommitted()) {
                response.reset();
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Poster not available");
            }
        }
    }

    /**
     * 获取缩略图，供文件列表 grid view / 相册缩略图使用。
     * 缩略图存储位置：thumbnails/{itemId}.jpg
     * 通过 StorageService 读取（本地磁盘或 OSS）。
     */
    @GetMapping("/thumbnail/{id}")
    public void getThumbnail(@PathVariable Long id,
                              HttpServletResponse response) throws IOException {
        FileDownloadVO info;
        try {
            info = fileService.getPreviewInfo(UserContext.getUserId(), id);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            return;
        }

        String thumbKey = "thumbnails/" + id + ".jpg";
        try (InputStream in = storageService.getInputStream(thumbKey)) {
            response.setContentType("image/jpeg");
            response.setHeader("Cache-Control", "max-age=86400");
            copy(in, response.getOutputStream(), Long.MAX_VALUE);
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Thumbnail not available");
            }
        }
    }

    /**
     * 文件夹下载（ZIP 打包）
     */
    @GetMapping("/download/folder/{id}")
    public void downloadFolder(@PathVariable Long id,
                                HttpServletResponse response) throws IOException {
        ZipResult zip = fileService.getFolderZip(UserContext.getUserId(), id);

        // 检查 ZIP 打包是否已提前失败（异常传播）
        if (zip.getZipError() != null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "ZIP打包失败: " + zip.getZipError().getMessage());
            return;
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(zip.getZipFileName(), StandardCharsets.UTF_8)
                        .replace("+", "%20") + "\"");
        response.setHeader("Accept-Ranges", "none");

        try (InputStream in = zip.getInputStream();
             OutputStream out = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        }
    }

    /**
     * 获取媒体文件播放进度
     */
    @GetMapping("/progress/{itemId}")
    public R<MediaProgressVO> getProgress(@PathVariable Long itemId) {
        return R.ok(fileService.getProgress(UserContext.getUserId(), itemId));
    }

    /**
     * 保存媒体文件播放进度
     */
    @PutMapping("/progress/{itemId}")
    public R<MediaProgressVO> saveProgress(@PathVariable Long itemId,
                                           @Valid @RequestBody SaveProgressDTO dto) {
        return R.ok(fileService.saveProgress(UserContext.getUserId(), itemId, dto));
    }

    /**
     * 流式输出文件，支持 Range
     */
    private void streamFile(FileDownloadVO info, HttpServletRequest request,
                             HttpServletResponse response, String disposition) throws IOException {
        // 先验证文件在存储中是否存在，避免 header 提交后才抛出异常
        InputStream sourceStream;
        try {
            sourceStream = storageService.getInputStream(info.getStorageKey());
        } catch (Exception e) {
            log.warn("文件存储不存在: storageKey={}", info.getStorageKey(), e);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在或已被删除");
            return;
        }

        String encodedName = URLEncoder.encode(info.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition",
                disposition + "; filename=\"" + encodedName + "\"");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(info.getMimeType());

        long fileSize = info.getFileSize();
        String rangeHeader = request.getHeader("Range");

        try (InputStream is = sourceStream) {
            if (rangeHeader == null) {
                // 全量输出
                response.setContentLengthLong(fileSize);
                copy(is, response.getOutputStream(), fileSize);
            } else {
                // 解析 Range
                String range = rangeHeader.replace("bytes=", "");
                long start, end;

                try {
                    if (range.startsWith("-")) {
                        // 后缀范围: bytes=-500
                        long suffixLength = Long.parseLong(range.substring(1));
                        if (suffixLength <= 0) {
                            response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE,
                                    "Invalid suffix range: " + rangeHeader);
                            return;
                        }
                        start = Math.max(0, fileSize - suffixLength);
                        end = fileSize - 1;
                    } else {
                        String[] parts = range.split("-", 2);
                        start = Long.parseLong(parts[0]);
                        end = parts.length > 1 && !parts[1].isEmpty()
                                ? Long.parseLong(parts[1]) : fileSize - 1;
                    }

                    if (start < 0 || end >= fileSize || start > end) {
                        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                        response.setHeader("Content-Range", "bytes */" + fileSize);
                        return;
                    }

                    long contentLength = end - start + 1;
                    response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                    response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
                    response.setContentLengthLong(contentLength);

                    // 使用服务端范围读取（OSS 等后端可节省带宽），只用 InputStream.close() 后 sourceStream 也被关
                    is.close();
                    try (InputStream ranged = storageService.getInputStream(info.getStorageKey(), start, end)) {
                        copy(ranged, response.getOutputStream(), contentLength);
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的 Range 请求头: {}", rangeHeader);
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid Range header");
                }
            }
        }
    }

    /**
     * 精确拷贝指定字节数
     */
    private void copy(InputStream in, OutputStream out, long count) throws IOException {
        byte[] buf = new byte[8192];
        long remaining = count;
        while (remaining > 0) {
            int len = (int) Math.min(buf.length, remaining);
            int read = in.read(buf, 0, len);
            if (read == -1) break;
            out.write(buf, 0, read);
            remaining -= read;
        }
        out.flush();
    }
}
