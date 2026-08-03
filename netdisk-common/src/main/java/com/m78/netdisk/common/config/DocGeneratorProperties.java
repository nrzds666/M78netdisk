package com.m78.netdisk.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "doc-generator")
public class DocGeneratorProperties {

    /** 文档生成服务地址 */
    private String url = "http://localhost:8001";
    /** 连接超时 */
    private String connectTimeout = "3s";
    /** 读取超时 */
    private String readTimeout = "30s";
}
