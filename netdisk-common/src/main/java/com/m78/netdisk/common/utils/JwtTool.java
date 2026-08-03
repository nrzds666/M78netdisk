package com.m78.netdisk.common.utils;

import cn.hutool.core.util.StrUtil;
import com.m78.netdisk.common.exception.BizException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Data
public class JwtTool {

    @Value("${netdisk.jwt.secret}")
    private String secret;

    @Value("${netdisk.jwt.issuer:m78-netdisk}")
    private String issuer;

    @Value("${netdisk.jwt.access-token-expiration:86400000}")
    private long accessTokenExpMs;

    @Value("${netdisk.jwt.refresh-token-expiration:2592000000}")
    private long refreshTokenExpMs;

    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_PREFIX = "token:";

    @Data
    @AllArgsConstructor
    public static class TokenPayload {
        private Long userId;
        private String role;
    }

    public JwtTool(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private SecretKey getSigningKey() {
        if (StrUtil.isEmpty(secret)) {
            throw new IllegalStateException("JWT secret is not configured");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (got " + keyBytes.length + ")");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(Long userId, String role) {
        Date now = new Date();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("type", "access")
                .claim("role", role != null ? role : "user")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpMs))
                .signWith(getSigningKey())
                .compact();

        // access token 存 Redis，过期时间同 JWT 有效期
        String tokenKey = TOKEN_PREFIX + "access:" + userId + ":" + token;
        redisTemplate.opsForValue().set(
                tokenKey,
                String.valueOf(userId),
                accessTokenExpMs, TimeUnit.MILLISECONDS);

        // 维护 token 索引 Set，用于 logout 时批量删除，避免 KEYS 阻塞
        String userTokenSetKey = TOKEN_PREFIX + "tokens:access:" + userId;
        redisTemplate.opsForSet().add(userTokenSetKey, tokenKey);
        redisTemplate.expire(userTokenSetKey, accessTokenExpMs, TimeUnit.MILLISECONDS);

        return token;
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpMs))
                .signWith(getSigningKey())
                .compact();

        // refresh token 存 Redis，key 包含 token hash 避免多设备覆盖
        String tokenHash = Integer.toHexString(token.hashCode());
        String redisKey = TOKEN_PREFIX + "refresh:" + userId + ":" + tokenHash;
        redisTemplate.opsForValue().set(
                redisKey,
                token,
                refreshTokenExpMs, TimeUnit.MILLISECONDS);

        return token;
    }

    public TokenPayload parseToken(String token) {
        if (StrUtil.isBlank(token)) {
            throw new BizException(401, "令牌不能为空");
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);

            Long userId = Long.parseLong(jws.getPayload().getSubject());
            String type = jws.getPayload().get("type", String.class);
            String role = jws.getPayload().get("role", String.class);

            // 校验 Redis 中是否存在该 token
            boolean valid;
            if ("refresh".equals(type)) {
                // 遍历所有该用户的 refresh token key 来查找匹配
                String pattern = TOKEN_PREFIX + "refresh:" + userId + ":*";
                Set<String> keys = redisTemplate.keys(pattern);
                valid = false;
                if (keys != null) {
                    for (String key : keys) {
                        String saved = redisTemplate.opsForValue().get(key);
                        if (token.equals(saved)) {
                            valid = true;
                            break;
                        }
                    }
                }
            } else {
                String saved = redisTemplate.opsForValue().get(TOKEN_PREFIX + "access:" + userId + ":" + token);
                valid = saved != null;
            }

            if (!valid) {
                // access token 失效 → 可刷新；refresh token 失效 → 需重新登录
                if ("refresh".equals(type)) {
                    throw new BizException(401, "刷新令牌已失效，请重新登录");
                }
                throw new BizException(401, "访问令牌已失效");
            }

            return new TokenPayload(userId, role);
        } catch (BizException e) {
            throw e;
        } catch (ExpiredJwtException e) {
            // JWT 签名过期，在 parseSignedClaims 阶段就抛出了，无法知道 type
            // 统一提示过期，前端可根据上下文决定是否尝试刷新
            throw new BizException(401, "令牌已过期");
        } catch (NumberFormatException e) {
            throw new BizException(401, "无效的令牌");
        } catch (JwtException e) {
            throw new BizException(401, "无效或过期的令牌");
        }
    }

    public void logout(Long userId) {
        String userTokenSetKey = TOKEN_PREFIX + "tokens:access:" + userId;
        Set<String> tokenKeys = redisTemplate.opsForSet().members(userTokenSetKey);
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
        redisTemplate.delete(userTokenSetKey);
        // Clean up all device-specific refresh tokens
        Set<String> refreshKeys = redisTemplate.keys(TOKEN_PREFIX + "refresh:" + userId + ":*");
        if (refreshKeys != null && !refreshKeys.isEmpty()) {
            redisTemplate.delete(refreshKeys);
        }
    }

    /** 兼容旧调用：仅获取 userId */
    public Long parseTokenUserId(String token) {
        return parseToken(token).getUserId();
    }
}
