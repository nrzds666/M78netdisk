package com.m78.netdisk.vault.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class VaultConfig implements WebMvcConfigurer {

    private final VaultAccessInterceptor vaultAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(vaultAccessInterceptor)
                .addPathPatterns("/api/vault/files/**", "/api/vault/lock");
    }
}
