package com.m78.netdisk.file.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.InputStream;
import java.util.List;

/**
 * ZIP 打包结果
 */
@Data
@Builder
@AllArgsConstructor
public class ZipResult {
    /** ZIP 输出流，由调用方关闭 */
    private InputStream inputStream;
    /** ZIP 总大小（字节），用 -1 表示未知（流式） */
    private long totalSize;
    /** ZIP 中的文件条目列表（用于日志/进度） */
    private List<String> entries;
    /** ZIP 文件名（如 \"我的文件夹.zip\"） */
    private String zipFileName;
    /** ZIP 打包异常（线程内捕获后传播给调用方） */
    private volatile Throwable zipError;
}
