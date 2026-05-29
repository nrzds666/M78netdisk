package com.m78.netdisk.common;

import com.m78.netdisk.common.utils.JwtTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test: JwtTool should not expose setters for injected secret fields.
 * Uses reflection to verify setter is absent.
 */
class JwtToolSecurityTest extends BaseTest {

    @Autowired
    private JwtTool jwtTool;

    @Test
    void jwtTool_shouldNotHavePublicSecretSetter() throws Exception {
        // Verify there's no public setter for the 'secret' field
        // @Data generates public setters; @Getter does not
        assertThrows(NoSuchMethodException.class,
                () -> jwtTool.getClass().getMethod("setSecret", String.class));
    }

    @Test
    void jwtTool_shouldStillHaveGetters() throws Exception {
        // Basic functionality must still work
        assertNotNull(jwtTool.getClass().getMethod("getSecret"));
        assertNotNull(jwtTool.getClass().getMethod("getIssuer"));
    }
}
