package com.m78.netdisk.config;

import com.m78.netdisk.common.config.ComfyUIProperties;
import com.m78.netdisk.client.ComfyUIClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableConfigurationProperties(ComfyUIProperties.class)
public class ComfyUIConfig {

    @Bean("comfyRestTemplate")
    public RestTemplate comfyRestTemplate(ComfyUIProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        int connectTimeout = parseSeconds(properties.getConnectTimeout(), 5);
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));

        int readTimeout = parseSeconds(properties.getReadTimeout(), 120);
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));

        log.info("ComfyUI RestTemplate 配置: connectTimeout={}s, readTimeout={}s", connectTimeout, readTimeout);
        return new RestTemplate(factory);
    }

    @Bean("imageGenerationExecutor")
    public ExecutorService imageGenerationExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 4,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(16),
                r -> {
                    Thread t = new Thread(r, "image-gen-worker");
                    t.setDaemon(true);
                    return t;
                }
        );
        log.info("图片生成线程池初始化: core=2, max=4, queue=16");
        return executor;
    }

    @Bean
    public ComfyUIClient comfyUIClient(@Qualifier("comfyRestTemplate") RestTemplate restTemplate,
                                      ComfyUIProperties properties,
                                      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new ComfyUIClient(restTemplate, properties, objectMapper);
    }

    private static int parseSeconds(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        String num = value.replaceAll("[^0-9]", "");
        if (num.isEmpty()) return defaultValue;
        try {
            int seconds = Integer.parseInt(num);
            return seconds > 0 ? seconds : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("无效的时间值，使用默认值 {}: {}", value, defaultValue);
            return defaultValue;
        }
    }
}
