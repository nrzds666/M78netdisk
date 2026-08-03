package com.m78.netdisk.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.file.mapper.ItemMapper;
import com.m78.netdisk.share.mapper.ShareMapper;
import com.m78.netdisk.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final UserMapper userMapper;
    private final ItemMapper itemMapper;
    private final ShareMapper shareMapper;

    private void checkAuth() {
        if (UserContext.getUserId() == null) throw new BizException(401, "未登录");
        if (!"admin".equals(UserContext.getRole())) throw new BizException(403, "无管理员权限");
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        checkAuth();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userMapper.selectCount(null));
        stats.put("totalFiles",
                itemMapper.selectCount(new LambdaQueryWrapper<com.m78.netdisk.file.domain.po.Item>()
                        .eq(com.m78.netdisk.file.domain.po.Item::getIsDirectory, false)
                        .eq(com.m78.netdisk.file.domain.po.Item::getIsDeleted, false)));
        stats.put("totalFolders",
                itemMapper.selectCount(new LambdaQueryWrapper<com.m78.netdisk.file.domain.po.Item>()
                        .eq(com.m78.netdisk.file.domain.po.Item::getIsDirectory, true)
                        .eq(com.m78.netdisk.file.domain.po.Item::getIsDeleted, false)));
        stats.put("usedBytes", userMapper.sumUsedBytes());
        stats.put("totalShares",
                shareMapper.selectCount(new LambdaQueryWrapper<com.m78.netdisk.share.domain.po.Share>()
                        .eq(com.m78.netdisk.share.domain.po.Share::getIsCanceled, false)));
        return R.ok(stats);
    }
}
