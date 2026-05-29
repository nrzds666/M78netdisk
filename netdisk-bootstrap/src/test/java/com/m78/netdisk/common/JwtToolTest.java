package com.m78.netdisk.common;

import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.utils.JwtTool;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RED test: JwtTool secret key validation and general token operations.
 */
class JwtToolTest extends BaseTest {

    @Autowired
    private JwtTool jwtTool;

    @Test
    void testCreateAndParseAccessToken() {
        Long userId = 42L;
        String token = jwtTool.createAccessToken(userId);
        assertNotNull(token);
        assertFalse(token.isBlank());

        Long parsed = jwtTool.parseToken(token);
        assertEquals(userId, parsed);
    }

    @Test
    void testCreateAndParseRefreshToken() {
        Long userId = 99L;
        String token = jwtTool.createRefreshToken(userId);
        assertNotNull(token);
        assertFalse(token.isBlank());

        Long parsed = jwtTool.parseToken(token);
        assertEquals(userId, parsed);
    }

    @Test
    void testParseInvalidToken() {
        assertThrows(BizException.class, () ->
                jwtTool.parseToken("invalid.token.here"));
    }

    @Test
    void testParseExpiredToken() {
        // Should throw BizException for expired tokens
        assertThrows(BizException.class, () ->
                jwtTool.parseToken("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidHlwZSI6ImFjY2VzcyIsImlhdCI6MTUwMDAwMDAwMCwiZXhwIjoxNTAwMDAwMDAxfQ.signature"));
    }

    @Test
    void testLogoutThenTokenInvalid() {
        Long userId = 77L;
        String token = jwtTool.createAccessToken(userId);
        assertNotNull(token);

        jwtTool.logout(userId);

        assertThrows(BizException.class, () ->
                jwtTool.parseToken(token));
    }

    @Test
    void testParseNullToken() {
        assertThrows(BizException.class, () ->
                jwtTool.parseToken(null));
    }

    @Test
    void testParseEmptyToken() {
        assertThrows(BizException.class, () ->
                jwtTool.parseToken(""));
    }
}
