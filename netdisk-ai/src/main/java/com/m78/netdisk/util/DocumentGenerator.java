package com.m78.netdisk.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文档文本提取工具类。纯本地实现，不依赖外部服务。
 * 文档生成功能已迁移到独立 Python 服务（端口 8001）。
 */
public final class DocumentGenerator {

    private DocumentGenerator() {}

    // ==================== 文本提取（RAG 文件阅读依赖） ====================

    /**
     * 从存储中读取文件流，提取纯文本。
     */
    public static String extractText(InputStream inputStream, String extension) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            return switch (extension) {
                case "md", "txt", "html", "csv", "json" -> new String(bytes, StandardCharsets.UTF_8);
                case "docx" -> extractDocxText(new java.io.ByteArrayInputStream(bytes));
                case "xlsx" -> extractXlsxText(new java.io.ByteArrayInputStream(bytes));
                default -> throw new IllegalArgumentException("不支持的类型: " + extension);
            };
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }
    }

    // ==================== Word 文本提取 ====================

    private static String extractDocxText(InputStream inputStream) {
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                if (sb.length() > 0) sb.append("\n");
                for (var run : para.getRuns()) {
                    if (run != null) sb.append(run.getText(0));
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("读取 Word 文档失败", e);
        }
    }

    // ==================== Excel 文本提取 ====================

    private static String extractXlsxText(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return "";
            StringBuilder sb = new StringBuilder();
            for (Row row : sheet) {
                if (sb.length() > 0) sb.append("\n");
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    if (i > 0) sb.append("\t");
                    sb.append(cell != null ? getStringValue(cell) : "");
                }
            }
            return sb.toString();
        } catch (IOException e) {
            throw new RuntimeException("读取 Excel 文档失败", e);
        }
    }

    private static String getStringValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA: return cell.getCellFormula();
            default: return "";
        }
    }
}
