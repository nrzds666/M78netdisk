package com.m78.netdisk.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.domain.po.OperationLog;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.mapper.OperationLogMapper;
import com.m78.netdisk.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminLogController {

    private final OperationLogMapper operationLogMapper;

    private void checkAuth() {
        if (UserContext.getUserId() == null) throw new BizException(401, "未登录");
        if (!"admin".equals(UserContext.getRole())) throw new BizException(403, "无管理员权限");
    }

    @GetMapping
    public R<Page<OperationLog>> list(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        checkAuth();
        Page<OperationLog> p = new Page<>(page, size);
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<>();
        if (userId != null) qw.eq(OperationLog::getUserId, userId);
        if (action != null && !action.isBlank()) qw.eq(OperationLog::getAction, action);
        if (dateFrom != null) qw.ge(OperationLog::getCreatedAt, dateFrom);
        if (dateTo != null) qw.le(OperationLog::getCreatedAt, dateTo);
        qw.orderByDesc(OperationLog::getCreatedAt);
        return R.ok(operationLogMapper.selectPage(p, qw));
    }
}
