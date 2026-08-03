package com.m78.netdisk.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "netdisk.comfyui")
public class ComfyUIProperties {

    /** ComfyUI 服务地址 */
    private String baseUrl = "http://127.0.0.1:8188";

    /** 连接超时 (秒) */
    private String connectTimeout = "5s";

    /** 读取/轮询超时 (秒) - SDXL Turbo 生成约需 5-15s */
    private String readTimeout = "120s";

    /** 初始轮询间隔 (毫秒) */
    private long pollIntervalMs = 500;

    /** 最大轮询间隔 (毫秒) - 指数退避上限 */
    private long pollMaxIntervalMs = 2000;

    /** 最大轮询总超时 (秒) */
    private long pollTimeout = 120;

    /** 默认宽度 */
    private int defaultWidth = 512;

    /** 默认高度 */
    private int defaultHeight = 512;

    /** 默认采样步数 (SDXL Turbo 推荐 1-4) */
    private int defaultSteps = 4;

    /** 默认 CFG 值 */
    private double defaultCfg = 1.0;

    /** 默认采样器 */
    private String defaultSampler = "euler";

    /** 默认调度器 */
    private String defaultScheduler = "karras";

    /** SDXL Turbo 模型文件名 */
    private String modelFileName = "sd_xl_turbo_1.0_fp16.safetensors";

    /** 默认负面提示词 */
    private String defaultNegativePrompt = "blurry, low quality, distorted, ugly, bad anatomy, watermark, text, jpeg artifacts";
}
