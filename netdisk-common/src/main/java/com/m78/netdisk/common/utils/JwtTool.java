package com.m78.netdisk.common.utils;

import cn.hutool.core.util.StrUtil;
import com.m78.netdisk.common.exception.BizException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
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

    public JwtTool(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private SecretKey getSigningKey() {
        if (StrUtil.isEmpty(secret)) {
            throw new IllegalArgumentException("JWT secret is not configured");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId) {
        Date now = new Date();
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("type", "access")
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

        // refresh token 存 Redis
        redisTemplate.opsForValue().set(
                TOKEN_PREFIX + "refresh:" + userId,
                token,
                refreshTokenExpMs, TimeUnit.MILLISECONDS);

        return token;
    }

    public Long parseToken(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token);

            Long userId = Long.parseLong(jws.getPayload().getSubject());
            String type = jws.getPayload().get("type", String.class);

            // 校验 Redis 中是否存在该 token
            boolean valid;
            if ("refresh".equals(type)) {
                String saved = redisTemplate.opsForValue().get(TOKEN_PREFIX + "refresh:" + userId);
                valid = token.equals(saved);
            } else {
                String saved = redisTemplate.opsForValue().get(TOKEN_PREFIX + "access:" + userId + ":" + token);
                // key 已包含完整 token 字符串，key 存在即代表 token 有效
                valid = saved != null;
            }

            if (!valid) {
                throw new BizException(401, "令牌已失效");
            }

            return userId;
        } catch (BizException e) {
            throw e;
        } catch (NumberFormatException e) {
            throw new BizException(401, "无效的令牌");
        } catch (JwtException e) {
            throw new BizException(401, "无效或过期的令牌");
        }
    }

    public void logout(Long userId) {
        // 使用 Set 存储用户的 access token key，避免 KEYS 阻塞
        String userTokenSetKey = TOKEN_PREFIX + "tokens:access:" + userId;
        Set<String> tokenKeys = redisTemplate.opsForSet().members(userTokenSetKey);
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
        redisTemplate.delete(userTokenSetKey);
        redisTemplate.delete(TOKEN_PREFIX + "refresh:" + userId);
    }
}