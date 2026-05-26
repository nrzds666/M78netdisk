package com.m78.netdisk.share.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.share.domain.dto.CreateShareDTO;
import com.m78.netdisk.share.domain.vo.ShareVO;
import com.m78.netdisk.share.service.IShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final IShareService shareService;

    @PostMapping
    public R<ShareVO> createShare(@Valid @RequestBody CreateShareDTO dto) {
        return R.ok(shareService.createShare(UserContext.getUserId(), dto));
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancelShare(@PathVariable Long id) {
        shareService.cancelShare(UserContext.getUserId(), id);
        return R.ok();
    }

    @GetMapping("/mine")
    public R<IPage<ShareVO>> myShares(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(shareService.listMyShares(UserContext.getUserId(), page, size));
    }

    @GetMapping("/access/{token}")
    public R<ShareVO> accessShare(@PathVariable String token,
                                   @RequestParam(required = false) String password) {
        return R.ok(shareService.accessShare(token, password));
    }
}