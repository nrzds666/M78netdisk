package com.m78.netdisk.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.utils.UserContext;
import com.m78.netdisk.user.domain.po.User;
import com.m78.netdisk.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;

    private void checkAuth() {
        if (UserContext.getUserId() == null) throw new BizException(401, "未登录");
        if (!"admin".equals(UserContext.getRole())) throw new BizException(403, "无管理员权限");
    }

    @GetMapping
    public R<Page<User>> list(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false) String keyword) {
        checkAuth();
        Page<User> p = new Page<>(page, size);
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(User::getUsername, keyword);
        }
        qw.orderByDesc(User::getCreatedAt);
        return R.ok(userMapper.selectPage(p, qw));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        checkAuth();
        User user = new User();
        user.setId(id);
        user.setStatus(body.get("status"));
        userMapper.updateById(user);
        return R.ok();
    }

    @PutMapping("/{id}/quota")
    public R<Void> updateQuota(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        checkAuth();
        User user = new User();
        user.setId(id);
        user.setQuotaBytes(body.get("quotaBytes"));
        userMapper.updateById(user);
        return R.ok();
    }
}
