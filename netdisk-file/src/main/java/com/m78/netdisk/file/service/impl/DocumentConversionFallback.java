package com.m78.netdisk.file.service.impl;

import com.m78.netdisk.file.service.DocumentConversionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;

/**
 * jodconverter 未启用时的降级实现，仅提供文件类型检测，
 * 转换操作会抛出异常（由调用方捕获并回退）。
 */
@Slf4j
@Service
@ConditionalOnMissingBean(DocumentConversionServiceImpl.class)
public class DocumentConversionFallback implements DocumentConversionService {

    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    @Override
    public boolean isOfficeFile(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        String ext = getFileExtension(fileName);
        return ext != null && OFFICE_EXTENSIONS.contains(ext);
    }

    @Override
    public String getFileExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1).toLowerCase();
    }

    @Override
    public byte[] convertToPdf(InputStream inputStream, String fileName) {
        throw new UnsupportedOperationException(
                "Office 文档转换未启用，请安装 LibreOffice 并设置 jodconverter.local.enabled=true");
    }
}
