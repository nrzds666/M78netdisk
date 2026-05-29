package com.m78.netdisk.common;

import com.m78.netdisk.common.utils.CaptchaUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RED test: CaptchaUtil should validate len parameter in generateSpec.
 */
class CaptchaUtilTest extends BaseTest {

    @Autowired
    private CaptchaUtil captchaUtil;

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
        CaptchaUtil.CaptchaResult result = captchaUtil.generateSpec(4);
        assertNotNull(result);
        assertNotNull(result.getKey());
        assertNotNull(result.getImageBase64());
        assertTrue(result.getKey().length() == 16);
    }
}
