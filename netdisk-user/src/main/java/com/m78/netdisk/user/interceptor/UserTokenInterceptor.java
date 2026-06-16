package com.m78.netdisk.user.interceptor;

import cn.hutool.core.util.StrUtil;
import com.m78.netdisk.common.utils.JwtTool;
import com.m78.netdisk.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@RequiredArgsConstructor
@Component
public class UserTokenInterceptor implements HandlerInterceptor {

    private final JwtTool jwtTool;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String token = request.getHeader("Authorization");
        // 也支持 URL 参数传递 token（用于 window.open 下载/预览场景）
        if (StrUtil.isBlank(token)) {
            token = request.getParameter("token");
        }
        if (StrUtil.isNotBlank(token)) {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            try {
                Long userId = jwtTool.parseToken(token);
                UserContext.setUserId(userId);
            } catch (Exception e) {
                log.warn("Invalid token: {}", e.getMessage());
                // token 无效，跳过
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.remove();
    }
}