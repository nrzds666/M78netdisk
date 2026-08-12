package com.m78.netdisk.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "rag.service")
public class RagServiceProperties {

    /** 是否启用 RAG 服务。默认 false，部署时通过配置显式开启 */
    private boolean enabled = false;
    /** RAG 服务地址 */
    private String url = "http://localhost:8000";
    /** 查询时启用查询改写 */
    private boolean enableRewrite = true;
    /** 查询时启用混合检索 */
    private boolean enableHybrid = true;
    /** 查询时启用重排序 */
    private boolean enableRerank = true;
    /** 查询时启用上下文压缩 */
    private boolean enableCompress = true;
    /** 默认返回 Top-K 条结果 */
    private int topK = 3;
    /** 候选数倍数（为重排序保留更多候选，默认 4） */
    private int retrievalMultiplier = 4;
    /** 上下文窗口大小（压缩时保留相关句前后多少句，默认 1） */
    private int compressContextWindow = 1;
    /** 检索结果注入上下文的最低相关度阈值（低于此值的结果丢弃） */
    private double scoreThreshold = 0.3;
    /** 注入上下文的最大总字符数（超出截断） */
    private int contextMaxChars = 3000;
    /** 支持 RAG 索引的文件扩展名白名单 */
    private List<String> supportedExtensions = List.of(
            "pdf", "docx", "pptx", "txt", "md", "csv", "py", "js", "ts", "java",
            "cpp", "c", "h", "rs", "go", "rb", "php", "swift", "kt", "yaml", "yml",
            "json", "xml", "html", "css", "sql", "sh", "bat", "toml", "ini", "cfg"
    );
}
