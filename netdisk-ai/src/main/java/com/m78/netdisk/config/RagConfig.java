package com.m78.netdisk.config;

import com.m78.netdisk.common.client.RagClient;
import com.m78.netdisk.common.config.RagServiceProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(RagServiceProperties.class)
public class RagConfig {

    @Bean("ragRestTemplate")
    public RestTemplate ragRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return new RestTemplate(factory);
    }

    @Bean
    public RagClient ragClient(@Qualifier("ragRestTemplate") RestTemplate restTemplate,
                               com.m78.netdisk.common.storage.StorageService storageService,
                               RagServiceProperties properties) {
        return new RagClient(restTemplate, storageService, properties);
    }
}
