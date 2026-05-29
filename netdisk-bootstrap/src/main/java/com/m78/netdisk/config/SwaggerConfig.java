package com.m78.netdisk.config;

import org.springframework.context.annotation.Profile;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Profile("dev")
public class SwaggerConfig {

    @Bean
    public OpenAPI netdiskOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("M78 网盘 API")
                        .description("M78 NetDisk - 个人云存储系统接口文档")
                        .version("v1.0.0")
                        .contact(new Contact().name("M78 Team"))
                        .license(new License().name("Apache 2.0")));
    }

    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("用户模块")
                .pathsToMatch("/api/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi fileApi() {
        return GroupedOpenApi.builder()
                .group("文件模块")
                .pathsToMatch("/api/files/**")
                .build();
    }

    @Bean
    public GroupedOpenApi shareApi() {
        return GroupedOpenApi.builder()
                .group("分享模块")
                .pathsToMatch("/api/shares/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("管理模块")
                .pathsToMatch("/api/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi calendarApi() {
        return GroupedOpenApi.builder()
                .group("日历模块")
                .pathsToMatch("/api/calendar/**")
                .build();
    }

    @Bean
    public GroupedOpenApi vaultApi() {
        return GroupedOpenApi.builder()
                .group("机密文件箱模块")
                .pathsToMatch("/api/vault/**")
                .build();
    }

    @Bean
    public GroupedOpenApi albumApi() {
        return GroupedOpenApi.builder()
                .group("相册模块")
                .pathsToMatch("/api/albums/**")
                .build();
    }
}