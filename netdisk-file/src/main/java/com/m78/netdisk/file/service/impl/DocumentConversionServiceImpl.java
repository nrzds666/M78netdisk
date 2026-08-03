package com.m78.netdisk.file.service.impl;

import com.m78.netdisk.file.service.DocumentConversionService;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.DocumentConverter;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Set;

@Slf4j
@Service
@ConditionalOnProperty(name = "jodconverter.local.enabled", havingValue = "true")
public class DocumentConversionServiceImpl implements DocumentConversionService {

    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    );

    private final DocumentConverter documentConverter;

    public DocumentConversionServiceImpl(DocumentConverter documentConverter) {
        this.documentConverter = documentConverter;
    }

    @PostConstruct
    public void init() {
        log.info("Office 文档转换服务已启用（jodconverter）");
    }

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
        String ext = getFileExtension(fileName);
        if (ext == null) {
            throw new IllegalArgumentException("无法识别文件格式: " + fileName);
        }
        try {
            var format = DefaultDocumentFormatRegistry.getFormatByExtension(ext);
            if (format == null) {
                throw new IllegalArgumentException("不支持的文档格式: " + ext);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
            documentConverter.convert(inputStream)
                    .as(format)
                    .to(baos)
                    .as(DefaultDocumentFormatRegistry.PDF)
                    .execute();
            log.debug("文档转换成功: {} → PDF ({} bytes)", fileName, baos.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("文档转换失败: {}", fileName, e);
            throw new RuntimeException("文档转换失败: " + fileName, e);
        }
    }
}
