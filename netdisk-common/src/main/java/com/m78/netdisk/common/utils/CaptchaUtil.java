package com.m78.netdisk.common.utils;

import com.wf.captcha.ArithmeticCaptcha;
import com.wf.captcha.SpecCaptcha;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 图形验证码工具类
 * 使用 EasyCaptcha 生成算术/字符验证码，存入 Redis
 */
@Component
@RequiredArgsConstructor
public class CaptchaUtil {

    private final StringRedisTemplate redisTemplate;

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_EXPIRE_SEC = 120; // 2分钟有效

    /**
     * 生成算术验证码，返回图片 base64 和 key
     */
    public CaptchaResult generateArithmetic() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(130, 48);
        captcha.setLen(2); // 2个数字运算
        String code = captcha.text();    // 运算结果，如 "5"
        String key = nextKey();
        redisTemplate.opsForValue().set(
                CAPTCHA_PREFIX + key, code.toLowerCase(),
                CAPTCHA_EXPIRE_SEC, TimeUnit.SECONDS);
        return new CaptchaResult(key, captcha.toBase64());
    }

    /**
     * 生成字符验证码，返回图片 base64 和 key
     */
    public CaptchaResult generateSpec(int len) {
        if (len <= 0) {
            throw new IllegalArgumentException("验证码长度必须大于0");
        }
        SpecCaptcha captcha = new SpecCaptcha(130, 48, len);
        String code = captcha.text().toLowerCase();
        String key = nextKey();
        redisTemplate.opsForValue().set(
                CAPTCHA_PREFIX + key, code,
                CAPTCHA_EXPIRE_SEC, TimeUnit.SECONDS);
        return new CaptchaResult(key, captcha.toBase64());
    }

    /**
     * 校验验证码（校验后立即删除，防止重复使用）
     */
    public boolean verify(String key, String code) {
        if (key == null || code == null) return false;
        String redisKey = CAPTCHA_PREFIX + key;
        String saved = redisTemplate.opsForValue().get(redisKey);
        if (saved == null) return false;
        redisTemplate.delete(redisKey);
        return saved.equals(code.toLowerCase().trim());
    }

    @Data
    @AllArgsConstructor
    public static class CaptchaResult {
        private String key;
        private String imageBase64;
    }

    private static String nextKey() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}