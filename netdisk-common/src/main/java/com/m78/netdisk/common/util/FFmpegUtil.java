package com.m78.netdisk.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg 进程封装工具类。
 * 提供视频帧截图功能，统一 P0 和 P1 的 FFmpeg 调用。
 */
@Slf4j
public class FFmpegUtil {

    private final String ffmpegPath;
    private final int timeoutSeconds;
    private final ProcessRunner processRunner;

    @FunctionalInterface
    public interface ProcessRunner {
        Process run(ProcessBuilder pb) throws IOException;
    }

    /**
     * @param ffmpegPath     FFmpeg 可执行文件路径（默认 "ffmpeg" 走系统 PATH）
     * @param timeoutSeconds 单次 FFmpeg 调用超时（秒）
     */
    public FFmpegUtil(String ffmpegPath, int timeoutSeconds) {
        this(ffmpegPath, timeoutSeconds, pb -> pb.start());
    }

    /**
     * 注入自定义 ProcessRunner（测试用）。
     * public 因为测试在 netdisk-bootstrap 模块，跨模块包级不可见。
     */
    public FFmpegUtil(String ffmpegPath, int timeoutSeconds, ProcessRunner processRunner) {
        this.ffmpegPath = ffmpegPath;
        this.timeoutSeconds = timeoutSeconds;
        this.processRunner = processRunner;
    }

    /**
     * 截取视频指定时间帧，返回 JPEG bytes。
     *
     * @param inputPath 视频文件绝对路径
     * @param timeSec   截图时间点（秒）
     * @return JPEG 字节数据，失败或超时返回 empty
     */
    public Optional<byte[]> captureFrame(String inputPath, long timeSec) {
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-ss", String.valueOf(timeSec),
                "-i", inputPath,
                "-vframes", "1", "-f", "mjpeg", "-"
        );
        pb.redirectErrorStream(false);

        try {
            Process process = processRunner.run(pb);

            // 使用独立线程分别读取 stdout 和 stderr，避免管道缓冲区满导致死锁
            ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();

            Thread stdoutThread = new Thread(() -> drain(process.getInputStream(), stdoutBuf));
            Thread stderrThread = new Thread(() -> drain(process.getErrorStream(), stderrBuf));

            try {
                stdoutThread.setDaemon(true);
                stderrThread.setDaemon(true);
                stdoutThread.start();
                stderrThread.start();

                // 等待进程结束，超时则强制终止
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("FFmpeg timeout (>{})s for: {}", timeoutSeconds, inputPath);
                    return Optional.empty();
                }

                stdoutThread.join(2000);
                stderrThread.join(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                stdoutThread.interrupt();
                stderrThread.interrupt();
                throw ie;
            }

            // 收集 stderr 日志（仅警告级别）
            String stderr = stderrBuf.toString(StandardCharsets.UTF_8).trim();
            if (!stderr.isEmpty()) {
                log.debug("FFmpeg stderr for {}: {}", inputPath, stderr.lines().limit(3).toList());
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("FFmpeg exit code {} for: {}", exitCode, inputPath);
                return Optional.empty();
            }

            byte[] data = stdoutBuf.toByteArray();
            if (data.length == 0) {
                log.warn("FFmpeg produced empty output for: {}", inputPath);
                return Optional.empty();
            }

            return Optional.of(data);
        } catch (IOException e) {
            log.warn("FFmpeg execution failed for: {}", inputPath, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("FFmpeg interrupted for: {}", inputPath);
            return Optional.empty();
        }
    }

    /** 将 InputStream 内容持续 drain 到 ByteArrayOutputStream */
    private static void drain(InputStream in, ByteArrayOutputStream out) {
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } catch (IOException ignored) {
            // 进程已结束，部分读取是正常的
        }
    }
}
