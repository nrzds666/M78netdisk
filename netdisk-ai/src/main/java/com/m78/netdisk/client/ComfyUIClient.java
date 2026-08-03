package com.m78.netdisk.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.m78.netdisk.common.config.ComfyUIProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

/**
 * ComfyUI REST API 客户端 - 封装 SDXL Turbo 工作流提交、状态轮询、图片下载等操作。
 */
public class ComfyUIClient {

    private static final Logger log = LoggerFactory.getLogger(ComfyUIClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final ComfyUIProperties properties;

    public ComfyUIClient(RestTemplate restTemplate, ComfyUIProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.baseUrl = properties.getBaseUrl();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交生成任务到 ComfyUI，返回 prompt_id。
     */
    public String submitPrompt(JsonNode workflow, String clientId) {
        // 用注入的 ObjectMapper 预先序列化，确保与 ImageGenerationService 中使用的是同一个 mapper
        String body;
        try {
            Map<String, Object> requestMap = new LinkedHashMap<>();
            requestMap.put("prompt", workflow);
            requestMap.put("client_id", clientId);
            body = objectMapper.writeValueAsString(requestMap);
        } catch (Exception e) {
            throw new ServiceException(500, "序列化工作流失败: " + e.getMessage());
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            JsonNode response = restTemplate.postForObject(baseUrl + "/prompt", entity, JsonNode.class);
            if (response == null) {
                throw new IllegalStateException("ComfyUI /prompt 返回为空");
            }

            String promptId = response.get("prompt_id").asText();
            JsonNode numberNode = response.get("number");
            if (numberNode != null && !numberNode.isNull()) {
                int queueNumber = numberNode.asInt();
                if (queueNumber > 0) {
                    log.info("任务提交成功: prompt_id={}, queue_number={}", promptId, queueNumber);
                } else {
                    log.warn("任务提交成功但队列编号无效: {}", promptId);
                }
            } else {
                log.warn("任务提交成功但队列编号为空: {}", promptId);
            }

            // 检查 node_errors
            JsonNode errors = response.get("node_errors");
            if (errors != null && errors.size() > 0) {
                Map<String, String> errorMap = objectMapper.treeToValue(errors, Map.class);
                throw new RuntimeException("节点配置错误: " + errorMap);
            }

            return promptId;
        } catch (HttpClientErrorException e) {
            // 4xx 错误 - workflow JSON 格式错误等
            String responseBody = e.getResponseBodyAsString();
            log.error("ComfyUI /prompt 请求失败 (HTTP {}): {}, body={}",
                    e.getStatusCode(), e.getStatusText(), responseBody);
            throw new ServiceException(500, "工作流格式错误，请检查参数设置");
        } catch (HttpServerErrorException e) {
            // 5xx 错误
            log.error("ComfyUI /prompt 服务端错误: {} - {}", e.getStatusCode(), e.getMessage());
            throw new ServiceException(500, "ComfyUI 服务错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("提交 ComfyUI 任务失败: ", e);
            throw new ServiceException(500, "提交任务失败: " + e.getMessage());
        }
    }

    /**
     * 轮询查询任务状态，直到完成或超时。
     * 返回包含输出信息的响应对象。
     */
    public PollResult pollForCompletion(String promptId, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long intervalMillis = properties.getPollIntervalMs();
        long maxIntervalMillis = properties.getPollMaxIntervalMs();

        while (true) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= timeoutMs) {
                // 最后一次抢救：超时边界可能刚好完成（ComfyUI 异步完成 + 网络延迟）
                JsonNode finalStatus = checkStatus(promptId);
                if (finalStatus != null && isCompleted(finalStatus)) {
                    log.info("任务在超时边界抢救成功: promptId={}", promptId);
                    return new PollResult(true, finalStatus);
                }
                throw new TimeoutException("图片生成超时（超过 " + timeoutMs / 1000 + "秒）");
            }

            JsonNode status = checkStatus(promptId);
            if (status == null) {
                log.warn("未找到 prompt_id={}，继续等待...", promptId);
            } else {
                String statusStr = getStatusStr(status);
                boolean completed = isCompleted(status);

                if (statusStr.equals("error")) {
                    throw new ErrorException("图片生成失败: " + extractErrorMessage(status));
                }

                if (completed) {
                    log.info("任务 prompt_id={} 完成成功", promptId);
                    return new PollResult(true, status);
                }
            }

            // 指数退避：先应用当前间隔，然后加倍作为下次间隔（首轮使用基础间隔，后续指数增长）
            long sleepTime = intervalMillis;
            intervalMillis = Math.min(intervalMillis * 2, maxIntervalMillis);
            Thread.sleep(sleepTime);

            long remaining = timeoutMs - elapsed;
            if (remaining > 0) {
                log.debug("正在轮询 prompt_id={}, 剩余时间={}", promptId, remaining / 1000);
            }
        }
    }

    /**
     * 检查单个 prompt 的状态（不阻塞）。
     * ComfyUI /history/{promptId} 返回 {"promptId": {"outputs":..., "status":...}}
     * 这里解包取内层节点，下游 getStatusStr/isCompleted/getImageOutput 直接读内层字段。
     */
    private JsonNode checkStatus(String promptId) {
        try {
            JsonNode history = restTemplate.getForObject(baseUrl + "/history/" + promptId, JsonNode.class);
            if (history == null) return null;
            // 解包：ComfyUI 用 promptId 作为 key 包裹实际状态
            JsonNode entry = history.get(promptId);
            return entry != null ? entry : history;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null; // 暂时没找到，正常情况
            }
            log.error("查询 prompt_id={} 状态失败: {} - {}", promptId, e.getStatusCode(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("查询状态异常: ", e);
            return null;
        }
    }

    private String getStatusStr(JsonNode status) {
        JsonNode statusObj = status.get("status");
        if (statusObj != null) {
            return statusObj.get("status_str").asText();
        }
        return "";
    }

    private boolean isCompleted(JsonNode status) {
        JsonNode statusObj = status.get("status");
        if (statusObj != null && statusObj.has("completed")) {
            return statusObj.get("completed").asBoolean();
        }
        return false;
    }

    /**
     * 从完成后的状态中提取错误信息。
     */
    private String extractErrorMessage(JsonNode status) {
        JsonNode messages = status.get("status").get("messages");
        if (messages != null && messages.isArray()) {
            for (JsonNode msg : messages) {
                if (msg.has("execution_error") && msg.get("execution_error").has("exception_message")) {
                    return msg.get("execution_error").get("exception_message").asText();
                }
            }
        }
        return "未知错误，请查看 ComfyUI 日志";
    }

    /**
     * 从输出结果中获取图片信息（filename, subfolder, type）。
     * 期望 outputs["9"] 为 SaveImage 节点的输出。
     */
    public ImageOutput getImageOutput(JsonNode status) {
        JsonNode outputs = status.get("outputs");
        if (outputs == null || !outputs.has("9")) {
            throw new IllegalStateException("未找到 SaveImage 节点输出 (expected outputs[9])");
        }

        JsonNode images = outputs.get("9").get("images");
        if (images == null || images.isEmpty()) {
            throw new IllegalStateException("SaveImage 节点无输出图片");
        }

        JsonNode image = images.get(0);
        return new ImageOutput(
                image.get("filename").asText(),
                image.has("subfolder") ? image.get("subfolder").asText() : "",
                image.has("type") ? image.get("type").asText() : "output"
        );
    }

    /**
     * 下载指定图片的原始字节流。
     */
    public byte[] downloadImage(String filename, String subfolder, String type) {
        StringBuilder url = new StringBuilder(baseUrl).append("/view?filename=")
                .append(filename)
                .append("&type=")
                .append(type);
        if (!subfolder.isBlank()) {
            url.append("&subfolder=").append(subfolder);
        }

        String fullUrl = url.toString();
        log.info("下载图片: url={}", fullUrl);

        try {
            byte[] result = restTemplate.getForObject(fullUrl, byte[].class);
            if (result == null || result.length == 0) {
                throw new IllegalStateException("下载的图片内容为空");
            }
            log.info("下载完成: {} bytes, 前4字节(hex)={}", result.length,
                    String.format("%02X %02X %02X %02X", result[0], result[1], result[2], result[3]));
            return result;
        } catch (HttpClientErrorException e) {
            log.error("下载图片失败 (HTTP {}): {}", e.getStatusCode(), e.getMessage());
            throw new ServiceException(500, "无法获取图片文件: " + e.getMessage());
        } catch (Exception e) {
            log.error("下载图片异常: ", e);
            throw new ServiceException(500, "图片下载失败: " + e.getMessage());
        }
    }

    /**
     * 验证 PNG 文件的头部签名（0x89 0x50 0x4E 0x47）。
     */
    public boolean validatePngHeader(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        // byte 是有符号的 (-128~127)，0x89=137 转 byte 为 -119，必须 & 0xFF 转无符号再比较
        return (data[0] & 0xFF) == 0x89
            && (data[1] & 0xFF) == 0x50
            && (data[2] & 0xFF) == 0x4E
            && (data[3] & 0xFF) == 0x47;
    }

    /**
     * 异常类定义
     */
    public static class ServiceException extends RuntimeException {
        private final int httpCode;

        public ServiceException(int httpCode, String message) {
            super(message);
            this.httpCode = httpCode;
        }

        public int getHttpCode() {
            return httpCode;
        }
    }

    public static class TimeoutException extends ServiceException {
        public TimeoutException(String message) {
            super(504, message);
        }
    }

    public static class ErrorException extends ServiceException {
        public ErrorException(String message) {
            super(500, message);
        }
    }

    public record PollResult(boolean finished, JsonNode status) {}

    public record ImageOutput(String filename, String subfolder, String type) {}
}
