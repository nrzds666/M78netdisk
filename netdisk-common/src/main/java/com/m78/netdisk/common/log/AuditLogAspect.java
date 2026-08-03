package com.m78.netdisk.common.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.m78.netdisk.common.mapper.OperationLogMapper;
import com.m78.netdisk.common.domain.po.OperationLog;
import com.m78.netdisk.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
public class AuditLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer PND = new DefaultParameterNameDiscoverer();

    public AuditLogAspect(OperationLogMapper operationLogMapper,
                          ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        // 先执行业务方法
        Object result = joinPoint.proceed();

        // Flux/FluxLike 类型不记录审计日志，避免阻塞流式响应
        if (result != null && "reactor.core.publisher.Flux".equals(result.getClass().getName())) {
            return result;
        }

        try {
            Long userId = UserContext.getUserId();
            if (userId == null) {
                return result;
            }

            Long itemId = resolveLong(joinPoint, auditLog.itemId());
            String detail = resolveDetail(joinPoint, auditLog);
            HttpServletRequest request = getRequest();

            OperationLog log = new OperationLog()
                    .setUserId(userId)
                    .setAction(auditLog.action())
                    .setItemId(itemId)
                    .setDetail(detail)
                    .setIpAddress(request != null ? request.getRemoteAddr() : null)
                    .setUserAgent(request != null ? request.getHeader("User-Agent") : null);

            operationLogMapper.insert(log);
        } catch (Exception e) {
            // 审计日志失败不能影响主业务，静默处理
            log.warn("审计日志写入失败", e);
        }

        return result;
    }

    private String resolveDetail(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        String spel = auditLog.detail();
        if (spel.isEmpty()) {
            return null;
        }

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();

            StandardEvaluationContext ctx = new MethodBasedEvaluationContext(
                    null, method, args, PND);
            Object value = PARSER.parseExpression(spel).getValue(ctx);
            if (value == null) {
                return null;
            }

            // 如果返回值已是 String，直接返回
            if (value instanceof String) {
                return (String) value;
            }

            // 否则序列化为 JSON
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Long resolveLong(ProceedingJoinPoint joinPoint, String spel) {
        if (spel.isEmpty()) {
            return null;
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object[] args = joinPoint.getArgs();
            StandardEvaluationContext ctx = new MethodBasedEvaluationContext(
                    null, method, args, PND);
            Object value = PARSER.parseExpression(spel).getValue(ctx);
            if (value == null) {
                return null;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return attrs.getRequest();
        }
        return null;
    }
}
