package com.m78.netdisk.common.config;

import com.m78.netdisk.common.util.FFmpegUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FFmpeg 相关 Bean 配置。
 * 通过 netdisk.storage.ffmpeg-path 配置 FFmpeg 可执行文件路径。
 */
@Configuration
public class FFmpegConfig {

    @Value("${netdisk.storage.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Bean
    public FFmpegUtil ffmpegUtil() {
        return new FFmpegUtil(ffmpegPath, 10);
    }
}
