package com.m78.netdisk.service;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import com.m78.netdisk.domain.ChatRequest;
import com.m78.netdisk.domain.ChatRequest.DocContext;
import com.m78.netdisk.tool.NetdiskTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class StreamChatService {

    private final ChatClient chatClient;
    private final AiDocumentService aiDocumentService;
    private final NetdiskTools netdiskTools;
    private final RagClient ragClient;
    private final RagServiceProperties ragProperties;

    public StreamChatService(ChatClient.Builder chatClientBuilder,
                             NetdiskTools netdiskTools,
                             AiDocumentService aiDocumentService,
                             RagClient ragClient,
                             RagServiceProperties ragProperties) {
        this.aiDocumentService = aiDocumentService;
        this.netdiskTools = netdiskTools;
        this.ragClient = ragClient;
        this.ragProperties = ragProperties;
        // 注册工具：LLM 可自主调用 readDocument, createDocument, generateImage
        this.chatClient = chatClientBuilder.defaultTools(netdiskTools).build();
    }

    /**
     * 系统提示词：简洁说明能力，具体行为由工具描述约束。
     */
    private static final String SYSTEM_PROMPT =
            "你是 M78 网盘的 AI 助手，帮助用户管理、搜索、生成网盘中的文档和图片。\n" +
            "能力：\n" +
            "1. 搜索文件：系统已自动检索用户网盘中与当前问题相关的文件内容（见下方「相关文件内容」区域），你无需再次搜索。\n" +
            "2. 读取文件：如需查看某文件的完整内容，使用 readDocument 工具（传入 fileId）。\n" +
            "3. 生成文档：用户要求生成/导出/总结/整理/写入文档时，调用 createDocument 工具，不要直接输出文档正文作为文本回复。\n" +
            "4. 生成图片：用户要求生成/绘制/创建图片时，调用 generateImage 工具，不要仅输出文字描述。\n" +
            "5. 回答文件相关问题时，优先参考下方提供的文件内容，再做出回答。\n" +
            "6. 工具执行失败时，如实告知用户，不要编造结果。\n" +
            "7. 生成文档或图片后，用简洁的文字告知用户结果和操作方式。\n" +
            "8. 普通对话、问候、闲聊时不需要调用工具，直接回复即可。";

    /**
     * 流式对话（工具调用模式）。
     */
    public Flux<String> streamChat(String message, Long userId,
                                    List<ChatRequest.HistoryMessage> history,
                                    DocContext docContext) {
        AtomicReference<String> fullReply = new AtomicReference<>("");

        List<Message> historyMessages = convertHistory(history);
        String ragContext = buildRagContext(message, userId);
        String systemPrompt = buildSystemPrompt(ragContext);
        String userPrompt = buildUserPrompt(message, userId, docContext);

        return chatClient.prompt()
                .system(systemPrompt)
                .messages(historyMessages)
                .user(userPrompt)
                .toolContext(Map.of("userId", userId))
                .stream()
                .content()
                .doOnNext(chunk -> fullReply.updateAndGet(v -> v + chunk))
                .doOnError(err -> log.error("流式对话出错: {}", err.getMessage()))
                .doOnComplete(() -> {
                    String reply = fullReply.get();
                    log.info("流式对话完成，userId={}, 回复长度={}", userId, reply.length());
                });
    }

    /**
     * 构建 system prompt，注入 RAG 检索上下文（如果有）。
     */
    private String buildSystemPrompt(String ragContext) {
        if (ragContext == null || ragContext.isEmpty()) {
            return SYSTEM_PROMPT;
        }
        return SYSTEM_PROMPT + "\n\n" + ragContext;
    }

    /**
     * 检索 RAG 知识库并过滤后拼接为上下文文本。
     * 过滤逻辑：
     * 1. 分数阈值过滤（低于 scoreThreshold 丢弃）
     * 2. 同 source 去重（只保留最高分）
     * 3. 总长度截断（超出 contextMaxChars 截断）
     *
     * @return 格式化后的上下文文本，无结果时返回空字符串
     */
    private String buildRagContext(String query, Long userId) {
        if (!ragProperties.isEnabled()) {
            return "";
        }

        int topK = ragProperties.getTopK();
        double threshold = ragProperties.getScoreThreshold();
        int maxChars = ragProperties.getContextMaxChars();

        List<Map<String, Object>> rawResults;
        try {
            rawResults = ragClient.query(query, topK, userId);
        } catch (Exception e) {
            log.warn("RAG 检索异常，跳过上下文注入: {}", e.getMessage());
            return "";
        }

        if (rawResults == null || rawResults.isEmpty()) {
            return "";
        }

        // 过滤：分数阈值 + 去重（同 source 保留最高分）
        List<Map<String, Object>> filtered = rawResults.stream()
                .filter(r -> {
                    Object scoreObj = r.get("score");
                    if (scoreObj == null) return false;
                    double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;
                    return score >= threshold;
                })
                .collect(ArrayList::new, (list, r) -> {
                    String source = String.valueOf(r.get("source"));
                    // 检查是否已有同 source 的结果
                    boolean exists = list.stream().anyMatch(existing ->
                            source.equals(String.valueOf(existing.get("source"))));
                    if (!exists) {
                        list.add(r);
                    }
                }, ArrayList::addAll);

        if (filtered.isEmpty()) {
            log.debug("RAG 检索结果全部被过滤（阈值={}），query={}", threshold, query);
            return "";
        }

        // 拼接上下文，控制总长度
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户网盘中的相关文件内容\n");

        int totalChars = sb.length();
        int idx = 1;
        for (Map<String, Object> r : filtered) {
            String source = String.valueOf(r.get("source"));
            double score = r.get("score") instanceof Number ? ((Number) r.get("score")).doubleValue() : 0.0;
            String content = String.valueOf(r.getOrDefault("content", ""));
            // 单个 chunk 截断至 500 字符
            if (content.length() > 500) {
                content = content.substring(0, 500) + "...";
            }

            String entry = String.format("\n[%d] 文件名: %s | 相关度: %.2f\n内容: %s\n", idx, source, score, content);
            if (totalChars + entry.length() > maxChars) {
                sb.append(String.format("\n...（共 %d 条结果，已截断至 %d 字符）\n", filtered.size(), maxChars));
                break;
            }
            sb.append(entry);
            totalChars += entry.length();
            idx++;
        }

        log.debug("RAG 上下文注入: query={}, 原始结果={}, 过滤后={}, 注入字符数={}",
                query, rawResults.size(), filtered.size(), totalChars);
        return sb.toString();
    }

    /**
     * 构建 user prompt，注入文档上下文（修改文档时）。
     */
    private String buildUserPrompt(String message, Long userId, DocContext docContext) {
        if (docContext == null || docContext.getTempFileId() == null) {
            return message;
        }

        String docContent = aiDocumentService.readTempDocContent(docContext.getTempFileId(), userId);
        if (docContent == null) {
            log.warn("临时文档不存在或已过期: tempFileId={}", docContext.getTempFileId());
            return message;
        }

        int round = docContext.getRound();
        if (round >= 3) {
            return String.format(
                    "当前文档「%s」的完整内容如下:\n\n```\n%s\n```\n\n" +
                    "用户修改指令: %s\n\n" +
                    "请根据用户指令修改文档，调用 createDocument 工具保存（传入 tempFileId 参数保持同一文件）。",
                    docContext.getFileName(), docContent, message
            );
        } else {
            return String.format(
                    "当前文档「%s」的完整内容如下:\n\n```\n%s\n```\n\n用户修改指令: %s\n\n" +
                    "请根据用户指令修改文档，调用 createDocument 工具保存（传入 tempFileId 参数保持同一文件）。",
                    docContext.getFileName(), docContent, message
            );
        }
    }

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
}
