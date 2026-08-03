package com.m78.netdisk.controller;

import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.domain.ImageGenRequest;
import com.m78.netdisk.domain.ImageGenResponse;
import com.m78.netdisk.service.ImageGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图片生成接口 - 调用 ComfyUI (SDXL Turbo) 生成图片。
 */
@Slf4j
@Tag(name = "图片生成", description = "基于 SDXL Turbo 的图片生成功能")
@RestController
@RequestMapping("/api/chat")
@AllArgsConstructor
public class ImageGenerationController {

    private final ImageGenerationService imageGenerationService;

    @Operation(summary = "生成图片", description = "根据提示词使用 SDXL Turbo 生成图片，并保存到网盘 AI 生成文件夹")
    @PostMapping("/generate-image")
    public R<ImageGenResponse> generateImage(@RequestBody ImageGenRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }

        try {
            ImageGenResponse response = imageGenerationService.generate(
                request.getPrompt(),
                request.getNegativePrompt(),
                request.getWidth(),
                request.getHeight(),
                userId
            );
            return R.ok(response);
        } catch (Exception e) {
            log.error("图片生成失败: error={}", e.getMessage());
            return R.fail(500, e.getMessage());
        }
    }
}