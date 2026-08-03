package com.m78.netdisk.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 服务启动时清空 Redis 中所有 token，强制所有用户重新登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupRunner implements ApplicationRunner {

    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_PATTERN = "token:*";

    @Override
    public void run(ApplicationArguments args) {
        try {
            Set<String> keys = redisTemplate.keys(TOKEN_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Cleared {} token keys from Redis on startup", keys.size());
            } else {
                log.info("No token keys to clear on startup");
            }
        } catch (Exception e) {
            log.warn("Failed to clear token keys on startup (Redis may not be ready yet): {}", e.getMessage());
        }
    }
}
