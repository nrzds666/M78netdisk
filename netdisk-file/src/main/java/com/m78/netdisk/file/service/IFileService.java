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

    void cancelUpload(Long ownerId, Long taskId);

    UploadTaskVO completeUpload(Long ownerId, Long taskId);

    UploadTaskVO getUploadStatus(Long ownerId, Long taskId);

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
}