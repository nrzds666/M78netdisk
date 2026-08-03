package com.m78.netdisk.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "图片生成请求")
public class ImageGenRequest {

    @Schema(description = "图片描述提示词（必填）", example = "一只在月光下奔跑的白色狐狸，赛博朋克风格", required = true)
    private String prompt;

    @Schema(description = "负面提示词（可选）", example = "blurry, low quality")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String negativePrompt;

    @Schema(description = "图片宽度（像素），默认 512", example = "512")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer width;

    @Schema(description = "图片高度（像素），默认 512", example = "512")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer height;
}
