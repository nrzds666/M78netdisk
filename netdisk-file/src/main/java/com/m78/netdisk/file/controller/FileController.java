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
import com.m78.netdisk.file.service.IFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

    @GetMapping("/list")
    public R<IPage<ItemVO>> listItems(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(fileService.listItems(UserContext.getUserId(), parentId, page, size));
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

    @PostMapping("/upload/complete")
    public R<UploadTaskVO> completeUpload(@RequestParam Long taskId) {
        return R.ok(fileService.completeUpload(UserContext.getUserId(), taskId));
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
            storageService.store(storageKey, file.getBytes());
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
     * 文件预览（浏览器内联展示）
     */
    @GetMapping("/preview/{id}")
    public void previewFile(@PathVariable Long id,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        FileDownloadVO info = fileService.getPreviewInfo(UserContext.getUserId(), id);
        streamFile(info, request, response, "inline");
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
        String encodedName = URLEncoder.encode(info.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition",
                disposition + "; filename=\"" + encodedName + "\"");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(info.getMimeType());

        long fileSize = info.getFileSize();
        String rangeHeader = request.getHeader("Range");

        try (InputStream is = storageService.getInputStream(info.getStorageKey())) {
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

                    is.skip(start);
                    copy(is, response.getOutputStream(), contentLength);
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
