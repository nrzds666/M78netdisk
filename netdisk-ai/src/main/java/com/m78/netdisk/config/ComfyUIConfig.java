package com.m78.netdisk.config;

import com.m78.netdisk.client.ComfyUIClient;
import com.m78.netdisk.common.config.ComfyUIProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Objects;

@Slf4j
@Configuration
@EnableConfigurationProperties(ComfyUIProperties.class)
public class ComfyUIConfig {

    @Bean("comfyRestTemplate")
    public RestTemplate comfyRestTemplate(ComfyUIProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 解析连接超时
        int connectTimeout = parseSeconds(properties.getConnectTimeout(), 5);
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeout));

        // 解析读取超时（轮询等待时间）
        int readTimeout = parseSeconds(properties.getReadTimeout(), 120);
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));

        log.info("ComfyUI RestTemplate 配置: connectTimeout={}s, readTimeout={}s", connectTimeout, readTimeout);
        return new RestTemplate(factory);
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

    @Bean
    public ComfyUIClient comfyUIClient(@Qualifier("comfyRestTemplate") RestTemplate restTemplate,
                                      ComfyUIProperties properties,
                                      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new ComfyUIClient(restTemplate, properties, objectMapper);
    }
}
