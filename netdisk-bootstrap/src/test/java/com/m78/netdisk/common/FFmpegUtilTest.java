package com.m78.netdisk.common;

import com.m78.netdisk.common.util.FFmpegUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RED test: FFmpegUtil command construction, timeout, and error handling.
 */
class FFmpegUtilTest {

    private final ByteArrayInputStream FAKE_JPEG = new ByteArrayInputStream(
            new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x10, (byte) 0xFF, (byte) 0xD9});

    @Test
    void captureFrame_whenProcessSucceeds_shouldReturnBytes() throws Exception {
        var process = mockProcess(FAKE_JPEG, 0, true);
        var util = new FFmpegUtil("ffmpeg", 10, pb -> process);

        Optional<byte[]> result = util.captureFrame("/path/to/video.mp4", 0);

        assertTrue(result.isPresent());
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, 0x10, (byte) 0xFF, (byte) 0xD9},
                result.get());
    }

    @Test
    void captureFrame_whenProcessTimesOut_shouldReturnEmpty() throws Exception {
        var process = mockProcess(FAKE_JPEG, 0, false);
        var util = new FFmpegUtil("ffmpeg", 10, pb -> process);

        Optional<byte[]> result = util.captureFrame("/path/to/video.mp4", 0);

        assertTrue(result.isEmpty());
        verify(process).destroyForcibly();
    }

    @Test
    void captureFrame_whenExitCodeNonZero_shouldReturnEmpty() throws Exception {
        var process = mockProcess(FAKE_JPEG, 1, true);
        var util = new FFmpegUtil("ffmpeg", 10, pb -> process);

        Optional<byte[]> result = util.captureFrame("/path/to/video.mp4", 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void captureFrame_whenProcessThrowsIOException_shouldReturnEmpty() throws Exception {
        var util = new FFmpegUtil("ffmpeg", 10, pb -> { throw new java.io.IOException("not found"); });

        Optional<byte[]> result = util.captureFrame("/path/to/video.mp4", 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void captureFrame_shouldBuildCorrectCommand() throws Exception {
        var process = mockProcess(FAKE_JPEG, 0, true);
        var util = new FFmpegUtil("ffmpeg", 10, pb -> {
            var cmd = pb.command();
            assertEquals(10, cmd.size());
            assertEquals("ffmpeg", cmd.get(0));
            assertEquals("-ss", cmd.get(1));
            assertEquals("5", cmd.get(2));
            assertEquals("-i", cmd.get(3));
            assertEquals("/test.mp4", cmd.get(4));
            assertEquals("-vframes", cmd.get(5));
            assertEquals("1", cmd.get(6));
            assertEquals("-f", cmd.get(7));
            assertEquals("mjpeg", cmd.get(8));
            assertEquals("-", cmd.get(9));
            assertTrue(pb.redirectErrorStream());
            return process;
        });

        util.captureFrame("/test.mp4", 5);
    }

    // ── helpers ──

    private java.lang.Process mockProcess(InputStream stdout, int exitCode, boolean waitForResult)
            throws Exception {
        var p = mock(java.lang.Process.class);
        when(p.getInputStream()).thenReturn(stdout);
        when(p.exitValue()).thenReturn(exitCode);
        when(p.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(waitForResult);
        return p;
    }
}
