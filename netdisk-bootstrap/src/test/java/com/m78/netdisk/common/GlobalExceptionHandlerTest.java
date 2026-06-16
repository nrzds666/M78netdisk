package com.m78.netdisk.common;

import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 单元测试
 * <p>
 * 验证各类异常被正确转换为 R<T> 统一响应格式。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBizException_shouldReturnSpecifiedCodeAndMessage() {
        BizException ex = new BizException(401, "令牌已过期");

        // 模拟 @ExceptionHandler 返回，实际是 R<Void> 不是 ResponseEntity
        R<Void> result = handler.handleBizException(ex);

        assertEquals(401, result.getCode());
        assertEquals("令牌已过期", result.getMsg());
    }

    @Test
    void handleBizException_withDefaultCode_shouldReturn500() {
        BizException ex = new BizException("业务异常");

        R<Void> result = handler.handleBizException(ex);

        assertEquals(500, result.getCode());
        assertEquals("业务异常", result.getMsg());
    }

    @Test
    void handleValidation_shouldReturn400WithFieldErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(null, "formDTO");
        bindingResult.addError(new FieldError("formDTO", "username", "用户名不能为空"));
        bindingResult.addError(new FieldError("formDTO", "password", "密码长度6-72个字符"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        R<Void> result = handler.handleValidation(ex);

        assertEquals(400, result.getCode());
        assertNotNull(result.getMsg());
        assertTrue(result.getMsg().contains("用户名不能为空"));
        assertTrue(result.getMsg().contains("密码长度6-72个字符"));
    }

    @Test
    void handleMissingParam_shouldReturn400WithParamName() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("captchaCode", "String");

        R<Void> result = handler.handleMissingParam(ex);

        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().contains("captchaCode"));
    }

    @Test
    void handleUnknown_shouldReturn500() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        RuntimeException ex = new RuntimeException("意外错误");

        R<Void> result = handler.handleUnknown(ex, request);

        assertEquals(500, result.getCode());
        assertEquals("服务器内部错误", result.getMsg());
    }

    @Test
    void handleMultipartException_shouldReturn400WithFileSizeHint() {
        MultipartException ex = new MultipartException("文件大小超过限制");

        R<Void> result = handler.handleMultipartException(ex);

        assertEquals(400, result.getCode());
        assertNotNull(result.getMsg());
        assertTrue(result.getMsg().contains("2GB"),
                "错误消息应提示最大文件大小: " + result.getMsg());
    }
}
