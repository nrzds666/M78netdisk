package com.m78.netdisk.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.m78.netdisk.common.config.ComfyUIProperties;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.domain.ChatRequest;
import com.m78.netdisk.domain.ImageGenResponse;
import com.m78.netdisk.domain.TempDocumentVO;
import com.m78.netdisk.service.AiDocumentService;
import com.m78.netdisk.service.ImageGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

@Slf4j
@Component
public class NetdiskTools {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("md", "txt", "docx", "xlsx", "html", "json", "csv");
    private static final int READ_CONTENT_TRUNCATE_CHARS = 8000;

    private final AiDocumentService aiDocumentService;
    private final ImageGenerationService imageGenerationService;
    private final ComfyUIProperties comfyUIProperties;
    private final ExecutorService imageGenerationExecutor;
    private final ObjectMapper objectMapper;

    // pending 图片任务跟踪：taskId -> ImageGenResponse
    private final ConcurrentHashMap<String, ImageGenResponse> pendingImageTasks = new ConcurrentHashMap<>();
    // pending 文档任务跟踪：tempFileId -> TempDocumentVO
    private final ConcurrentHashMap<String, TempDocumentVO> pendingDocTasks = new ConcurrentHashMap<>();

    public NetdiskTools(AiDocumentService aiDocumentService,
                        ImageGenerationService imageGenerationService,
                        ComfyUIProperties comfyUIProperties,
                        ExecutorService imageGenerationExecutor,
                        ObjectMapper objectMapper) {
        this.aiDocumentService = aiDocumentService;
        this.imageGenerationService = imageGenerationService;
        this.comfyUIProperties = comfyUIProperties;
        this.imageGenerationExecutor = imageGenerationExecutor;
        this.objectMapper = objectMapper;
    }

    // ==================== 工具一：readDocument ====================

    @Tool(description = """
            读取网盘中指定文件的完整文本内容。
            fileId 可以从系统提供的「相关文件内容」中获取。
            当需要基于文件完整内容进行分析、总结、改写时使用此工具。
            注意：文件内容较长时会自动截断至 8000 字符。
            """)
    public String readDocument(
            @ToolParam(description = "文件的 ID（数字，从上下文中的「相关文件内容」区域获取）") Long fileId,
            ToolContext toolContext) {
        Long uid = (Long) toolContext.getContext().get("userId");
        if (uid == null) {
            return "读取失败：无法获取用户身份，请重新登录后重试";
        }
        try {
            AiDocumentService.DocumentContent doc = aiDocumentService.readDocument(uid, fileId);
            String content = doc.content();
            if (content.length() > READ_CONTENT_TRUNCATE_CHARS) {
                content = content.substring(0, READ_CONTENT_TRUNCATE_CHARS)
                        + "\n...（内容过长已截断，共 " + doc.content().length() + " 字符）";
                log.warn("readDocument 截断: fileId={}, 原始长度={}", fileId, doc.content().length());
            }
            return String.format("文件: %s\n大小: %d 字节\n\n内容:\n%s", doc.fileName(), doc.fileSize(), content);
        } catch (BizException e) {
            return "读取文件失败: " + e.getMessage();
        } catch (Exception e) {
            log.warn("readDocument 异常: fileId={}, error={}", fileId, e.getMessage());
            return "读取文件失败: " + e.getMessage();
        }
    }

    // ==================== 工具二：createDocument ====================

    @Tool(description = """
            生成文档并保存到网盘，支持 md/txt/docx/xlsx/html/json/csv 格式。
            当用户要求生成、导出、总结、整理、写入文档时使用此工具。
            生成后前端会展示文档卡片，用户可确认保存到网盘或下载到本地。
            如果用户是在修改已有文档，传入 tempFileId 以复用同一临时文件（非首次生成时填此参数）。
            文档标题不含扩展名，格式参数为扩展名（如 md, docx）。
            """)
    public String createDocument(
            @ToolParam(description = "文档标题（不含扩展名），如「项目周报」") String title,
            @ToolParam(description = "文档正文内容，按格式要求输出") String content,
            @ToolParam(description = "文件格式：md/txt/docx/xlsx/html/json/csv") String format,
            @ToolParam(description = "修改文档时的临时文件ID（首次生成留空）", required = false) String tempFileId,
            ToolContext toolContext) {
        Long uid = (Long) toolContext.getContext().get("userId");
        if (uid == null) {
            return "生成文档失败：无法获取用户身份，请重新登录后重试";
        }

        // format 白名单校验
        String normalizedFormat = normalizeFormat(format);
        if (normalizedFormat == null) {
            return "格式不支持，请选择以下格式之一：md, txt, docx, xlsx, html, json, csv";
        }
        if (content == null || content.isBlank()) {
            return "文档内容不能为空";
        }

        // 构建 DocContext（修复 round 问题：修改模式下从 Redis 读取当前 round）
        ChatRequest.DocContext docContext = null;
        if (tempFileId != null && !tempFileId.isBlank()) {
            int round = 0;
            TempDocumentVO existing = pendingDocTasks.get(tempFileId);
            if (existing != null) {
                round = existing.getRound();
            } else {
                try {
                    existing = aiDocumentService.getTempDocumentInfo(tempFileId, uid);
                    if (existing != null) {
                        round = existing.getRound();
                        pendingDocTasks.put(tempFileId, existing);
                    }
                } catch (Exception e) {
                    log.debug("读取历史 round 失败，从 0 开始: tempFileId={}", tempFileId);
                }
            }
            docContext = new ChatRequest.DocContext(tempFileId, title + "." + normalizedFormat, normalizedFormat, round);
        }

        try {
            TempDocumentVO doc = aiDocumentService.generateTempDocument(uid, content, title, normalizedFormat, docContext);
            pendingDocTasks.put(doc.getTempFileId(), doc);

            String result = String.format("文档生成成功，tempFileId: %s，文件名: %s，格式: %s，轮次: %d",
                    doc.getTempFileId(), doc.getFileName(), doc.getFileType(), doc.getRound());
            log.info("createDocument 成功: tempFileId={}, title={}, format={}, round={}",
                    doc.getTempFileId(), title, normalizedFormat, doc.getRound());
            return result;
        } catch (BizException e) {
            return "文档生成失败: " + e.getMessage();
        } catch (Exception e) {
            log.warn("createDocument 异常: title={}, error={}", title, e.getMessage());
            return "文档生成失败: " + e.getMessage();
        }
    }

    // ==================== 工具三：generateImage ====================

    @Tool(description = """
            根据描述生成图片并保存到网盘的「AI生成」文件夹。
            当用户要求生成、绘制、创建图片时使用此工具。
            提示词应使用英文，描述主体、环境、风格、光线等细节，末尾附加画质关键词如 8k, highly detailed, sharp focus。
            生成过程约需 5-15 秒，立即返回 taskId，前端通过 taskId 追踪进度。
            可选参数：negativePrompt（负面描述）、width（宽度默认768）、height（高度默认768）。
            """)
    public String generateImage(
            @ToolParam(description = "图片描述（英文，越详细越好，包含主体+环境+风格+光线）") String prompt,
            @ToolParam(description = "负面描述，不需要则留空", required = false) String negativePrompt,
            @ToolParam(description = "图片宽度，默认768，推荐 512/768/1024", required = false) Integer width,
            @ToolParam(description = "图片高度，默认768，推荐 512/768/1024", required = false) Integer height,
            ToolContext toolContext) {
        Long uid = (Long) toolContext.getContext().get("userId");
        if (uid == null) {
            return "图片生成失败：无法获取用户身份，请重新登录后重试";
        }

        // 如果 LLM 给的 prompt 没有逗号，追加画质关键词（简单启发式）
        String effectivePrompt = prompt;
        if (!prompt.contains(",")) {
            effectivePrompt = prompt + ", highly detailed, sharp focus, masterpiece, professional quality";
        }

        Integer w = width != null ? width : comfyUIProperties.getDefaultWidth();
        Integer h = height != null ? height : comfyUIProperties.getDefaultHeight();
        String neg = negativePrompt != null ? negativePrompt : comfyUIProperties.getDefaultNegativePrompt();

        try {
            ImageGenResponse response = imageGenerationService.submitAsync(effectivePrompt, neg, w, h, uid, imageGenerationExecutor,
                    (user, result) -> pendingImageTasks.put(result.getTaskId(), result));
            String taskId = response.getTaskId();
            log.info("generateImage 任务提交: taskId={}, prompt={}, w={}, h={}",
                    taskId, prompt.substring(0, Math.min(50, prompt.length())), w, h);
            return String.format("图片生成任务已提交，taskId: %s，预计耗时约 10-15 秒。完成后前端会展示图片。", taskId);
        } catch (BizException e) {
            return "图片生成失败: " + e.getMessage();
        } catch (Exception e) {
            log.warn("generateImage 异常: error={}", e.getMessage());
            return "图片生成失败: " + e.getMessage();
        }
    }

    // ==================== 辅助方法 ====================

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) return null;
        String normalized = format.trim().toLowerCase();
        return SUPPORTED_FORMATS.contains(normalized) ? normalized : null;
    }

    /**
     * 获取所有 pending 图片任务（供 Controller 在 onComplete 时检查）。
     */
    public Map<String, ImageGenResponse> getPendingImageTasks() {
        return new ConcurrentHashMap<>(pendingImageTasks);
    }

    /**
     * 获取所有 pending 文档任务（供 Controller 在 onComplete 时检查）。
     */
    public Map<String, TempDocumentVO> getPendingDocTasks() {
        return new ConcurrentHashMap<>(pendingDocTasks);
    }

    /**
     * 标记图片任务完成，从 pending 列表中移除。
     */
    public ImageGenResponse completeImageTask(String taskId) {
        return pendingImageTasks.remove(taskId);
    }

    /**
     * 标记文档任务完成，从 pending 列表中移除。
     */
    public TempDocumentVO completeDocTask(String tempFileId) {
        return pendingDocTasks.remove(tempFileId);
    }
}
