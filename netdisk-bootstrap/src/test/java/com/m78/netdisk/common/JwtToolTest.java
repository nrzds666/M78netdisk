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
        String token = jwtTool.createAccessToken(userId, "admin");
        assertNotNull(token);
        assertFalse(token.isBlank());

        JwtTool.TokenPayload parsed = jwtTool.parseToken(token);
        assertEquals(userId, parsed.getUserId());
        assertEquals("admin", parsed.getRole());
    }

    @Test
    void testCreateAndParseRefreshToken() {
        Long userId = 99L;
        String token = jwtTool.createRefreshToken(userId);
        assertNotNull(token);
        assertFalse(token.isBlank());

        JwtTool.TokenPayload parsed = jwtTool.parseToken(token);
        assertEquals(userId, parsed.getUserId());
        assertNull(parsed.getRole()); // refresh token doesn't carry role
    }

    @Test
    void testCreateAccessTokenDefaultRole() {
        Long userId = 55L;
        String token = jwtTool.createAccessToken(userId, null);
        JwtTool.TokenPayload parsed = jwtTool.parseToken(token);
        assertEquals("user", parsed.getRole());
    }

    @Test
    void testParseInvalidToken() {
        assertThrows(BizException.class, () ->
                jwtTool.parseToken("invalid.token.here"));
    }

    @Test
    void testParseExpiredToken() {
        assertThrows(BizException.class, () ->
                jwtTool.parseToken("eyJhbG...ture"));
    }

    @Test
    void testLogoutThenTokenInvalid() {
        Long userId = 77L;
        String token = jwtTool.createAccessToken(userId, "user");
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

    @Test
    void testParseTokenUserId() {
        Long userId = 88L;
        String token = jwtTool.createAccessToken(userId, "user");
        assertEquals(userId, jwtTool.parseTokenUserId(token));
    }
}
