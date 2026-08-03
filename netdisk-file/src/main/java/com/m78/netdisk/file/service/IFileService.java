package com.m78.netdisk.file.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.file.domain.dto.*;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.domain.vo.MediaProgressVO;
import com.m78.netdisk.file.domain.vo.UploadTaskVO;
import com.m78.netdisk.file.domain.vo.ZipResult;

import java.util.List;

public interface IFileService {

    IPage<ItemVO> listItems(Long ownerId, Long parentId, Integer page, Integer size);

    /**
     * 带筛选条件的文件列表查询
     * @param query 文件名搜索关键词
     * @param mimePrefix MIME类型前缀过滤（如 image/, video/）
     * @param dateFrom 开始日期 (yyyy-MM-dd)
     * @param dateTo 结束日期 (yyyy-MM-dd)
     */
    IPage<ItemVO> listItems(Long ownerId, Long parentId, Integer page, Integer size,
                            String query, String mimePrefix, String dateFrom, String dateTo);

    /**
     * 带完整筛选条件的文件列表查询（支持多种 MIME 类型精确匹配）
     */
    IPage<ItemVO> listItems(Long ownerId, Long parentId, Integer page, Integer size,
                            String query, String mimePrefix,
                            java.util.List<String> mimeTypes, String excludePrefix,
                            String dateFrom, String dateTo);

    ItemVO createFile(Long ownerId, Long parentId, String fileName, Long fileSize, String mimeType, String storageKey);

    ItemVO createFolder(Long ownerId, CreateFolderDTO dto);

    ItemVO rename(Long ownerId, RenameItemDTO dto);

    void move(Long ownerId, MoveItemsDTO dto);

    void deleteToTrash(Long ownerId, List<Long> itemIds);

    void restoreFromTrash(Long ownerId, List<Long> itemIds);

    void permanentlyDelete(Long ownerId, List<Long> itemIds);

    IPage<ItemVO> listTrash(Long ownerId, Integer page, Integer size);

    UploadTaskVO initUpload(Long ownerId, InitUploadDTO dto);

    void confirmChunk(Long ownerId, Long taskId, Integer chunkIndex, String storageKey, String etag, Integer size);

    /**
     * 上传单个分片
     * @param ownerId   用户ID
     * @param taskId    上传任务ID
     * @param chunkIndex 分片序号（从0开始）
     * @param file      分片文件
     */
    void uploadChunk(Long ownerId, Long taskId, Integer chunkIndex,
                     org.springframework.web.multipart.MultipartFile file);

    void cancelUpload(Long ownerId, Long taskId);

    void pauseUpload(Long ownerId, Long taskId);


    UploadTaskVO completeUpload(Long ownerId, Long taskId);

    UploadTaskVO getUploadStatus(Long ownerId, Long taskId);

    /**
     * 列出当前用户所有未完成的上传任务（uploading / merging）
     */
    List<UploadTaskVO> listUnfinishedTasks(Long ownerId);

    /**
     * 获取已完成的分片索引列表（用于断点续传）
     */
    List<Integer> getCompletedChunks(Long ownerId, Long taskId);

    /**
     * 删除上传任务及所有分片（存储 + DB）
     */
    void deleteUploadTask(Long ownerId, Long taskId);

    /**
     * 获取文件下载流信息
     * @param ownerId 用户ID
     * @param itemId  文件ID
     * @return FileDownloadVO（包含 storageKey, mimeType, fileName, fileSize）
     */
    FileDownloadVO getDownloadInfo(Long ownerId, Long itemId);

    /**
     * 获取文件预览流信息（同下载，但前端用 inline 展示）
     */
    FileDownloadVO getPreviewInfo(Long ownerId, Long itemId);

    /**
     * 获取文件夹下所有文件，打包为 ZIP 流
     * @param ownerId  用户ID
     * @param folderId 文件夹ID
     * @return ZipResult（包含 ZipOutputStream 封装信息）
     */
    ZipResult getFolderZip(Long ownerId, Long folderId);

    /**
     * 批量下载：将多个文件/文件夹打包成 ZIP
     * @param ownerId  用户ID
     * @param itemIds  文件/文件夹ID列表
     * @return ZipResult（流式 ZIP 输出）
     */
    ZipResult getBatchZip(Long ownerId, List<Long> itemIds);

    /**
     * 获取媒体文件播放进度
     * @param ownerId 用户ID
     * @param itemId  媒体文件ID
     * @return MediaProgressVO
     */
    MediaProgressVO getProgress(Long userId, Long itemId);

    /**
     * 保存媒体文件播放进度
     * @param ownerId 用户ID
     * @param itemId  媒体文件ID
     * @param dto     进度信息
     * @return MediaProgressVO
     */
    MediaProgressVO saveProgress(Long userId, Long itemId, SaveProgressDTO dto);

    List<ItemVO> listRecentItems(Long userId, Integer days);

    List<ItemVO> listRecentSaves(Long userId, Integer days);
}