package com.m78.netdisk.vault.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.storage.StorageService;
import com.m78.netdisk.file.domain.dto.CreateFolderDTO;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.domain.vo.FileDownloadVO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.user.domain.po.User;
import com.m78.netdisk.user.mapper.UserMapper;
import com.m78.netdisk.vault.domain.dto.SetupVaultDTO;
import com.m78.netdisk.vault.domain.po.UserVault;
import com.m78.netdisk.vault.domain.vo.VaultStatusVO;
import com.m78.netdisk.vault.mapper.UserVaultMapper;
import com.m78.netdisk.vault.service.IVaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultServiceImpl implements IVaultService {

    private static final String VAULT_UNLOCK_KEY = "vault:unlock:";
    private static final long UNLOCK_TTL_SECONDS = 3600;
    private static final String VAULT_FAIL_KEY = "vault:fail:";
    private static final String VAULT_LOCK_KEY = "vault:lock:";

    private final UserVaultMapper userVaultMapper;
    private final UserMapper userMapper;
    private final ItemMapper itemMapper;
    private final StorageService storageService;
    private final StringRedisTemplate redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${netdisk.default-quota:10737418240}")
    private long defaultQuota;

    @Override
    @Transactional
    public void setup(Long userId, SetupVaultDTO dto) {
        // 检查是否已设置
        UserVault existing = userVaultMapper.selectOne(
                new LambdaQueryWrapper<UserVault>().eq(UserVault::getUserId, userId));
        if (existing != null) {
            throw new BizException("保险箱已存在，无法重复设置");
        }

        // 验证两次密码一致
        if (!dto.getVaultPassword().equals(dto.getConfirmPassword())) {
            throw new BizException("两次输入的保险箱密码不一致");
        }

        // 验证登录密码
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getLoginPassword(), user.getPasswordHash())) {
            throw new BizException("登录密码错误");
        }

        // 创建保险箱记录
        UserVault vault = new UserVault()
                .setUserId(userId)
                .setPasswordHash(passwordEncoder.encode(dto.getVaultPassword()));
        userVaultMapper.insert(vault);

        log.info("保险箱创建成功: userId={}", userId);
    }

    @Override
    @Transactional
    public void unlock(Long userId, String password) {
        if (StrUtil.isBlank(password)) {
            throw new BizException("保险箱密码不能为空");
        }

        // 检查是否已设置保险箱
        UserVault vault = userVaultMapper.selectOne(
                new LambdaQueryWrapper<UserVault>().eq(UserVault::getUserId, userId));
        if (vault == null) {
            throw new BizException("请先设置保险箱密码");
        }

        // 防暴力破解
        String failKey = VAULT_FAIL_KEY + userId;
        String lockKey = VAULT_LOCK_KEY + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BizException(429, "保险箱已锁定，请10分钟后再试");
        }

        if (!passwordEncoder.matches(password, vault.getPasswordHash())) {
            Long failCount = redisTemplate.opsForValue().increment(failKey);
            if (failCount != null && failCount == 1) {
                redisTemplate.expire(failKey, 1, TimeUnit.HOURS);
            }
            if (failCount != null && failCount >= 5) {
                redisTemplate.opsForValue().set(lockKey, "1", 10, TimeUnit.MINUTES);
                redisTemplate.delete(failKey);
            }
            throw new BizException(403, "保险箱密码错误");
        }

        // 清除失败计数
        redisTemplate.delete(failKey);
        redisTemplate.delete(lockKey);

        // 设置解锁标记，1小时有效
        redisTemplate.opsForValue().set(VAULT_UNLOCK_KEY + userId, "1", UNLOCK_TTL_SECONDS, TimeUnit.SECONDS);

        log.info("保险箱已解锁: userId={}", userId);
    }

    @Override
    public void lock(Long userId) {
        redisTemplate.delete(VAULT_UNLOCK_KEY + userId);
        log.info("保险箱已锁定: userId={}", userId);
    }

    @Override
    public VaultStatusVO getStatus(Long userId) {
        UserVault vault = userVaultMapper.selectOne(
                new LambdaQueryWrapper<UserVault>().eq(UserVault::getUserId, userId));
        boolean unlocked = "1".equals(redisTemplate.opsForValue().get(VAULT_UNLOCK_KEY + userId));
        return VaultStatusVO.builder()
                .enabled(vault != null)
                .unlocked(unlocked)
                .build();
    }

    @Override
    public IPage<ItemVO> listItems(Long userId, Long parentId, Integer pageNum, Integer size) {
        Page<Item> page = new Page<>(pageNum, Math.min(size, 100));
        return itemMapper.selectVaultItems(page, userId,
                (parentId == null || parentId == 0) ? null : parentId)
                .convert(this::toItemVO);
    }

    @Override
    @Transactional
    public ItemVO createFolder(Long userId, CreateFolderDTO dto) {
        String name = dto.getName();
        if (StrUtil.isBlank(name)) {
            throw new BizException("文件夹名称不能为空");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..") || name.contains("\0")) {
            throw new BizException("文件夹名称包含非法字符");
        }
        Long parentId = (dto.getParentId() == null || dto.getParentId() == 0) ? null : dto.getParentId();

        // 检查同名
        if (itemMapper.countByName(userId, parentId, name) > 0) {
            throw new BizException("同名文件夹已存在");
        }

        String path = buildPath(userId, parentId, name);

        Item item = new Item()
                .setOwnerId(userId)
                .setParentId(parentId)
                .setName(name)
                .setIsDirectory(true)
                .setSize(0L)
                .setPath(path)
                .setIsVaulted(true)
                .setVersion(1);

        itemMapper.insert(item);
        return toItemVO(item);
    }

    @Override
    @Transactional
    public ItemVO uploadFile(Long userId, Long parentId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (StrUtil.isBlank(originalName)) {
            throw new BizException("文件名不能为空");
        }

        Long pid = (parentId == null || parentId == 0) ? null : parentId;

        // 检查同名
        if (itemMapper.countByName(userId, pid, originalName) > 0) {
            throw new BizException("该目录下已存在同名文件");
        }

        // 检查配额
        User user = userMapper.selectById(userId);
        if (user != null && user.getQuotaBytes() - user.getUsedBytes() < file.getSize()) {
            throw new BizException("存储空间不足");
        }

        String storageKey = "vault/" + UUID.randomUUID().toString().replace("-", "")
                + "/" + originalName;

        try {
            storageService.store(storageKey, file.getBytes());
        } catch (IOException e) {
            log.error("保险箱文件上传写入失败", e);
            throw new BizException("文件上传失败");
        }

        String path = buildPath(userId, pid, originalName);

        Item item = new Item()
                .setOwnerId(userId)
                .setParentId(pid)
                .setName(originalName)
                .setIsDirectory(false)
                .setSize(file.getSize())
                .setMimeType(file.getContentType())
                .setStorageKey(storageKey)
                .setPath(path)
                .setIsVaulted(true)
                .setVersion(1);

        itemMapper.insert(item);

        // 原子增用量
        userMapper.tryAddUsedBytes(userId, file.getSize());

        log.info("保险箱文件上传完成: userId={}, fileName={}, size={}, itemId={}",
                userId, originalName, file.getSize(), item.getId());
        return toItemVO(item);
    }

    @Override
    public void downloadFile(Long userId, Long itemId, HttpServletResponse response) {
        Item item = itemMapper.selectById(itemId);
        if (item == null || !item.getOwnerId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        if (!Boolean.TRUE.equals(item.getIsVaulted())) {
            throw new BizException("该文件不在保险箱中");
        }

        // 从 storage 读取流
        String encodedName = URLEncoder.encode(item.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedName + "\"");
        response.setContentType(item.getMimeType() != null ? item.getMimeType() : "application/octet-stream");
        response.setContentLengthLong(item.getSize());

        try (InputStream is = storageService.getInputStream(item.getStorageKey());
             OutputStream os = response.getOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            log.error("保险箱文件下载失败: itemId={}", itemId, e);
            throw new BizException("文件下载失败");
        }
    }

    @Override
    @Transactional
    public void removeFromVault(Long userId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null || !item.getOwnerId().equals(userId)) {
            throw new BizException("文件不存在");
        }
        if (!Boolean.TRUE.equals(item.getIsVaulted())) {
            throw new BizException("该文件不在保险箱中");
        }
        item.setIsVaulted(false);
        itemMapper.updateById(item);
        log.info("文件已移出保险箱: itemId={}, userId={}", itemId, userId);
    }

    // ==================== 辅助方法 ====================

    private ItemVO toItemVO(Item item) {
        if (item == null) return null;
        return ItemVO.builder()
                .id(item.getId())
                .parentId(item.getParentId())
                .name(item.getName())
                .isDirectory(item.getIsDirectory())
                .size(item.getSize())
                .mimeType(item.getMimeType())
                .etag(item.getEtag())
                .path(item.getPath())
                .isDeleted(item.getIsDeleted())
                .version(item.getVersion())
                .createdAt(item.getCreatedAt() != null ? item.getCreatedAt().toString() : null)
                .updatedAt(item.getUpdatedAt() != null ? item.getUpdatedAt().toString() : null)
                .build();
    }

    private String buildPath(Long ownerId, Long parentId, String name) {
        if (parentId == null) {
            return "/vault/" + name;
        }
        Item parent = itemMapper.selectById(parentId);
        if (parent == null) {
            return "/vault/" + name;
        }
        return parent.getPath() + "/" + name;
    }
}
