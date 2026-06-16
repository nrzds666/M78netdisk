package com.m78.netdisk.file.service;

import java.io.InputStream;

/**
 * Office 文档转换服务。
 * 将 Word/Excel/PPT 等文档实时转换为 PDF，用于浏览器内联预览。
 */
public interface DocumentConversionService {

    /**
     * 判断文件名是否为 Office 文档（doc/docx/xls/xlsx/ppt/pptx）
     */
    boolean isOfficeFile(String fileName);

    /**
     * 从文件名中提取扩展名（不含点号，小写）
     */
    String getFileExtension(String fileName);

    /**
     * 将 Office 文档流转换为 PDF 字节流
     *
     * @param inputStream 源文档流
     * @param fileName    源文件名（用于检测格式）
     * @return PDF 字节数组
     */
    byte[] convertToPdf(InputStream inputStream, String fileName);
}
