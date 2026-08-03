package com.m78.netdisk.user.interceptor;

import cn.hutool.core.util.StrUtil;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.utils.JwtTool;
import com.m78.netdisk.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
        // 兼容 iframe/新窗口预览：从 query 参数获取 token
        if (StrUtil.isBlank(token)) {
            token = request.getParameter("token");
        }
        if (StrUtil.isNotBlank(token)) {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            try {
                JwtTool.TokenPayload payload = jwtTool.parseToken(token);
                UserContext.setUserId(payload.getUserId());
                UserContext.setRole(payload.getRole());
                return true;
            } catch (BizException e) {
                String msg = e.getMessage();
                // access token 过期/失效 → 401 让前端有机会刷新
                // refresh token 过期 → 401 但前端应跳登录页
                if (msg != null && msg.contains("刷新令牌")) {
                    log.warn("Refresh token expired for {}: {}", request.getRequestURI(), msg);
                } else {
                    log.warn("Invalid access token for {}: {}", request.getRequestURI(), msg);
                }
                writeUnauthorized(response, msg);
                return false;
            }
        }

        // 无 token 时要求认证
        writeUnauthorized(response, "未登录");
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":401,\"msg\":\"" + message.replace("\"", "\\\"") + "\",\"data\":null}");
            response.getWriter().flush();
        } catch (Exception e) {
            log.error("Failed to write unauthorized response", e);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.remove();
    }
}
