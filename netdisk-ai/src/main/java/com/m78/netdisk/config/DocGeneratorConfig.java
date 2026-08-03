package com.m78.netdisk.config;

import com.m78.netdisk.common.config.DocGeneratorProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(DocGeneratorProperties.class)
public class DocGeneratorConfig {

    @Bean("docGeneratorRestTemplate")
    public RestTemplate docGeneratorRestTemplate(DocGeneratorProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(parseSeconds(properties.getConnectTimeout(), 3)));
        factory.setReadTimeout(Duration.ofSeconds(parseSeconds(properties.getReadTimeout(), 30)));
        return new RestTemplate(factory);
    }

    private static long parseSeconds(String value, long defaultSeconds) {
        if (value == null || value.isBlank()) return defaultSeconds;
        String num = value.replaceAll("[^0-9]", "");
        if (num.isEmpty()) return defaultSeconds;
        try {
            return Long.parseLong(num);
        } catch (NumberFormatException e) {
            return defaultSeconds;
        }
    }
}
