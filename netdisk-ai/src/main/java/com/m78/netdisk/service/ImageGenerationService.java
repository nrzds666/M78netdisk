package com.m78.netdisk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.m78.netdisk.client.ComfyUIClient;
import com.m78.netdisk.common.config.ComfyUIProperties;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.domain.ImageGenResponse;
import com.m78.netdisk.file.domain.dto.CreateFolderDTO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.service.IFileService;
import com.m78.netdisk.common.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 图片生成服务 - 调用 ComfyUI (SDXL Turbo) 生成图片并保存到网盘。
 */
@Slf4j
@Service
public class ImageGenerationService {

    private static final String CLIENT_ID = "m78-netdisk";

    private final IFileService fileService;
    private final StorageService storageService;
    private final ComfyUIClient comfyUIClient;
    private final ComfyUIProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    // 并发控制：每个用户同时只能有一个生成任务（用 AtomicBoolean 实现，设计文档要求返回 429）
    private final Map<Long, AtomicBoolean> userGeneratingLocks = new ConcurrentHashMap<>();

    public ImageGenerationService(IFileService fileService, StorageService storageService,
                                  ComfyUIClient comfyUIClient, ComfyUIProperties properties,
                                  ObjectMapper objectMapper) {
        this.fileService = fileService;
        this.storageService = storageService;
        this.comfyUIClient = comfyUIClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成图片主流程。
     */
    public ImageGenResponse generate(String prompt, String negativePrompt, Integer width, Integer height, Long userId) {
        // 参数校验
        if (prompt == null || prompt.isBlank()) {
            throw new BizException(400, "请输入图片描述");
        }
        if (prompt.length() > 2000) {
            throw new BizException(400, "描述文字过长，请精简到 2000 字以内");
        }

        // 并发控制：检查是否有正在进行的生成任务（设计文档要求：AtomicBoolean.compareAndSet → 冲突时返回 429）
        AtomicBoolean flag = userGeneratingLocks.computeIfAbsent(userId, k -> new AtomicBoolean(false));
        if (!flag.compareAndSet(false, true)) {
            throw new BizException(429, "图片生成任务正在进行中，请稍后重试");
        }
        try {
            return generateInternal(prompt, negativePrompt, width, height, userId);
        } finally {
            flag.set(false); // 释放锁
        }
    }

    private ImageGenResponse generateInternal(String prompt, String negativePrompt, Integer width, Integer height, Long userId) {
        log.info("开始生图: userId={}, prompt={}, w={}, h={}", userId, prompt.substring(0, Math.min(50, prompt.length())), width, height);

        // 1. 构建 SDXL Turbo 工作流 JSON
        JsonNode workflow = buildSdXlTurboWorkflow(prompt, negativePrompt, width, height);

        // 2. 提交任务到 ComfyUI
        String promptId;
        try {
            promptId = comfyUIClient.submitPrompt(workflow, CLIENT_ID);
            log.info("ComfyUI 任务提交成功: promptId={}", promptId);
        } catch (ComfyUIClient.ServiceException e) {
            throw new BizException(500, e.getMessage());
        } catch (Exception e) {
            throw new BizException(500, "提交图片生成任务失败: " + e.getMessage());
        }

        // 3. 轮询等待完成
        long timeoutSeconds = properties.getPollTimeout();
        log.info("开始轮询 ComfyUI: promptId={}, timeout={}s", promptId, timeoutSeconds);
        ComfyUIClient.PollResult pollResult;
        try {
            pollResult = comfyUIClient.pollForCompletion(promptId, timeoutSeconds * 1000);
            log.info("轮询完成: promptId={}", promptId);
        } catch (ComfyUIClient.TimeoutException e) {
            log.error("轮询超时: promptId={}, timeout={}s", promptId, timeoutSeconds);
            throw new BizException(504, e.getMessage());
        } catch (ComfyUIClient.ServiceException e) {
            throw new BizException(500, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(500, "图片生成过程中断");
        } catch (Exception e) {
            throw new BizException(500, "轮询生成状态异常: " + e.getMessage());
        }

        // 4. 提取输出信息
        ComfyUIClient.ImageOutput imageOutput = comfyUIClient.getImageOutput(pollResult.status());
        log.info("图片输出信息: filename={}, subfolder={}, type={}", imageOutput.filename(), imageOutput.subfolder(), imageOutput.type());

        // 5. 下载图片
        byte[] imageBytes;
        try {
            imageBytes = comfyUIClient.downloadImage(imageOutput.filename(), imageOutput.subfolder(), imageOutput.type());
            log.info("图片下载成功: {} bytes", imageBytes.length);
        } catch (ComfyUIClient.ServiceException e) {
            throw new BizException(500, e.getMessage());
        } catch (Exception e) {
            throw new BizException(500, "下载生成图片失败: " + e.getMessage());
        }

        // 6. 验证 PNG 头部
        if (!comfyUIClient.validatePngHeader(imageBytes)) {
            throw new BizException(500, "生成的图片无效（不是有效的 PNG 格式）");
        }
        log.info("PNG 头部校验通过");

        // 7. 存储到 OSS/本地
        String fileName = generateFileName("ai-image", ".png");
        String storageKey = "ai-generated/" + UUID.randomUUID().toString().replace("-", "") + ".png";
        try {
            storageService.store(storageKey, imageBytes);
            log.info("图片存储成功: storageKey={}", storageKey);
        } catch (Exception e) {
            log.error("图片存储失败: storageKey={}", storageKey, e);
            throw new BizException(500, "图片存储失败: " + e.getMessage());
        }

        // 8. 创建"AI生成"文件夹（如果不存在）
        Long folderId = getOrCreateAiGeneratedFolder(userId);
        log.info("AI生成文件夹: folderId={}", folderId);

        // 9. 创建网盘文件记录
        ItemVO item = fileService.createFile(userId, folderId, fileName, (long)imageBytes.length, "image/png", storageKey);
        log.info("网盘文件记录创建: fileId={}, fileName={}", item.getId(), fileName);

        // 10. 返回结果
        ImageGenResponse response = new ImageGenResponse();
        response.setFileId(item.getId());
        response.setFileName(fileName);
        response.setFileUrl("/api/files/preview/" + item.getId());
        response.setFileSize(item.getSize());
        response.setWidth(width != null ? width : properties.getDefaultWidth());
        response.setHeight(height != null ? height : properties.getDefaultHeight());

        log.info("图片生成完成: fileId={}, fileName={}, size={} bytes", item.getId(), fileName, item.getSize());
        return response;
    }

    /**
     * 构建 SDXL Turbo 工作流 JSON。
     */
    private JsonNode buildSdXlTurboWorkflow(String prompt, String negativePrompt, Integer width, Integer height) {
        int w = width != null ? width : properties.getDefaultWidth();
        int h = height != null ? height : properties.getDefaultHeight();
        String negPrompt = negativePrompt != null ? negativePrompt : properties.getDefaultNegativePrompt();
        long seed = secureRandom.nextLong() & Long.MAX_VALUE;

        // 创建节点对象工厂
        ObjectNodeFactory factory = new ObjectNodeFactory(objectMapper);

        // 工作流对象
        ObjectNode workflow = factory.objectNode();

        // 节点 4: CheckpointLoaderSimple
        ObjectNode node4 = factory.objectNode();
        node4.put("class_type", "CheckpointLoaderSimple");
        ObjectNode inputs4 = factory.objectNode();
        inputs4.put("ckpt_name", properties.getModelFileName());
        node4.set("inputs", inputs4);
        workflow.put("4", node4);

        // 节点 6: CLIPTextEncode (正提示词)
        ObjectNode node6 = factory.objectNode();
        node6.put("class_type", "CLIPTextEncode");
        ObjectNode inputs6 = factory.objectNode();
        inputs6.put("text", prompt);
        inputs6.put("clip", factory.arrayNode().add("4").add(1));
        node6.set("inputs", inputs6);
        workflow.put("6", node6);

        // 节点 7: CLIPTextEncode (负提示词)
        ObjectNode node7 = factory.objectNode();
        node7.put("class_type", "CLIPTextEncode");
        ObjectNode inputs7 = factory.objectNode();
        inputs7.put("text", negPrompt);
        inputs7.put("clip", factory.arrayNode().add("4").add(1));
        node7.set("inputs", inputs7);
        workflow.put("7", node7);

        // 节点 5: EmptyLatentImage
        ObjectNode node5 = factory.objectNode();
        node5.put("class_type", "EmptyLatentImage");
        ObjectNode inputs5 = factory.objectNode();
        inputs5.put("width", w);
        inputs5.put("height", h);
        inputs5.put("batch_size", 1);
        node5.set("inputs", inputs5);
        workflow.put("5", node5);

        // 节点 3: KSampler
        ObjectNode node3 = factory.objectNode();
        node3.put("class_type", "KSampler");
        ObjectNode inputs3 = factory.objectNode();
        inputs3.put("seed", seed);
        inputs3.put("steps", properties.getDefaultSteps());
        inputs3.put("cfg", properties.getDefaultCfg());
        inputs3.put("sampler_name", properties.getDefaultSampler());
        inputs3.put("scheduler", properties.getDefaultScheduler());
        inputs3.put("denoise", 1.0);
        inputs3.put("model", factory.arrayNode().add("4").add(0));
        inputs3.put("positive", factory.arrayNode().add("6").add(0));
        inputs3.put("negative", factory.arrayNode().add("7").add(0));
        inputs3.put("latent_image", factory.arrayNode().add("5").add(0));
        node3.set("inputs", inputs3);
        workflow.put("3", node3);

        // 节点 8: VAEDecode
        ObjectNode node8 = factory.objectNode();
        node8.put("class_type", "VAEDecode");
        ObjectNode inputs8 = factory.objectNode();
        inputs8.put("samples", factory.arrayNode().add("3").add(0));
        inputs8.put("vae", factory.arrayNode().add("4").add(2));
        node8.set("inputs", inputs8);
        workflow.put("8", node8);

        // 节点 9: SaveImage
        ObjectNode node9 = factory.objectNode();
        node9.put("class_type", "SaveImage");
        ObjectNode inputs9 = factory.objectNode();
        inputs9.put("filename_prefix", "m78-ai-gen");
        inputs9.put("images", factory.arrayNode().add("8").add(0));
        node9.set("inputs", inputs9);
        workflow.put("9", node9);

        return workflow;
    }

    /**
     * 获取或创建"AI生成"文件夹。
     */
    private Long getOrCreateAiGeneratedFolder(Long userId) {
        try {
            // 模糊搜索"AI生成"，但只取文件夹（排除文件名含"AI生成"的普通文件）
            List<ItemVO> items = fileService.listItems(userId, null, 1, 50, "AI生成", null, null, null).getRecords();
            if (items != null) {
                for (ItemVO item : items) {
                    if (Boolean.TRUE.equals(item.getIsDirectory())) {
                        log.info("找到已有AI生成文件夹: folderId={}", item.getId());
                        return item.getId();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("查找 AI 生成文件夹失败: ", e);
        }

        // 创建新文件夹（在根目录下）
        try {
            CreateFolderDTO dto = new CreateFolderDTO();
            dto.setName("AI生成");
            dto.setParentId(null); // 根目录
            ItemVO folder = fileService.createFolder(userId, dto);
            log.info("创建AI生成文件夹: folderId={}", folder.getId());
            return folder.getId();
        } catch (Exception e) {
            log.error("创建 AI 生成文件夹失败: ", e);
            throw new BizException(500, "无法创建文件夹: " + e.getMessage());
        }
    }

    /**
     * 生成带时间戳的文件名。
     */
    private String generateFileName(String prefix, String extension) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        return prefix + "-" + sdf.format(new Date()) + extension;
    }

    /**
     * Jackson NodeFactory 辅助类，用于方便地构建工作流 JSON。
     */
    private static class ObjectNodeFactory {
        private final ObjectMapper mapper;

        public ObjectNodeFactory(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        public ObjectNode objectNode() {
            return mapper.createObjectNode();
        }

        public ArrayNode arrayNode() {
            return mapper.createArrayNode();
        }
    }
}
