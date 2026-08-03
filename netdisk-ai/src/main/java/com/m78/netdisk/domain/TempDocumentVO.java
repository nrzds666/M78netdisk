package com.m78.netdisk.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TempDocumentVO {

    /** 临时文件 ID（UUID） */
    private String tempFileId;
    /** 文件名（含扩展名） */
    private String fileName;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 文件格式（md/docx/xlsx 等） */
    private String fileType;
    /** 当前第几轮修改（0 = 首次生成） */
    private int round;

    /** 磁盘上的临时文件路径（不暴露给前端） */
    @JsonIgnore
    private String filePath;

    public TempDocumentVO(String tempFileId, String fileName, Long fileSize, String fileType) {
        this.tempFileId = tempFileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.round = 0;
    }
}
