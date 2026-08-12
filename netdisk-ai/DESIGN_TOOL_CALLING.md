# AI 助手工具调用改造设计文档 v2

> 版本：v2.0（已合并 CR 评审意见）  
> 日期：2026-08-05  
> 状态：待评审

---

## 一、背景与现状

### 1.1 当前架构

```
用户消息 → [RAG 预处理] → 单次 LLM 调用 → 解析 [GEN_DOC] / [GEN_IMG] 标记 → 执行工具 → SSE named events → 前端渲染
                                                              ↓
                                                    结果不回传 LLM（单向）
```

**核心问题：**
- LLM 看不到工具执行结果，无法根据结果调整后续行为
- 标记格式依赖 LLM "猜"输出格式，不稳定，容易出错
- 无法实现多工具协作（如：先搜索文件 → 读取内容 → 生成总结）

### 1.2 当前四个能力

| 能力 | 实现方式 | 触发机制 | 结果回传 LLM |
|---|---|---|---|
| 聊天对话 | Spring AI `ChatClient` | 用户输入 | — |
| RAG 文件问答 | `RagClient` 预处理注入 prompt | 始终执行，score ≥ 0.5 时注入 | — |
| 文档生成 | `[GEN_DOC:标题\|格式]` 正则解析 | LLM 输出标记后触发，onComplete 执行 | ❌ 不回传 |
| 图片生成 | `[GEN_IMG:prompt\|...]` 正则解析 | LLM 输出标记后触发，onComplete 执行 | ❌ 不回传 |

### 1.3 前端依赖说明（CR 确认的关键事实）

前端 `chat-stream.js` 和 `AiAssistant.vue` 中：

```javascript
// chat-stream.js L196-204
} else if (eventType === 'doc-generated') {
  yield { docCard: parsed }
} else if (eventType === 'img-generated') {
  yield { imageRef: parsed }

// chat-stream.js L251-262 (processBuffer 同样处理)
} else if (eventType === 'doc-generated') { ... }
} else if (eventType === 'img-generated') { ... }

// AiAssistant.vue 消费
<ai-doc-cards :doc-cards="msg.docCards" />
<ai-image-card :image-ref="msg.imageRef" />
```

**约束：前端事件消费逻辑必须保留，工具执行后仍需发送相同的 SSE named events。**

---

## 二、目标架构

### 2.1 改造后架构

```
用户消息 → 单次 LLM 调用（注册工具，RAG 预处理禁用）
              ↓
         LLM 自主决定：
         ├─ 直接回答（无工具调用）
         ├─ 调用 searchFileContent(query)
         │     → 返回含 fileId 的文件列表
         │     → 结果回传给 LLM
         │     → LLM 决定是否调用 readDocument(fileId)
         │           → 返回完整内容
         │           → 结果回传给 LLM
         │           → LLM 生成总结，调用 createDocument / 直接输出文字
         ├─ 调用 generateImage(prompt)
         │     → 提交任务，异步执行（5-15秒）
         │     → 立即返回 {taskId, fileId, fileUrl, generating: true}
         │     → 结果回传给 LLM，LLM 输出文字
         │     → 后端异步完成生图后发送 img-generated event
         └─ 调用 createDocument(title, content, format, tempFileId?)
               → 返回 {tempFileId, fileName, fileType}
               → 结果回传给 LLM，LLM 输出文字
               → 同时发送 doc-generated event
```

### 2.2 决策矩阵

| 能力 | 改造方式 | 原因 |
|---|---|---|
| RAG 预处理 | **工具化**（移除自动注入） | 避免与 searchFileContent 工具双重注入；让 LLM 自主决定何时检索 |
| 聊天对话 | **保留** | 主流程，不变 |
| 文档生成 | **改为工具** | 标记协议 → 结构化工具调用 |
| 图片生成 | **改为工具（异步）** | 标记协议 → 结构化工具调用；异步执行避免阻塞 SSE |
| 文件搜索 | **新增工具** | LLM 自主决定何时搜索文件内容 |
| 读取文档 | **新增工具** | 支持 RAG 结果中的 fileId → 全文读取 |

---

## 三、技术方案

### 3.1 框架选型

**使用 Spring AI 原生 `@Tool` 注解，不引入 LangChain4j。**

| 对比项 | Spring AI（当前） | LangChain4j（新引入） |
|---|---|---|
| 现有依赖 | ✅ 已集成 | ❌ 需新增 |
| 工具注册 | `@Tool` 注解 | `@Tool` 注解 |
| 工具上下文（userId） | `ToolContext` 自动注入 | `@ToolContextParam` |
| 文档质量 | 官方文档完善 | 中文资料少 |
| 框架一致性 | 保持单一框架 | 两套框架并存 |

**结论：** 直接在现有 Spring AI 基础上扩展，零额外依赖。

### 3.2 userId 获取机制（修复 CR #4）

**不使用 `ToolContext`，直接使用项目已有的 `UserContext.getUserId()` ThreadLocal。**

```java
@Component
public class NetdiskTools {

    // 不需要将 userId 作为 @Tool 参数（安全风险：LLM 可看到/篡改）
    // 工具方法内部直接读取 ThreadLocal，与 StreamChatService 保持一致
    private Long userId() {
        return UserContext.getUserId();
    }

    @Tool("...")
    public String searchFileContent(@ToolParam(...) String query, ...) {
        // 内部调用 UserContext.getUserId()
        List<Map<String, Object>> results = ragClient.query(query, topK, userId());
        ...
    }
}
```

`UserContext` 由登录 Interceptor 在请求线程中设置，工具执行与 Controller 在同一线程，ThreadLocal 可见。

### 3.3 工具定义

```
新增文件：netdisk-ai/src/main/java/com/m78/netdisk/tool/NetdiskTools.java
```

#### 工具一：`searchFileContent`

**问题修复（CR #2、#7）：RAG 结果不含 fileId，需在工具内部补齐。**

```java
@Tool(
    "搜索网盘中文件的文本内容，返回匹配的文件列表（包含文件名和文件ID）。" +
    "当用户询问文件内容、或需要先了解有哪些相关文件时使用此工具。" +
    "返回结果中的 fileId 可直接传入 readDocument 工具读取完整内容。"
)
public String searchFileContent(
    @ToolParam("搜索关键词或问题描述") String query,
    @ToolParam("返回结果数量，默认3条") @DefaultValue("3") int topK
) {
    // 1. RAG 向量检索（已有逻辑）
    List<Map<String, Object>> ragResults = ragClient.query(query, topK, userId());
    if (ragResults.isEmpty()) {
        return "未找到相关文件";
    }

    // 2. 补齐 fileId：用文件名在 MySQL 中反查（修复 CR #2）
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ragResults.size(); i++) {
        Map<String, Object> r = ragResults.get(i);
        String source = Objects.toString(r.get("source"), "");
        Double score = r.get("score") != null ? ((Number) r.get("score")).doubleValue() : 0.0;
        String content = Objects.toString(r.get("content"), "");

        // 通过文件名在 MySQL 中查找 fileId
        Long fileId = resolveFileIdByFileName(source);

        sb.append(String.format("[%d] 文件名: %s | 相关度: %.2f | fileId: %d\n内容: %s\n",
                i + 1, source, score, fileId, truncate(content, 500)));
    }
    return sb.toString();
}

private Long resolveFileIdByFileName(String fileName) {
    try {
        List<ItemVO> items = aiDocumentService.searchFiles(userId(), fileName, 1);
        return items.isEmpty() ? null : items.get(0).getId();
    } catch (Exception e) {
        log.debug("文件ID解析失败: fileName={}, error={}", fileName, e.getMessage());
        return null;
    }
}
```

**设计要点：**
- RAG 提供文本相似度，MySQL 提供文件 ID，两者互补
- `truncate(content, 500)` 限制内容长度，避免 Token 浪费
- fileId 为 null 时跳过，不阻断流程

#### 工具二：`readDocument`

**问题修复（CR #9）：添加内容截断。**

```java
@Tool(
    "读取网盘中指定文件的完整文本内容。fileId 可以从 searchFileContent 的结果中获取。" +
    "当需要基于文件完整内容进行分析、总结、改写时使用此工具。" +
    "注意：文件内容较长时会自动截断，仅返回前 8000 字符。"
)
public String readDocument(
    @ToolParam("文件的 ID（数字，从 searchFileContent 结果中获取）") Long fileId
) {
    AiDocumentService.DocumentContent doc = aiDocumentService.readDocument(userId(), fileId);
    String content = doc.content();
    if (content.length() > 8000) {
        content = content.substring(0, 8000) + "\n...（内容过长已截断，共 " + doc.content().length() + " 字符）";
        log.warn("readDocument 截断: fileId={}, 原始长度={}, 截断后长度={}", fileId, doc.content().length(), content.length());
    }
    return String.format("文件: %s\n大小: %d 字节\n\n内容:\n%s", doc.fileName(), doc.fileSize(), content);
}
```

#### 工具三：`createDocument`

**问题修复（CR #5）：增加可选的 tempFileId 参数支持编辑模式。**

```java
@Tool(
    "生成文档并保存到网盘，支持 md/txt/docx/xlsx/html/json/csv 格式。" +
    "当用户要求生成、导出、总结、整理、写入文档时使用此工具。" +
    "生成后发送 doc-generated SSE 事件，前端会展示文档卡片，用户可确认保存到网盘。" +
    "如果用户是在修改已有文档，传入 tempFileId 以复用同一临时文件（非首次生成时填此参数）。"
)
public String createDocument(
    @ToolParam("文档标题（不含扩展名）") String title,
    @ToolParam("文档正文内容") String content,
    @ToolParam("文件格式：md/txt/docx/xlsx/html/json/csv") String format,
    @ToolParam("修改文档时的临时文件ID（首次生成留空）") @DefaultValue("") String tempFileId
) {
    // 解析 tempFileId（空字符串视为 null）
    String resolvedTempFileId = tempFileId.isBlank() ? null : tempFileId;

    // 构建 DocContext（修复 CR #5）
    ChatRequest.DocContext docContext = null;
    if (resolvedTempFileId != null) {
        docContext = new ChatRequest.DocContext(
                resolvedTempFileId,
                title + "." + format,
                format,
                0  // round 由调用方控制，工具层统一从 0 开始
        );
    }

    TempDocumentVO doc = aiDocumentService.generateTempDocument(userId(), content, title, format, docContext);

    // 构建工具返回结果（供 LLM 和 SSE 事件共同使用）
    String result = String.format("文档生成成功，tempFileId: %s，文件名: %s，格式: %s",
            doc.getTempFileId(), doc.getFileName(), doc.getFileType());

    // 异步发送 doc-generated SSE 事件（通过回调机制，见 §3.5）
    notifyDocGenerated(doc);

    return result;
}
```

#### 工具四：`generateImage`

**问题修复（CR #3）：异步提交 + 同步返回任务 ID，SSE 事件在后台完成。**

```java
@Tool(
    "根据描述生成图片并保存到网盘的「AI生成」文件夹。" +
    "当用户要求生成、绘制、创建图片时使用此工具。" +
    "生成过程约需 5-15 秒，工具立即返回任务 ID 和预览链接，前端会展示进度状态。" +
    "提示词应使用英文，描述主体、环境、风格、光线等细节。"
)
public String generateImage(
    @ToolParam("图片描述（英文）") String prompt,
    @ToolParam("负面描述，不需要则留空") @DefaultValue("") String negativePrompt,
    @ToolParam("图片宽度，默认768") @DefaultValue("768") int width,
    @ToolParam("图片高度，默认768") @DefaultValue("768") int height
) {
    // 1. 提交生成任务（不等待完成）
    ImageGenResponse response = imageGenerationService.submitAsync(prompt, negativePrompt, width, height, userId());

    // 2. 立即返回，不阻塞 LLM 回复流
    String result = String.format(
            "图片生成任务已提交，taskId: %s，预览链接: %s，预计耗时约 %d 秒。" +
            "生成完成后会在对话中展示图片。",
            response.getTaskId(), response.getFileUrl(), response.getEstimatedSeconds());

    // 3. 异步完成通知（通过回调注册，见 §3.5）
    notifyImageGenerated(response);

    return result;
}
```

**ImageGenerationService 新增异步方法：**

```java
/**
 * 异步提交生图任务，立即返回（不轮询等待）。
 * 生成完成后通过 ImageGenCallback 通知调用方。
 */
public ImageGenResponse submitAsync(String prompt, String negativePrompt, Integer width, Integer height, Long userId) {
    // 与 generate() 相同的提交逻辑，但不等待轮询
    String promptId = comfyUIClient.submitPrompt(workflow, CLIENT_ID);
    // 立即提交后台线程执行轮询
    executorService.submit(() -> completeGenerationAsync(promptId, userId, width, height));
    // 返回含 taskId 的响应
    return new ImageGenResponse(null, null, "pending", promptId, width != null ? width : 768, height != null ? height : 768);
}

private void completeGenerationAsync(String promptId, Long userId, Integer width, Integer height) {
    try {
        ComfyUIClient.PollResult pollResult = comfyUIClient.pollForCompletion(promptId, timeoutMs);
        ImageGenResponse response = processResult(promptId, pollResult, userId, width, height);
        // 调用回调
        callbackRegistry.notifyImageGenerated(userId, response);
    } catch (Exception e) {
        log.error("异步生图完成失败: promptId={}", promptId, e);
    }
}
```

### 3.4 SSE 事件机制（修复 CR #1）

**核心原则：工具执行后通过回调发送 SSE named events，保持前端契约不变。**

```
StreamChatService 注入 NetdiskTools，NetdiskTools 持有 SseEmitter 引用

调用链：
  ChatStreamController.streamChat()
    → StreamChatService.streamChat()
        → chatClient.prompt().tools(netdiskTools).stream().content()
            → [LLM 调用 searchFileContent]
            → [LLM 调用 readDocument]
            → [LLM 调用 createDocument]
                → NetdiskTools.notifyDocGenerated(doc)
                    → SseEmitter.send(event("doc-generated", doc))  ✅ 前端收到
            → [LLM 调用 generateImage]
                → ImageGenerationService.submitAsync()
                    → 后台线程轮询
                    → 完成后 callbackRegistry.notifyImageGenerated(response)
                        → SseEmitter.send(event("img-generated", response))  ✅ 前端收到
            → LLM 输出文字回复
                → SSE chunk 推送
            → SSE done event
```

**回调注册机制（避免循环依赖）：**

```java
// ChatStreamController 中注册回调
SseEmitter emitter = new SseEmitter(300_000L);
netdiskTools.registerSseEmitter(userId, emitter);

// NetdiskTools 中持有回调
public class NetdiskTools {
    private final Map<Long, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    public void registerSseEmitter(Long userId, SseEmitter emitter) {
        emitterMap.put(userId, emitter);
    }

    public void notifyDocGenerated(TempDocumentVO doc) {
        SseEmitter emitter = emitterMap.get(UserContext.getUserId());
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("doc-generated").data(objectMapper.writeValueAsString(doc)));
            } catch (Exception e) {
                log.warn("doc-generated event 发送失败", e);
            }
        }
    }

    public void notifyImageGenerated(ImageGenResponse response) {
        SseEmitter emitter = emitterMap.get(UserContext.getUserId());
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("img-generated").data(objectMapper.writeValueAsString(response)));
            } catch (Exception e) {
                log.warn("img-generated event 发送失败", e);
            }
        }
    }
}
```

**前端无需任何改动**，`doc-generated` 和 `img-generated` 事件触发时机从"LLM 回复完成"变为"工具执行完成"，事件格式完全一致。

### 3.5 StreamChatService 改造

```java
// 改造前
this.chatClient = chatClientBuilder.build();
// SYSTEM_PROMPT：80 行，包含标记格式说明
// enrichWithRag()：RAG 预处理
// extractGenDocTags() / extractGenImgTag()：标记解析

// 改造后
this.chatClient = chatClientBuilder
    .defaultTools(netdiskTools)   // 注册工具 Bean
    .build();
```

**SYSTEM_PROMPT 大幅简化（从 80 行压缩至 10 行）：**

```java
private static final String SYSTEM_PROMPT =
        "你是M78网盘的AI助手。\n" +
        "你可以使用提供的工具：搜索文件内容、读取文档全文、生成文档、生成图片。\n" +
        "规则：\n" +
        "1. 回答文件相关问题时，优先使用 searchFileContent 工具检索相关文件，再根据需要调用 readDocument 读取完整内容。\n" +
        "2. 用户要求生成/导出/总结/整理/写入文档时，使用 createDocument 工具，不要输出文档正文作为文本回复。\n" +
        "3. 用户要求生成/绘制/创建图片时，使用 generateImage 工具，不要仅输出文字描述。\n" +
        "4. 工具执行失败时，如实告知用户，不要编造结果。\n" +
        "5. 生成文档或图片后，用简洁的文字告知用户结果。";
```

**移除的代码：**
- `GEN_DOC_PATTERN` / `GEN_IMG_PATTERN` 正则常量
- `extractGenDocTags()` 方法
- `extractGenImgTag()` 方法
- `GenDocTag` / `GenImgTag` record
- `enrichWithRag()` 方法（RAG 预处理整体移除）
- `RagEnrichResult` record
- `buildUserPrompt()` 中标记格式拼接逻辑（保留 `docContext` 读取逻辑，但不再拼接标记）

**`docContext` 多轮文档修改的处理（修复 CR #5）：**

```java
/**
 * 构建 user prompt，注入文档上下文（修改文档时）。
 * docContext 由前端传入，后端读取内容注入 prompt，LLM 通过 createDocument 保存。
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
    return String.format(
            "当前正在编辑文档「%s」（格式：%s）。完整内容如下：\n\n```\n%s\n```\n\n" +
            "用户修改指令：%s\n\n" +
            "请根据指令输出修改后的完整文档正文，并调用 createDocument 工具保存（传入 tempFileId 参数以保持同一文件）。",
            docContext.getFileName(), docContext.getFileType(), docContent, message
    );
}
```

### 3.6 ChatStreamController 改造

**改造前后对比：**

```
【改造前】
doOnComplete(() -> {
    // 解析标记 → 执行工具 → 发送 SSE event
    List<GenDocTag> tags = streamChatService.extractGenDocTags(reply);
    for (GenDocTag tag : tags) {
        TempDocumentVO doc = aiDocumentService.generateTempDocument(...);
        emitter.send(SseEmitter.event().name("doc-generated").data(...));
    }
    GenImgTag imgTag = streamChatService.extractGenImgTag(reply);
    if (imgTag != null) {
        ImageGenResponse response = imageGenerationService.generate(...);
        emitter.send(SseEmitter.event().name("img-generated").data(...));
    }
    emitter.send(done);
});

【改造后】
doOnComplete(() -> {
    // 工具执行由 @Tool 框架自动处理，SSE 事件由 NetdiskTools 回调发送
    // Controller 只需发送 done 事件
    emitter.send(SseEmitter.event().data(toJson(new StreamChunk("", true))));
});
```

**移除的依赖注入：**
- `AiDocumentService`（不再在 Controller 层直接调用）
- `ImageGenerationService`（不再在 Controller 层直接调用）
- `ObjectMapper`（仍在，用于 StreamChunk 序列化）

**保留的依赖：**
- `StreamChatService`（主流程）
- `netdiskTools`（用于注册 SSE 回调）

---

## 四、RAG 预处理与 searchFileContent 工具的关系（修复 CR #6）

**明确规则：工具模式下禁用 RAG 自动预处理。**

```java
// StreamChatService.streamChat()
// 改造前：始终执行 RAG 预处理
Mono<RagEnrichResult> enrichMono = Mono.fromCallable(() -> enrichWithRag(userPrompt, userId))...

// 改造后：RAG 预处理由 LLM 通过 searchFileContent 工具自主决定
// 不再在 streamChat 中自动调用 RAG
// SYSTEM_PROMPT 中明确："回答文件相关问题时，优先使用 searchFileContent 工具检索"
```

**理由：**
- 双重注入导致 LLM 上下文出现重复内容
- RAG 预处理阈值（0.5）不可调，工具模式下 LLM 可自主控制检索时机
- 对于非文件相关问题（问候、闲聊），搜索工具不会被调用，无性能浪费

---

## 五、工具失败处理（修复 CR #10）

**Spring AI 工具调用失败时，异常信息会被追加到 LLM 的 Observation 中。**

系统 prompt 已包含失败处理规则：
> "工具执行失败时，如实告知用户，不要编造结果。"

**各类失败场景的覆盖：**

| 失败场景 | LLM 看到的 Observation | 预期行为 |
|---|---|---|
| RAG 服务不可用 | `searchFileContent` 返回 "未找到相关文件" 或异常信息 | 告知用户搜索不可用，不提供文件相关内容 |
| ComfyUI 不可用 | `generateImage` 返回异常信息 | 告知用户图片服务暂时不可用 |
| 文件不存在 | `readDocument` 抛出 BizException | 告知用户文件不存在 |
| 文档服务超时 | `createDocument` 返回异常信息 | 告知用户生成失败，建议重试 |

---

## 六、文件变更清单

### 6.1 新增文件

| 文件路径 | 说明 |
|---|---|
| `netdisk-ai/.../tool/NetdiskTools.java` | 四个 `@Tool` 方法 + SSE 回调注册 |
| `netdisk-ai/.../service/ImageGenerationCallback.java` | 异步生图完成回调接口 |
| `netdisk-ai/.../service/CallbackRegistry.java` | 回调注册表（userId → callback） |

### 6.2 修改文件

| 文件路径 | 改动内容 |
|---|---|
| `netdisk-ai/.../service/StreamChatService.java` | 1. 注入 `NetdiskTools`<br>2. `.defaultTools(netdiskTools)`<br>3. 删除所有标记相关代码<br>4. 简化 `SYSTEM_PROMPT`<br>5. 删除 `enrichWithRag()`<br>6. 保留 `buildUserPrompt()`（docContext 逻辑） |
| `netdisk-ai/.../controller/ChatStreamController.java` | 1. 移除 `AiDocumentService` / `ImageGenerationService` 注入<br>2. 简化 `doOnComplete`<br>3. 注册 SSE 回调：`netdiskTools.registerSseEmitter(userId, emitter)` |
| `netdisk-ai/.../service/ImageGenerationService.java` | 新增 `submitAsync()` 方法和回调通知机制 |
| `netdisk-ai/.../service/AiDocumentService.java` | 无逻辑变更（工具层直接调用） |

### 6.3 不改动文件

| 文件 | 原因 |
|---|---|
| `AiDocumentController.java` | 手动保存/预览接口保留 |
| `ImageGenerationController.java` | 手动生图接口保留 |
| `chat-stream.js` | 前端事件消费逻辑不变 |
| `AiAssistant.vue` | 前端组件不变 |
| `RagClient.java` | RAG 查询逻辑不变（改为工具调用） |
| `ComfyUIClient.java` | ComfyUI 交互逻辑不变 |
| `ChatRequest.java` | `DocContext` 保留（多轮修改仍需） |

---

## 七、迁移步骤

### 阶段一：基础改造（不破坏现有功能）

1. 新增 `NetdiskTools.java`，实现四个工具方法（含 fileId 补齐、内容截断、异步生图）
2. 新增 `CallbackRegistry.java` 和 `ImageGenerationCallback.java`
3. 修改 `StreamChatService`，注册工具，禁用 RAG 预处理，简化 prompt
4. 修改 `ChatStreamController`，注册 SSE 回调，简化 onComplete
5. 修改 `ImageGenerationService`，新增 `submitAsync()`
6. 单元测试：验证工具描述正确生成、userId 获取正确、fileId 补齐逻辑

### 阶段二：灰度切换

7. 新增配置开关 `ai.tools.enabled`（默认 false）
8. `StreamChatService` 双模式：
   ```java
   if (toolEnabled) {
       chatClient = chatClientBuilder.defaultTools(netdiskTools).build();
   } else {
       chatClient = chatClientBuilder.build(); // 原有标记协议
   }
   ```
9. 灰度验证：10% 流量走工具模式，观察：
   - 工具调用率（LLM 是否正确使用工具）
   - 工具调用失败率
   - SSE 事件是否正常发送
   - 前端渲染是否正常

### 阶段三：全面切换

10. 确认工具模式稳定后，关闭标记协议分支代码
11. 清理废弃代码：`extractGenDocTags`、`extractGenImgTag`、正则常量、`GenDocTag`、`GenImgTag`、`RagEnrichResult`

### 阶段四：后续优化（可选）

12. 调优 `searchFileContent` 返回格式，提升 LLM 利用率
13. 增加工具调用 trace 日志，便于调试
14. 评估是否引入 `maxToolCalls` 限制，防止工具调用循环

---

## 八、风险与应对

| 风险 | 影响 | 应对措施 |
|---|---|---|
| 工具调用增加 Token 消耗 | 成本上升 20-40% | 灰度观察，设置 `maxToolCalls=5` 限制 |
| LLM 不调用工具直接生成内容 | 功能退化 | system prompt 明确强调"必须使用工具"，配合实测调优 |
| 异步生图回调失败 | 前端收不到 img-generated | 超时兜底：Controller onComplete 中检查 pending 状态，发送错误事件 |
| 工具描述不够清晰导致误调用 | 用户体验下降 | 充分测试工具描述，加入 negative prompt（"不直接输出文件内容"） |
| 多轮工具调用超时 | 响应变慢 | 设置工具调用最大轮次限制（5 轮），超出返回部分结果 |
| userId ThreadLocal 在异步工具回调中不可见 | 工具执行失败 | 工具方法在主线程执行（Spring AI 同步调用），ThreadLocal 可见；异步回调通过 userId 显式传递 |

---

## 九、预期效果

| 指标 | 改造前 | 改造后 |
|---|---|---|
| 工具调用准确率 | ~70%（依赖 LLM 记忆标记格式） | 待实测（预期 ~85-90%，结构化参数降低出错率） |
| 多工具协作能力 | ❌ 不支持 | ✅ 支持（先搜索→再读取→再总结） |
| 工具结果可观察性 | ❌ LLM 看不到结果 | ✅ 结果回传，LLM 可调整策略 |
| Prompt 复杂度 | 80 行标记规则 | 10 行能力说明 |
| Token 消耗 | 基准 | +20-40%（工具描述 + 工具结果） |
| 文档生成延迟 | 同步（阻塞在 onComplete） | 同步（工具内执行），但前端通过 SSE 事件异步展示 |
| 图片生成用户体验 | 先生成后显示 | 立即返回任务 ID，后台生成，前端显示进度 |

---

## 十、附录

### 10.1 工具调用示例对话

```
User: 帮我总结一下《产品需求文档》的内容，生成一份 PPT

Step 1 - LLM 调用 searchFileContent(query="产品需求文档", topK=3)
  Tool Return:
    [1] 文件名: 产品需求文档.md | 相关度: 0.91 | fileId: 1024
        内容: Q3 项目计划包含以下里程碑...
    [2] 文件名: 产品需求文档_v2.md | 相关度: 0.78 | fileId: 1089
        内容: 补充了非功能需求章节...

Step 2 - LLM 调用 readDocument(fileId=1024)
  Tool Return:
    文件: 产品需求文档.md | 大小: 3200 字节
    内容:
    # 项目背景
    ...（完整内容，截断至 8000 字符）

Step 3 - LLM 调用 createDocument(
    title="产品需求PPT",
    content="# P1: 项目背景\n## P2: 核心功能\n...",
    format="md",
    tempFileId=""
)
  Tool Return: 文档生成成功，tempFileId: abc123...
  SSE Event: doc-generated → {tempFileId: "abc123", fileName: "产品需求PPT.md", ...}

Step 4 - LLM 输出文字回复：
  "《产品需求文档》PPT 已生成，共 5 页，包含项目背景、核心功能、非功能需求等内容。
   你可以在网盘的「AI生成」文件夹中查看，点击「保存到网盘」即可永久保存。"
```

### 10.2 Spring AI 工具调用配置

```yaml
# application.yaml（新增配置）
ai:
  tools:
    enabled: false  # 灰度开关，默认关闭
```

```java
// StreamChatService 中根据配置决定使用哪种模式
@Autowired
private ToolConfig toolConfig;  // @ConfigurationProperties("ai.tools")

public StreamChatService(ChatClient.Builder chatClientBuilder, NetdiskTools netdiskTools, ...) {
    if (toolConfig.isEnabled()) {
        this.chatClient = chatClientBuilder.defaultTools(netdiskTools).build();
    } else {
        this.chatClient = chatClientBuilder.build();
    }
}
```
