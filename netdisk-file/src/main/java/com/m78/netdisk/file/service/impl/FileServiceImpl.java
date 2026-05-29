package com.m78.netdisk.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.dto.*;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.po.ItemVersion;
import com.m78.netdisk.file.domain.po.MediaProgress;
import com.m78.netdisk.file.domain.po.UploadChunk;
import com.m78.netdisk.file.domain.po.UploadTask;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.domain.vo.MediaProgressVO;
import com.m78.netdisk.file.domain.vo.UploadTaskVO;
import com.m78.netdisk.file.domain.vo.ZipResult;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.file.mapper.ItemVersionMapper;
import com.m78.netdisk.file.mapper.MediaProgressMapper;
import com.m78.netdisk.file.mapper.UploadChunkMapper;
import com.m78.netdisk.file.mapper.UploadTaskMapper;
import com.m78.netdisk.file.service.IFileService;
import com.m78.netdisk.user.domain.po.User;
import com.m78.netdisk.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements IFileService {

    private final ItemMapper itemMapper;
    private final ItemVersionMapper itemVersionMapper;
    private final UploadTaskMapper uploadTaskMapper;
    private final UploadChunkMapper uploadChunkMapper;
    private final StorageService storageService;
    private final UserMapper userMapper;
    private final MediaProgressMapper mediaProgressMapper;

    // ==================== 文件/文件夹 CRUD ====================

    @Override
    public IPage<ItemVO> listItems(Long ownerId, Long parentId, Integer pageNum, Integer size) {
        Page<Item> page = new Page<>(pageNum, Math.min(size, 100));
        IPage<Item> itemPage;
        if (parentId == null || parentId == 0) {
            itemPage = itemMapper.selectRootItems(page, ownerId);
        } else {
            itemPage = itemMapper.selectChildren(page, ownerId, parentId);
        }
        return itemPage.convert(this::toItemVO);
    }

    @Override
    @Transactional
    public ItemVO createFile(Long ownerId, Long parentId, String fileName,
                              Long fileSize, String mimeType, String storageKey) {
        if (fileName == null || fileName.trim().isEmpty() ||
            fileName.contains("/") || fileName.contains("\\") ||
            fileName.contains("..") || fileName.contains("\0")) {
            throw new BizException("文件名包含非法字符");
        }
        Long pid = parentId != null && parentId > 0 ? parentId : null;
        if (itemMapper.countByName(ownerId, pid, fileName) > 0) {
            throw new BizException("该目录下已存在同名文件");
        }

        String path = buildPath(ownerId, pid, fileName);

        Item item = new Item()
                .setOwnerId(ownerId)
                .setParentId(pid)
                .setName(fileName)
                .setIsDirectory(false)
                .setSize(fileSize)
                .setMimeType(mimeType)
                .setStorageKey(storageKey)
                .setPath(path)
                .setVersion(1);

        itemMapper.insert(item);

        ItemVersion version = new ItemVersion()
                .setItemId(item.getId())
                .setVersion(1)
                .setSize(fileSize)
                .setStorageKey(storageKey)
                .setCreatedBy(ownerId);
        itemVersionMapper.insert(version);

        // 原子增用量，超配额则回滚
        if (userMapper.tryAddUsedBytes(ownerId, fileSize) == 0) {
            itemMapper.deleteById(item.getId());
            itemVersionMapper.deleteById(version.getId());
            throw new BizException("存储空间不足，无法上传文件");
        }

        log.info("文件上传完成: userId={}, fileName={}, size={}, itemId={}",
                ownerId, fileName, fileSize, item.getId());
        return toItemVO(item);
    }

    @Override
    @Transactional
    public ItemVO createFolder(Long ownerId, CreateFolderDTO dto) {
        String name = dto.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new BizException("文件夹名称不能为空");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..") || name.contains("\0")) {
            throw new BizException("文件夹名称包含非法字符");
        }
        Long parentId = dto.getParentId() == null || dto.getParentId() == 0 ? null : dto.getParentId();
        if (itemMapper.countByName(ownerId, parentId, dto.getName()) > 0) {
            throw new BizException("同名文件夹已存在");
        }

        String path = buildPath(ownerId, parentId, dto.getName());

        Item item = new Item()
                .setOwnerId(ownerId)
                .setParentId(parentId)
                .setName(dto.getName())
                .setIsDirectory(true)
                .setSize(0L)
                .setPath(path)
                .setVersion(1);

        itemMapper.insert(item);
        return toItemVO(item);
    }

    @Override
    @Transactional
    public ItemVO rename(Long ownerId, RenameItemDTO dto) {
        Item item = itemMapper.selectById(dto.getItemId());
        validateOwner(item, ownerId);

        if (itemMapper.countByName(ownerId, item.getParentId(), dto.getNewName()) > 0) {
            throw new BizException("该名称已存在");
        }

        String oldPath = item.getPath();
        String newName = dto.getNewName();
        if (newName == null || newName.trim().isEmpty() ||
            newName.contains("/") || newName.contains("\\") ||
            newName.contains("..") || newName.contains("\0")) {
            throw new BizException("文件名包含非法字符");
        }
        item.setName(newName);
        item.setPath(rebuildPath(item));
        itemMapper.updateById(item);

        // 如果是文件夹，级联更新子文件的 path
        if (item.getIsDirectory()) {
            String oldPathPrefix = oldPath + "/";
            List<Item> children = itemMapper.selectList(
                    new LambdaQueryWrapper<Item>()
                            .eq(Item::getOwnerId, ownerId)
                            .apply("path LIKE {0} ESCAPE '!'", escapeLike(oldPathPrefix)));
            for (Item child : children) {
                child.setPath(item.getPath() + child.getPath().substring(oldPath.length()));
                itemMapper.updateById(child);
            }
        }

        return toItemVO(item);
    }

    @Override
    @Transactional
    public void move(Long ownerId, MoveItemsDTO dto) {
        if (dto.getItemIds() == null || dto.getItemIds().isEmpty()) return;

        Long newParentId = dto.getTargetParentId() != null && dto.getTargetParentId() > 0
                ? dto.getTargetParentId() : null;

        // 预查目标父目录路径（所有 item 移到同一目录，只需查一次）
        String parentPath = null;
        if (newParentId != null) {
            Item parent = itemMapper.selectById(newParentId);
            if (parent == null) {
                throw new BizException("目标目录不存在");
            }
            if (!parent.getIsDirectory()) {
                throw new BizException("目标不是文件夹");
            }
            parentPath = parent.getPath();
        }

        for (Long itemId : dto.getItemIds()) {
            Item item = itemMapper.selectById(itemId);
            if (item == null || !item.getOwnerId().equals(ownerId)) {
                log.warn("跳过无权或不存在文件: itemId={}", itemId);
                continue;
            }

            // 检查循环引用：目标目录不能是自身或其后代
            if (newParentId != null && (newParentId.equals(item.getId()) || isDescendant(newParentId, item.getId()))) {
                throw new BizException("不能将文件夹移动到自身或子文件夹中: " + item.getName());
            }

            // 校验目标目录下重名
            if (itemMapper.countByName(ownerId, newParentId, item.getName()) > 0) {
                throw new BizException("目标目录已存在同名文件/文件夹: " + item.getName());
            }

            String oldPath = item.getPath();
            item.setParentId(newParentId);
            item.setPath(parentPath != null ? parentPath + "/" + item.getName() : "/" + item.getName());
            itemMapper.updateById(item);

            // 如果是文件夹，级联更新子文件的 path
            if (item.getIsDirectory()) {
                String oldPrefix = oldPath + "/";
                String newPrefix = item.getPath() + "/";
                List<Item> children = itemMapper.selectList(
                        new LambdaQueryWrapper<Item>()
                                .eq(Item::getOwnerId, ownerId)
                                .apply("path LIKE {0} ESCAPE '!'", escapeLike(oldPrefix)));
                for (Item child : children) {
                    child.setPath(newPrefix + child.getPath().substring(oldPrefix.length()));
                    itemMapper.updateById(child);
                }
            }
        }
    }

    @Override
    @Transactional
    public void deleteToTrash(Long ownerId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        for (Long id : itemIds) {
            Item item = itemMapper.selectById(id);
            if (item == null || !item.getOwnerId().equals(ownerId)) continue;

            int affected = itemMapper.softDelete(id, ownerId);
            if (affected == 0) {
                log.warn("软删除失败: id={}, ownerId={}", id, ownerId);
                continue;
            }

            // 如果是文件夹，递归软删除子文件
            if (item.getIsDirectory()) {
                List<Item> children = itemMapper.selectList(
                        new LambdaQueryWrapper<Item>()
                                .eq(Item::getParentId, id)
                                .eq(Item::getOwnerId, ownerId));
                for (Item child : children) {
                    List<Long> childIds = new ArrayList<>();
                    childIds.add(child.getId());
                    deleteToTrash(ownerId, childIds);
                }
            }
        }
    }

    @Override
    @Transactional
    public void restoreFromTrash(Long ownerId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        for (Long id : itemIds) {
            Item item = itemMapper.selectById(id);
            if (item == null || !item.getOwnerId().equals(ownerId)) continue;

            // 检查目标目录是否存在同名文件/文件夹
            if (itemMapper.countByName(ownerId, item.getParentId(), item.getName()) > 0) {
                throw new BizException("目标目录已存在同名文件/文件夹，请先处理冲突: " + item.getName());
            }

            int affected = itemMapper.restore(id, ownerId);
            if (affected == 0) {
                log.warn("恢复失败: id={}, ownerId={}", id, ownerId);
                continue;
            }

            // 如果是文件夹，递归恢复子文件
            if (item.getIsDirectory()) {
                List<Item> children = itemMapper.selectList(
                        new LambdaQueryWrapper<Item>()
                                .eq(Item::getParentId, id)
                                .eq(Item::getOwnerId, ownerId));
                for (Item child : children) {
                    List<Long> childIds = new ArrayList<>();
                    childIds.add(child.getId());
                    restoreFromTrash(ownerId, childIds);
                }
            }
        }
    }

    @Override
    @Transactional
    public void permanentlyDelete(Long ownerId, List<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return;
        for (Long id : itemIds) {
            Item item = itemMapper.selectById(id);
            if (item == null || !item.getOwnerId().equals(ownerId)) continue;

            // 文件不在回收站中，无法永久删除
            if (item.getIsDeleted() == null || !item.getIsDeleted()) {
                throw new BizException("文件不在回收站中，无法永久删除: " + item.getName());
            }

            // 如果是目录，先递归删除子文件/子目录
            if (item.getIsDirectory()) {
                List<Item> children = itemMapper.selectList(
                        new LambdaQueryWrapper<Item>()
                                .eq(Item::getParentId, id)
                                .eq(Item::getOwnerId, ownerId));
                for (Item child : children) {
                    List<Long> childIds = new ArrayList<>();
                    childIds.add(child.getId());
                    permanentlyDelete(ownerId, childIds);
                }
            }

            // 先删除 DB 记录
            itemMapper.deleteById(id);

            // 再清理磁盘存储（DB 删除后即使存储删除失败也没有孤儿记录）
            // 清理文件版本的存储
            List<ItemVersion> versions = itemVersionMapper.selectList(
                    new LambdaQueryWrapper<ItemVersion>().eq(ItemVersion::getItemId, id));
            for (ItemVersion v : versions) {
                storageService.delete(v.getStorageKey());
            }
            itemVersionMapper.delete(
                    new LambdaQueryWrapper<ItemVersion>().eq(ItemVersion::getItemId, id));

            // 清理文件本身的存储（非目录且非空 storageKey）
            if (!item.getIsDirectory() && item.getStorageKey() != null) {
                storageService.delete(item.getStorageKey());
            }

            log.info("永久删除: itemId={}, ownerId={}, name={}", id, ownerId, item.getName());

            // 原子减用量
            if (!item.getIsDirectory() && item.getSize() > 0) {
                userMapper.subtractUsedBytes(ownerId, item.getSize());
            }
        }
    }

    @Override
    public IPage<ItemVO> listTrash(Long ownerId, Integer pageNum, Integer size) {
        Page<Item> page = new Page<>(pageNum, Math.min(size, 100));
        return itemMapper.selectTrash(page, ownerId).convert(this::toItemVO);
    }

    @Override
    public List<ItemVO> listRecentItems(Long userId, Integer days) {
        return itemMapper.selectRecentItems(userId, days)
                .stream()
                .map(this::toItemVO)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public List<ItemVO> listRecentSaves(Long userId, Integer days) {
        return itemMapper.selectRecentSaves(userId, days)
                .stream()
                .map(this::toItemVO)
                .collect(java.util.stream.Collectors.toList());
    }

    // ==================== 分片上传 ====================

    @Override
    @Transactional
    public UploadTaskVO initUpload(Long ownerId, InitUploadDTO dto) {
        if (dto.getFileSize() == null || dto.getFileSize() <= 0) {
            throw new BizException("文件大小必须大于0");
        }

        // 预先检查配额（快速失败，避免创建无用任务）
        User user = userMapper.selectById(ownerId);
        if (user != null && user.getQuotaBytes() - user.getUsedBytes() < dto.getFileSize()) {
            throw new BizException("存储空间不足，无法初始化上传");
        }

        Long parentId = dto.getParentId() != null && dto.getParentId() > 0 ? dto.getParentId() : null;
        if (itemMapper.countByName(ownerId, parentId, dto.getFileName()) > 0) {
            throw new BizException("该目录下已存在同名文件");
        }

        int chunkSize = dto.getChunkSize() != null ? dto.getChunkSize() : 5242880;
        int totalChunks = (int) Math.ceil((double) dto.getFileSize() / chunkSize);
        String storagePrefix = "uploads/" + UUID.randomUUID().toString().replace("-", "");

        UploadTask task = new UploadTask()
                .setOwnerId(ownerId)
                .setParentId(parentId)
                .setFileName(dto.getFileName())
                .setFileSize(dto.getFileSize())
                .setMimeType(dto.getMimeType())
                .setChunkSize(chunkSize)
                .setTotalChunks(totalChunks)
                .setReceivedChunks(0)
                .setStatus("pending")
                .setStoragePrefix(storagePrefix)
                .setExpiresAt(LocalDateTime.now().plusHours(24));

        uploadTaskMapper.insert(task);
        return toUploadTaskVO(task);
    }

    @Override
    @Transactional
    public void confirmChunk(Long ownerId, Long taskId, Integer chunkIndex,
                              String storageKey, String etag, Integer size) {
        UploadTask task = uploadTaskMapper.selectById(taskId);
        if (task == null || !task.getOwnerId().equals(ownerId)) {
            throw new BizException("上传任务不存在");
        }
        if (!"uploading".equals(task.getStatus()) && !"pending".equals(task.getStatus())) {
            throw new BizException("上传任务状态异常: " + task.getStatus());
        }
        if (chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            throw new BizException("分片序号不合法");
        }

        if ("pending".equals(task.getStatus())) {
            task.setStatus("uploading");
            uploadTaskMapper.updateById(task);
        }

        // 尝试插入分片记录（利用 UNIQUE 约束实现幂等）
        try {
            UploadChunk chunk = new UploadChunk()
                    .setTaskId(taskId)
                    .setChunkIndex(chunkIndex)
                    .setSize(size != null ? size : 0)
                    .setEtag(etag)
                    .setStorageKey(storageKey);
            uploadChunkMapper.insert(chunk);

            // 原子递增已接收分片数
            uploadTaskMapper.incrementReceivedChunks(taskId);
        } catch (DuplicateKeyException e) {
            log.debug("分片已存在（幂等），跳过: taskId={}, chunkIndex={}", taskId, chunkIndex);
        }
    }

    @Override
    @Transactional
    public void cancelUpload(Long ownerId, Long taskId) {
        UploadTask task = uploadTaskMapper.selectById(taskId);
        if (task == null || !task.getOwnerId().equals(ownerId)) {
            throw new BizException("上传任务不存在");
        }

        // 清理分片物理存储
        List<UploadChunk> chunks = uploadChunkMapper.selectList(
                new LambdaQueryWrapper<UploadChunk>().eq(UploadChunk::getTaskId, taskId));
        for (UploadChunk chunk : chunks) {
            if (chunk.getStorageKey() != null) {
                storageService.delete(chunk.getStorageKey());
            }
        }
        uploadChunkMapper.delete(
                new LambdaQueryWrapper<UploadChunk>().eq(UploadChunk::getTaskId, taskId));

        task.setStatus("canceled");
        uploadTaskMapper.updateById(task);
        log.info("上传任务已取消并清理存储: taskId={}", taskId);
    }

    @Override
    @Transactional
    public UploadTaskVO completeUpload(Long ownerId, Long taskId) {
        UploadTask task = uploadTaskMapper.selectById(taskId);
        if (task == null || !task.getOwnerId().equals(ownerId)) {
            throw new BizException("上传任务不存在");
        }
        if (task.getReceivedChunks() < task.getTotalChunks()) {
            throw new BizException("分片尚未全部上传完成: " +
                    task.getReceivedChunks() + "/" + task.getTotalChunks());
        }

        String path = buildPath(ownerId, task.getParentId(), task.getFileName());

        // 合并所有分片为一个文件
        List<UploadChunk> chunks = uploadChunkMapper.selectList(
                new LambdaQueryWrapper<UploadChunk>()
                        .eq(UploadChunk::getTaskId, taskId)
                        .orderByAsc(UploadChunk::getChunkIndex));
        String mergedStorageKey = mergeChunks(chunks, task.getFileName());

        Item item = new Item()
                .setOwnerId(ownerId)
                .setParentId(task.getParentId())
                .setName(task.getFileName())
                .setIsDirectory(false)
                .setSize(task.getFileSize())
                .setMimeType(task.getMimeType())
                .setStorageKey(mergedStorageKey)
                .setPath(path)
                .setVersion(1);


        itemMapper.insert(item);

        ItemVersion version = new ItemVersion()
                .setItemId(item.getId())
                .setVersion(1)
                .setSize(task.getFileSize())
                .setStorageKey(mergedStorageKey)
                .setCreatedBy(ownerId);
        itemVersionMapper.insert(version);

        // 原子增用量，超配额则回滚
        if (userMapper.tryAddUsedBytes(ownerId, task.getFileSize()) == 0) {
            itemMapper.deleteById(item.getId());
            itemVersionMapper.deleteById(version.getId());
            throw new BizException("存储空间不足，无法完成上传");
        }

        task.setStatus("completed");
        uploadTaskMapper.updateById(task);

        log.info("上传完成: userId={}, fileName={}, size={}, itemId={}",
                ownerId, task.getFileName(), task.getFileSize(), item.getId());

        return toUploadTaskVO(task);
    }

    @Override
    public UploadTaskVO getUploadStatus(Long ownerId, Long taskId) {
        UploadTask task = uploadTaskMapper.selectById(taskId);
        if (task == null || !task.getOwnerId().equals(ownerId)) {
            throw new BizException("上传任务不存在");
        }
        return toUploadTaskVO(task);
    }

    // ==================== 下载/预览/ZIP ====================

    @Override
    public FileDownloadVO getDownloadInfo(Long ownerId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null || !item.getOwnerId().equals(ownerId)) {
            throw new BizException("文件不存在");
        }
        if (item.getIsDeleted() != null && item.getIsDeleted()) {
            throw new BizException("文件已被删除");
        }
        if (item.getIsDirectory()) {
            throw new BizException("不支持下载文件夹，请使用文件夹下载接口");
        }
        return FileDownloadVO.builder()
                .storageKey(item.getStorageKey())
                .fileName(item.getName())
                .mimeType(item.getMimeType() != null ? item.getMimeType() : "application/octet-stream")
                .fileSize(item.getSize())
                .build();
    }

    @Override
    public FileDownloadVO getPreviewInfo(Long ownerId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null || !item.getOwnerId().equals(ownerId)) {
            throw new BizException("文件不存在");
        }
        // Block preview for files saved from shares
        if (Boolean.TRUE.equals(item.getIsFromShare())) {
            throw new BizException("分享保存的文件不支持在线预览，请下载后查看");
        }
        return getDownloadInfo(ownerId, itemId);
    }

    @Override
    public ZipResult getFolderZip(Long ownerId, Long folderId) {
        Item folder = itemMapper.selectById(folderId);
        if (folder == null || !folder.getOwnerId().equals(ownerId)) {
            throw new BizException("文件夹不存在");
        }
        if (!folder.getIsDirectory()) {
            throw new BizException("只能下载文件夹");
        }

        // 递归收集所有非目录文件
        List<Item> allFiles = new ArrayList<>();
        collectFiles(folderId, allFiles);

        // 构建相对路径的条目名列表
        String basePath = folder.getPath();
        if (!basePath.endsWith("/")) basePath += "/";
        List<String> entries = new ArrayList<>();
        for (Item file : allFiles) {
            if (file.getIsDirectory()) continue;
            String entryName = file.getPath();
            if (entryName.startsWith(basePath)) {
                entryName = entryName.substring(basePath.length());
            }
            entries.add(entryName);
        }

        // 使用 Piped 流实现 ZIP 流式输出
        try {
            PipedInputStream in = new PipedInputStream(65536);
            PipedOutputStream out = new PipedOutputStream(in);

            String finalBasePath = basePath;
            ZipResult zipResult = new ZipResult(in, -1, entries, folder.getName() + ".zip", null);
            Thread zipThread = new Thread(() -> {
                try (ZipOutputStream zos = new ZipOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    for (Item file : allFiles) {
                        if (file.getIsDirectory()) continue;
                        String entryName = file.getPath();
                        if (entryName.startsWith(finalBasePath)) {
                            entryName = entryName.substring(finalBasePath.length());
                        }
                        // 防止 ZIP Slip 攻击
                        if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
                            throw new SecurityException("ZIP entry contains illegal path: " + entryName);
                        }
                        zos.putNextEntry(new ZipEntry(entryName));
                        if (file.getStorageKey() != null) {
                            try (InputStream is = storageService.getInputStream(file.getStorageKey())) {
                                int len;
                                while ((len = is.read(buf)) != -1) {
                                    zos.write(buf, 0, len);
                                }
                            }
                        }
                        zos.closeEntry();
                    }
                } catch (IOException e) {
                    log.error("ZIP打包失败", e);
                    zipResult.setZipError(e);
                } finally {
                    try { out.close(); } catch (IOException ignored) {}
                }
            });
            zipThread.setDaemon(true);
            zipThread.start();

            return zipResult;
        } catch (IOException e) {
            throw new RuntimeException("创建ZIP流失败", e);
        }
    }

    /**
     * 递归收集文件夹下的所有文件（含子目录）
     */
    private void collectFiles(Long parentId, List<Item> result) {
        List<Item> children = itemMapper.selectList(
                new LambdaQueryWrapper<Item>()
                        .eq(Item::getParentId, parentId)
                        .eq(Item::getIsDeleted, false));
        for (Item child : children) {
            result.add(child);
            if (child.getIsDirectory()) {
                collectFiles(child.getId(), result);
            }
        }
    }

    // ==================== 媒体进度追踪 ====================

    @Override
    public MediaProgressVO getProgress(Long userId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        validateOwner(item, userId);

        // Block progress tracking for files saved from shares
        if (Boolean.TRUE.equals(item.getIsFromShare())) {
            throw new BizException("分享保存的文件不支持在线预览");
        }

        String mimeType = item.getMimeType();
        if (mimeType == null || !(mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType.startsWith("image/"))) {
            throw new BizException("不是媒体文件");
        }

        MediaProgress mp = mediaProgressMapper.selectOne(
                new LambdaQueryWrapper<MediaProgress>()
                        .eq(MediaProgress::getUserId, userId)
                        .eq(MediaProgress::getItemId, itemId));
        if (mp == null) {
            return MediaProgressVO.builder()
                    .itemId(itemId)
                    .progressSeconds(0)
                    .totalDuration(0)
                    .finished(false)
                    .build();
        }
        return toMediaProgressVO(mp);
    }

    @Override
    public MediaProgressVO saveProgress(Long userId, Long itemId, SaveProgressDTO dto) {
        Item item = itemMapper.selectById(itemId);
        validateOwner(item, userId);

        // Block progress tracking for files saved from shares
        if (Boolean.TRUE.equals(item.getIsFromShare())) {
            throw new BizException("分享保存的文件不支持在线预览");
        }

        String mimeType = item.getMimeType();
        if (mimeType == null || !(mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType.startsWith("image/"))) {
            throw new BizException("不是媒体文件");
        }

        MediaProgress mp = mediaProgressMapper.selectOne(
                new LambdaQueryWrapper<MediaProgress>()
                        .eq(MediaProgress::getUserId, userId)
                        .eq(MediaProgress::getItemId, itemId));

        boolean finished = dto.getFinished() != null ? dto.getFinished() : false;

        if (mp == null) {
            mp = new MediaProgress()
                    .setUserId(userId)
                    .setItemId(itemId)
                    .setProgressSeconds(dto.getProgressSeconds())
                    .setTotalDuration(dto.getTotalDuration())
                    .setFinished(finished);
            mediaProgressMapper.insert(mp);
        } else {
            mp.setProgressSeconds(dto.getProgressSeconds())
                    .setTotalDuration(dto.getTotalDuration())
                    .setFinished(finished);
            mediaProgressMapper.updateById(mp);
        }

        return toMediaProgressVO(mp);
    }

    // ==================== 辅助 ====================

    private void validateOwner(Item item, Long ownerId) {
        if (item == null) throw new BizException("文件或文件夹不存在");
        if (!item.getOwnerId().equals(ownerId)) throw new BizException("无权操作此文件");
    }

    private String buildPath(Long ownerId, Long parentId, String name) {
        if (parentId == null) return "/" + name;
        Item parent = itemMapper.selectById(parentId);
        if (parent == null) return "/" + name;
        return (parent.getPath() != null ? parent.getPath() : "") + "/" + name;
    }

    private String rebuildPath(Item item) {
        return buildPath(item.getOwnerId(), item.getParentId(), item.getName());
    }

    private ItemVO toItemVO(Item item) {
        if (item == null) return null;
        return ItemVO.builder()
                .id(item.getId()).ownerId(item.getOwnerId()).parentId(item.getParentId())
                .name(item.getName()).isDirectory(item.getIsDirectory()).size(item.getSize())
                .mimeType(item.getMimeType()).etag(item.getEtag()).thumbnailKey(item.getThumbnailKey())
                .path(item.getPath()).version(item.getVersion()).isFromShare(item.getIsFromShare())
                .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toString() : null)
                .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : null)
                .build();
    }

    private UploadTaskVO toUploadTaskVO(UploadTask task) {
        if (task == null) return null;
        return UploadTaskVO.builder()
                .taskId(task.getId()).fileName(task.getFileName()).fileSize(task.getFileSize())
                .chunkSize(task.getChunkSize()).totalChunks(task.getTotalChunks())
                .receivedChunks(task.getReceivedChunks()).status(task.getStatus())
                .storagePrefix(task.getStoragePrefix())
                .expiresAt(task.getExpiresAt() != null ? task.getExpiresAt().toString() : null)
                .build();
    }

    private MediaProgressVO toMediaProgressVO(MediaProgress mp) {
        if (mp == null) return null;
        return MediaProgressVO.builder()
                .itemId(mp.getItemId())
                .progressSeconds(mp.getProgressSeconds())
                .totalDuration(mp.getTotalDuration())
                .finished(mp.getFinished())
                .updatedAt(mp.getUpdatedAt() != null ? mp.getUpdatedAt().toString() : null)
                .build();
    }

    /**
     * 转义 LIKE 通配符 % 和 _，使用 '!' 作为转义字符
     */
    private String escapeLike(String s) {
        if (s == null) return null;
        return s.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
                .replace("/", "!/");
    }

    /**
     * 检查 potentialChildId 是否是 parentId 的后代
     */
    private boolean isDescendant(Long parentId, Long potentialChildId) {
        Long currentId = potentialChildId;
        while (currentId != null) {
            if (currentId.equals(parentId)) return true;
            Item current = itemMapper.selectById(currentId);
            if (current == null || current.getParentId() == null) break;
            currentId = current.getParentId();
        }
        return false;
    }

    /**
     * 合并分片为一个完整的文件，返回合并后的 storageKey
     */
    private String mergeChunks(List<UploadChunk> chunks, String fileName) {
        if (chunks == null || chunks.isEmpty()) {
            throw new BizException("没有分片可合并");
        }
        String mergedKey = "merged/" + UUID.randomUUID().toString().replace("-", "") + "/" + fileName;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (UploadChunk chunk : chunks) {
                if (chunk.getStorageKey() != null) {
                    try (InputStream is = storageService.getInputStream(chunk.getStorageKey())) {
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            baos.write(buf, 0, len);
                        }
                    }
                }
            }
            storageService.store(mergedKey, baos.toByteArray());
            return mergedKey;
        } catch (IOException e) {
            throw new RuntimeException("合并分片失败", e);
        }
    }
}
