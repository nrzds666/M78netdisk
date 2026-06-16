package com.m78.netdisk.common;

import com.m78.netdisk.common.utils.CaptchaUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RED test: CaptchaUtil should generate arithmetic captcha without Nashorn.
 */
class CaptchaUtilTest extends BaseTest {

    @Autowired
    private CaptchaUtil captchaUtil;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private void mockRedis() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
    }

    @Test
    void generateSpec_withZeroLen_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> captchaUtil.generateSpec(0));
    }

    @Test
    void generateSpec_withNegativeLen_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> captchaUtil.generateSpec(-1));
    }

    @Test
    void generateSpec_withValidLen_shouldSucceed() {
        mockRedis();
        CaptchaUtil.CaptchaResult result = captchaUtil.generateSpec(4);
        assertNotNull(result);
        assertNotNull(result.getKey());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getKey().length() == 16);
    }

    @Test
    void generateArithmetic_shouldReturnBase64Image() {
        mockRedis();
        CaptchaUtil.CaptchaResult result = captchaUtil.generateArithmetic();
        assertNotNull(result);
        assertNotNull(result.getKey());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getKey().length() == 16);
        assertTrue(result.getImageBase64().startsWith("data:image"));
    }
}
