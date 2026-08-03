package com.m78.netdisk.share.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.common.utils.JwtTool;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.vo.ShareVO;
import com.m78.netdisk.share.service.IShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final IShareService shareService;
    private final StorageService storageService;
    private final JwtTool jwtTool;

    @PostMapping
    public R<ShareVO> createShare(@Valid @RequestBody CreateShareDTO dto) {
        return R.ok(shareService.createShare(UserContext.getUserId(), dto));
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancelShare(@PathVariable Long id) {
        shareService.cancelShare(UserContext.getUserId(), id);
        return R.ok();
    }

    @GetMapping("/mine")
    public R<IPage<ShareVO>> myShares(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(shareService.listMyShares(UserContext.getUserId(), page, size));
    }

    @GetMapping("/access/{token}")
    public R<ShareVO> accessShare(@PathVariable String token,
                                   @RequestParam(required = false) String password) {
        return R.ok(shareService.accessShare(token, password));
    }

    @GetMapping("/received")
    public R<IPage<ShareVO>> receivedShares(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(shareService.listReceivedShares(UserContext.getUserId(), page, size));
    }

    // ==================== Browse Share Contents ====================

    @GetMapping("/access/{token}/items")
    public R<IPage<ItemVO>> listShareItems(
            @PathVariable String token,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(shareService.listShareItems(token, password, parentId, page, size));
    }

    // ==================== Download from Share ====================

    @GetMapping("/access/{token}/download")
    public void downloadShareFile(@PathVariable String token,
                                   @RequestParam(required = false) String password,
                                   @RequestParam Long itemId,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        FileDownloadVO info = shareService.getShareDownloadInfo(token, password, itemId);
        streamShareFile(info, request, response);
    }

    // ==================== Save Shared Files ====================

    @PostMapping("/access/{token}/save")
    public R<List<ItemVO>> saveShareFiles(
            @PathVariable String token,
            @RequestParam(required = false) String password,
            @RequestBody List<Long> itemIds,
            @RequestHeader("Authorization") String authHeader) {
        // Manually extract userId since /api/shares/access/** is excluded from interceptor
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            try {
                JwtTool.TokenPayload payload = jwtTool.parseToken(jwt);
                UserContext.setUserId(payload.getUserId());
                UserContext.setRole(payload.getRole());
            } catch (Exception e) {
                return R.unauthorized("登录已过期，请重新登录");
            }
        }
        try {
            return R.ok(shareService.saveShareFiles(token, password, itemIds));
        } finally {
            UserContext.remove();
        }
    }

    // ==================== Stream Helper ====================

    /**
     * Stream a shared file with HTTP Range support
     */
    private void streamShareFile(FileDownloadVO info, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {
        // 先验证文件在存储中是否存在，避免 header 提交后才抛出异常
        try {
            storageService.getInputStream(info.getStorageKey()).close();
        } catch (Exception e) {
            log.warn("分享文件存储不存在: storageKey={}, fileName={}", info.getStorageKey(), info.getFileName(), e);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "文件不存在或已被删除");
            return;
        }

        String encodedName = URLEncoder.encode(info.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + encodedName + "\"");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentType(info.getMimeType());

        long fileSize = info.getFileSize();
        String rangeHeader = request.getHeader("Range");

        try (InputStream is = storageService.getInputStream(info.getStorageKey())) {
            if (rangeHeader == null) {
                // Full content
                response.setContentLengthLong(fileSize);
                copy(is, response.getOutputStream(), fileSize);
            } else {
                // Parse Range
                String range = rangeHeader.replace("bytes=", "");
                long start, end;

                try {
                    if (range.startsWith("-")) {
                        // Suffix range: bytes=-500
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
     * Copy exactly count bytes from in to out
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