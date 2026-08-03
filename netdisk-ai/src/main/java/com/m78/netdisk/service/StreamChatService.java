package com.m78.netdisk.service;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import com.m78.netdisk.domain.ChatRequest;
import com.m78.netdisk.domain.ChatRequest.DocContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class StreamChatService {

    private final ChatClient chatClient;
    private final RagClient ragClient;
    private final RagServiceProperties properties;
    private final AiDocumentService aiDocumentService;

    public StreamChatService(ChatClient.Builder chatClientBuilder, RagClient ragClient,
                             RagServiceProperties properties, AiDocumentService aiDocumentService) {
        this.chatClient = chatClientBuilder.build();
        this.ragClient = ragClient;
        this.properties = properties;
        this.aiDocumentService = aiDocumentService;
    }

    /** RAG 结果 score 阈值，低于此值的结果被过滤 */
    private static final double RAG_SCORE_THRESHOLD = 0.5;

    /** 系统提示词：约束 LLM 行为 */
    private static final String SYSTEM_PROMPT =
            "你是M78网盘的AI助手。" +
            "能力：回答网盘文件相关问题、总结文档、生成文档内容、分析文档、生成图片。" +
            "规则：1. 用户要求生成文档时，直接输出文档正文，不要加'好的'、'以下是'等说明语句；" +
            "2. 生成文档时不要编造下载链接或文件路径，用户通过页面「保存到网盘」按钮保存；" +
            "3. 仅当用户明确询问文件内容、文档信息时才参考 RAG 提供的文件片段；" +
            "4. 回答能力介绍、问候、闲聊时不得引用任何文件内容；" +
            "5. 当用户明确要求生成/导出/总结成/整理成/写成文档时，在回复末尾附加标记" +
            " [GEN_DOC:文档标题|文件格式]，其中「文档标题」为不含扩展名的文件名，" +
            " 「文件格式」为 md/txt/docx/xlsx/html/json/csv 之一；" +
            "6. 普通对话、回答问题、闲聊时不要加 [GEN_DOC:] 标记；" +
            "7. 当用户对已有文档提出修改要求时，输出修改后的完整文档正文，" +
            " 在末尾附加 [GEN_DOC:新标题|格式]；" +
            "8. 每条回复最多附加一个 [GEN_DOC:] 标记；" +
            "9. 根据 [GEN_DOC] 标记中的格式，采用以下文本规范输出正文：" +
            " - md/txt：Markdown 语法（# 标题、- 列表、| 表格）；" +
            " - html：完整 HTML 文档（含 <html><head><body> 标签）；" +
            " - json：合法 JSON（可被 JSON.parse 解析）；" +
            " - csv：CSV 格式（逗号分隔）；" +
            " - docx：用 Markdown 语法输出，确保层级清晰；" +
            " - xlsx：用 Markdown 表格语法输出，数字不加引号或单位；" +
            "10. 当用户要求生成/画/创建图片时，先简短确认（如'好的，我来生成...'），" +
            " 然后输出 [GEN_IMG:扩写提示词|负面提示词|宽|高] 标记；" +
            "11. 扩写原则：" +
            " - 目标语言：始终使用英文 SD 提示词；" +
            " - 长度：100-200 字；" +
            " - 结构：主体描述 + 环境/背景 + 风格/光照 + 材质细节；" +
            " - 在提示词末尾自动附加画质关键词：8k, highly detailed, sharp focus, masterpiece, professional；" +
            " - 保留用户原意的同时加入专业细节（材质、光线、构图、色彩）；" +
            " - 不加艺术家署名风格（如 'by Greg Rutkowski'）；" +
            " - 负面提示词选填，如需填使用通用负面词；" +
            " - 尺寸根据用户描述场景选：头像/特写→768，半身/场景→768，全景→1024；" +
            "12. 普通对话、问候、文件问答不加 [GEN_IMG:] 标记；" +
            "13. 每条回复最多附加 1 个 [GEN_IMG:] 标记；" +
            "14. 生图完成后可在文本中说明'图片已生成并保存到网盘'。";

    private static final Pattern GEN_DOC_PATTERN =
            Pattern.compile("\\[GEN_DOC:([^\\]|]+)(?:\\|([^\\]|]+))?(?:\\|template=([^\\]]+))?\\]");

    private static final Pattern GEN_IMG_PATTERN =
            Pattern.compile("\\[GEN_IMG:([^\\]|]+)" +
                    "(?:\\|([^\\]|]*))?" +   // negativePrompt（可选）
                    "(?:\\|([^\\]|]*))?" +   // width（可选）
                    "(?:\\|([^\\]|]*))?" +   // height（可选）
                    "\\]");

    public record GenImgTag(String prompt, String negativePrompt, Integer width, Integer height) {}

    /**
     * 流式对话（带 RAG 增强 + 多轮上下文 + 文档生成）。
     *
     * @param message 当前用户消息
     * @param userId 用户 ID
     * @param history 对话历史
     * @param docContext 修改文档上下文（可为 null）
     * @return Flux<String> 第一个元素为 RAG 状态标记 "[RAG:ON]" 或 "[RAG:OFF]"，后续为 AI 回复 chunks
     */
    public Flux<String> streamChat(String message, Long userId,
                                    List<ChatRequest.HistoryMessage> history,
                                    DocContext docContext) {
        AtomicReference<String> fullReply = new AtomicReference<>("");

        // 构建历史消息列表（用于多轮对话）
        List<Message> historyMessages = convertHistory(history);

        // 构建 user prompt（如果 docContext 不为空则注入文档上下文）
        String userPrompt = buildUserPrompt(message, userId, docContext);

        // 始终查询 RAG，由 score 阈值决定是否注入文档内容
        Mono<RagEnrichResult> enrichMono = Mono.fromCallable(() -> enrichWithRag(userPrompt, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .cache();

        return Flux.concat(
                enrichMono.map(enriched -> enriched.ragActive ? "[RAG:ON]" : "[RAG:OFF]"),
                enrichMono.flatMapMany(enriched -> {
                    var promptBuilder = chatClient.prompt()
                            .system(SYSTEM_PROMPT);
                    if (!historyMessages.isEmpty()) {
                        promptBuilder.messages(historyMessages);
                    }
                    return promptBuilder
                            .user(enriched.prompt)
                            .stream()
                            .content()
                            .doOnNext(chunk -> fullReply.updateAndGet(v -> v + chunk))
                            .doOnError(err -> log.error("流式对话出错: {}", err.getMessage()))
                            .doOnComplete(() -> {
                                String reply = fullReply.get();
                                List<GenDocTag> tags = extractGenDocTags(reply);
                                log.info("流式对话完成，RAG={}, historySize={}, 回复长度={}, docTags={}",
                                        enriched.ragActive, historyMessages.size(), reply.length(), tags.size());
                            });
                })
        );
    }

    /**
     * 构建 user prompt，如果 docContext 不为空则注入当前文档上下文。
     */
    private String buildUserPrompt(String message, Long userId, DocContext docContext) {
        if (docContext == null || docContext.getTempFileId() == null) {
            return message;
        }

        String docContent = aiDocumentService.readTempDocContent(docContext.getTempFileId(), userId);
        if (docContent == null) {
            // 临时文件已过期，降级为普通对话
            log.warn("临时文档不存在或已过期: tempFileId={}", docContext.getTempFileId());
            return message;
        }

        if (docContext.getRound() >= 3) {
            // 摘要模式：压缩历史修改记录
            return String.format(
                    "当前文档「%s」的完整内容如下:\n\n```\n%s\n```\n\n" +
                    "用户修改指令: %s\n\n" +
                    "请根据用户指令修改文档，输出修改后的完整文档正文，" +
                    "在末尾附加 [GEN_DOC:新标题|%s]。",
                    docContext.getFileName(), docContent, message, docContext.getFileType()
            );
        } else {
            return String.format(
                    "当前文档「%s」的完整内容如下:\n\n```\n%s\n```\n\n用户修改指令: %s\n\n" +
                    "请根据用户指令修改文档，输出修改后的完整文档正文，" +
                    "在末尾附加 [GEN_DOC:新标题|%s]。",
                    docContext.getFileName(), docContent, message, docContext.getFileType()
            );
        }
    }

    /**
     * 从完整回复中提取 [GEN_DOC:标题|格式] 标记。
     */
    public List<GenDocTag> extractGenDocTags(String reply) {
        List<GenDocTag> tags = new ArrayList<>();
        if (reply == null || reply.isBlank()) return tags;

        Matcher matcher = GEN_DOC_PATTERN.matcher(reply);
        int lastEnd = 0;
        String firstBody = null;

        while (matcher.find()) {
            String title = matcher.group(1) != null ? matcher.group(1).trim() : "未命名文档";
            String format = matcher.group(2) != null ? matcher.group(2).trim() : "md";
            String template = matcher.group(3) != null ? matcher.group(3).trim() : null;

            // 提取标记之前的文本作为文档正文
            String body;
            if (firstBody == null) {
                body = reply.substring(0, matcher.start()).trim();
                firstBody = body;
            } else {
                body = reply.substring(lastEnd, matcher.start()).trim();
            }

            lastEnd = matcher.end();
            tags.add(new GenDocTag(title, format, body, template));
        }

        return tags;
    }

    /**
     * 从完整回复中提取 [GEN_IMG:prompt|negative|width|height] 标记（最多一个）。
     * 返回 null 表示没有生图标记。
     */
    public GenImgTag extractGenImgTag(String reply) {
        if (reply == null || reply.isBlank()) return null;
        Matcher matcher = GEN_IMG_PATTERN.matcher(reply);
        if (!matcher.find()) return null;

        String prompt = matcher.group(1) != null ? matcher.group(1).trim() : null;
        String negativePrompt = matcher.group(2);
        String widthStr = matcher.group(3);
        String heightStr = matcher.group(4);

        Integer width = widthStr != null && !widthStr.isBlank() ? Integer.parseInt(widthStr.trim()) : null;
        Integer height = heightStr != null && !heightStr.isBlank() ? Integer.parseInt(heightStr.trim()) : null;

        return new GenImgTag(prompt, negativePrompt, width, height);
    }

    /**
     * 将前端传来的历史消息转换为 Spring AI Message 列表。
     */
    private static List<Message> convertHistory(List<ChatRequest.HistoryMessage> history) {
        if (history == null || history.isEmpty()) return List.of();
        List<Message> messages = new ArrayList<>(history.size());
        for (var h : history) {
            if ("user".equalsIgnoreCase(h.getRole())) {
                messages.add(new UserMessage(h.getContent()));
            } else if ("assistant".equalsIgnoreCase(h.getRole())) {
                messages.add(new AssistantMessage(h.getContent()));
            }
        }
        return messages;
    }

    /**
     * 用 RAG 检索结果增强用户消息。
     */
    private RagEnrichResult enrichWithRag(String message, Long userId) {
        try {
            List<Map<String, Object>> results = ragClient.query(message, properties.getTopK(), userId);

            if (results.isEmpty()) {
                return new RagEnrichResult(message, false);
            }

            List<Map<String, Object>> relevant = new ArrayList<>();
            for (Map<String, Object> r : results) {
                Object scoreObj = r.get("score");
                double score = 0.0;
                if (scoreObj instanceof Number) {
                    score = ((Number) scoreObj).doubleValue();
                }
                if (score >= RAG_SCORE_THRESHOLD) {
                    relevant.add(r);
                }
            }

            if (relevant.isEmpty()) {
                log.info("RAG 检索到 {} 条结果但 score 均低于阈值 {}, 跳过",
                        results.size(), RAG_SCORE_THRESHOLD);
                return new RagEnrichResult(message, false);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("以下是与你的问题相关的文件内容，请参考这些信息回答：\n\n");

            for (int i = 0; i < relevant.size(); i++) {
                Map<String, Object> r = relevant.get(i);
                String content = Objects.toString(r.get("content"), "");
                String source = Objects.toString(r.get("source"), "未知来源");
                sb.append(String.format("[%d] 来源: %s\n%s\n\n", i + 1, source, content));
            }

            sb.append("用户问题：").append(message).append("\n");

            log.info("RAG 检索到 {} 条，过滤后 {} 条（阈值={}），已注入 prompt",
                    results.size(), relevant.size(), RAG_SCORE_THRESHOLD);
            return new RagEnrichResult(sb.toString(), true);
        } catch (Exception e) {
            log.info("RAG 检索不可用，使用原始消息: {}", e.getMessage());
            return new RagEnrichResult(message, false);
        }
    }

    /**
     * RAG 增强结果。
     */
    private record RagEnrichResult(String prompt, boolean ragActive) {}

    /**
     * [GEN_DOC:] 标记解析结果。
     */
    public record GenDocTag(String title, String format, String body, String template) {}
}
