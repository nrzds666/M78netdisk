package com.m78.netdisk.vault.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.file.domain.dto.CreateFolderDTO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.vault.domain.dto.SetupVaultDTO;
import com.m78.netdisk.vault.domain.vo.VaultStatusVO;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

public interface IVaultService {

    void setup(Long userId, SetupVaultDTO dto);

    void unlock(Long userId, String password);

    void lock(Long userId);

    VaultStatusVO getStatus(Long userId);

    IPage<ItemVO> listItems(Long userId, Long parentId, Integer page, Integer size);

    ItemVO createFolder(Long userId, CreateFolderDTO dto);

    ItemVO uploadFile(Long userId, Long parentId, MultipartFile file);

    void downloadFile(Long userId, Long itemId, HttpServletResponse response);

    void removeFromVault(Long userId, Long itemId);
}
