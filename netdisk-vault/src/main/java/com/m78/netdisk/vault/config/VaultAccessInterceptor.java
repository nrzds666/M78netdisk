package com.m78.netdisk.vault.config;

import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class VaultAccessInterceptor implements HandlerInterceptor {

    private static final String VAULT_UNLOCK_KEY = "vault:unlock:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "需要登录");
        }
        String unlockFlag = redisTemplate.opsForValue().get(VAULT_UNLOCK_KEY + userId);
        if (!"1".equals(unlockFlag)) {
            throw new BizException(403, "保险箱未解锁，请先输入保险箱密码");
        }
        return true;
    }
}
