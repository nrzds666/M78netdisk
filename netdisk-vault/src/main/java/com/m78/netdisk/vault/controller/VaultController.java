package com.m78.netdisk.vault.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.common.log.AuditLog;
import com.m78.netdisk.file.domain.dto.CreateFolderDTO;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.vault.domain.dto.SetupVaultDTO;
import com.m78.netdisk.vault.domain.dto.UnlockVaultDTO;
import com.m78.netdisk.vault.domain.vo.VaultStatusVO;
import com.m78.netdisk.vault.service.IVaultService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
public class VaultController {

    private final IVaultService vaultService;

    @AuditLog(action = "VAULT_SETUP")
    @PostMapping("/setup")
    public R<Void> setup(@Valid @RequestBody SetupVaultDTO dto) {
        vaultService.setup(UserContext.getUserId(), dto);
        return R.ok();
    }

    @AuditLog(action = "VAULT_UNLOCK")
    @PostMapping("/unlock")
    public R<Void> unlock(@Valid @RequestBody UnlockVaultDTO dto) {
        vaultService.unlock(UserContext.getUserId(), dto.getPassword());
        return R.ok();
    }

    @AuditLog(action = "VAULT_LOCK")
    @PostMapping("/lock")
    public R<Void> lock() {
        vaultService.lock(UserContext.getUserId());
        return R.ok();
    }

    @GetMapping("/status")
    public R<VaultStatusVO> status() {
        return R.ok(vaultService.getStatus(UserContext.getUserId()));
    }

    @GetMapping("/files/list")
    public R<IPage<ItemVO>> listItems(
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(vaultService.listItems(UserContext.getUserId(), parentId, page, size));
    }

    @PostMapping("/files/folder")
    public R<ItemVO> createFolder(@Valid @RequestBody CreateFolderDTO dto) {
        return R.ok(vaultService.createFolder(UserContext.getUserId(), dto));
    }

    @AuditLog(action = "VAULT_UPLOAD", detail = "#file.originalFilename")
    @PostMapping("/files/upload")
    public R<ItemVO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long parentId) {
        return R.ok(vaultService.uploadFile(UserContext.getUserId(), parentId, file));
    }

    @GetMapping("/files/download/{id}")
    public void downloadFile(@PathVariable Long id, HttpServletResponse response) {
        vaultService.downloadFile(UserContext.getUserId(), id, response);
    }

    @AuditLog(action = "VAULT_REMOVE", itemId = "#itemId")
    @PutMapping("/files/remove")
    public R<Void> removeFromVault(@RequestParam Long itemId) {
        vaultService.removeFromVault(UserContext.getUserId(), itemId);
        return R.ok();
    }
}
