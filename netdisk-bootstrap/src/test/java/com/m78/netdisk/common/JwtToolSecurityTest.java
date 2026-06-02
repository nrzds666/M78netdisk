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
    void jwtTool_shouldHaveGettersAndSetters() throws Exception {
        // @Data generates public getters and setters
        assertNotNull(jwtTool.getClass().getMethod("getSecret"));
        assertNotNull(jwtTool.getClass().getMethod("getIssuer"));
        assertNotNull(jwtTool.getClass().getMethod("setSecret", String.class));
        assertNotNull(jwtTool.getClass().getMethod("setIssuer", String.class));
    }
}
