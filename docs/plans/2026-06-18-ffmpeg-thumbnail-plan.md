# FFmpeg 视频海报 + 缩略图实施计划

> **Hermes:** TDD 逐任务执行，每个任务 RED→GREEN→REFACTOR

**目标：** 实现 P0（LocalStorageService 视频海报截帧）和 P1（上传后异步生成缩略图）

**前置条件：** FFmpeg 已安装在 Windows PATH 中（D:\ffmpeg-8.1.1-essentials_build\bin\ffmpeg.exe）

**架构概要：**
- P0: `LocalStorageService.getVideoSnapshot()` 用 FFmpeg 截帧，Semaphore 限流，磁盘缓存
- P1: `@TransactionalEventListener(AFTER_COMMIT)` → 异步 `MediaProcessingService` 生成缩略图 → `storageService` 存储 → DB 更新
- 前端: slideshow 用原图，grid view 用缩略图

**测试命令：**
```bash
# 运行全部测试
cmd.exe /c "cd /d D:\M78netdisk && D:\maven\apache-maven-3.9.9\bin\mvn test -pl netdisk-bootstrap -am"
```

---

## Task 1: 创建 FFmpegUtil 工具类

**目标：** 封装 FFmpeg 进程调用，统一 P0 和 P1 的 FFmpeg 使用

**文件：**
- 新建: `netdisk-common/src/main/java/com/m78/netdisk/common/util/FFmpegUtil.java`
- 测试: `netdisk-bootstrap/src/test/java/com/m78/netdisk/common/FFmpegUtilTest.java`

**内容：**
```java
@Slf4j
public class FFmpegUtil {

    private final String ffmpegPath;
    private final int timeoutSeconds;

    public FFmpegUtil(String ffmpegPath, int timeoutSeconds) {
        this.ffmpegPath = ffmpegPath;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 截取视频指定时间帧，返回 JPEG bytes
     * @return Optional.empty() 表示失败/超时
     */
    public Optional<byte[]> captureFrame(String inputPath, long timeSec) {
        ProcessBuilder pb = new ProcessBuilder(
            ffmpegPath, "-ss", String.valueOf(timeSec),
            "-i", inputPath,
            "-vframes", "1", "-f", "mjpeg", "-"
        );
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream stdout = process.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = stdout.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("FFmpeg timeout (>{}s) for: {}", timeoutSeconds, inputPath);
                return Optional.empty();
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.warn("FFmpeg exit code {} for: {}", exitCode, inputPath);
                return Optional.empty();
            }
            return Optional.of(baos.toByteArray());
        } catch (IOException e) {
            log.warn("FFmpeg execution failed for: {}", inputPath, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
```

**TDD 步骤：**

Step 1 RED: 编写测试验证命令构造 + 超时处理 + 错误处理（mock ProcessBuilder/Process）
Step 2: 确认测试因 FFmpegUtil 不存在而 FAIL
Step 3 GREEN: 实现 FFmpegUtil
Step 4: 确认测试 PASS
Step 5: Commit

---

## Task 2: 添加 ffmpeg-path 配置

**目标：** 允许配置 FFmpeg 二进制路径，默认"ffmpeg"（走 PATH）

**文件：**
- 修改: `netdisk-bootstrap/src/main/resources/application.yaml`

**修改：**
```yaml
netdisk:
  storage:
    ffmpeg-path: ffmpeg           # 新增
    type: oss
```

同时新增 `FFmpegConfig.java` 配置类（netdisk-common）：
```java
@Configuration
public class FFmpegConfig {
    @Value("${netdisk.storage.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Bean
    public FFmpegUtil ffmpegUtil() {
        return new FFmpegUtil(ffmpegPath, 10);
    }
}
```

无需测试（纯配置改动），手动验证配置加载。

---

## Task 3: LocalStorageService.getVideoSnapshot() — P0 核心

**目标：** 重写 LocalStorageService 的 getVideoSnapshot，用 FFmpeg 截帧 + 磁盘缓存 + 并发控制

**文件：**
- 修改: `netdisk-common/src/main/java/com/m78/netdisk/common/storage/LocalStorageService.java`
- 测试: `netdisk-bootstrap/src/test/java/com/m78/netdisk/common/LocalStorageServiceTest.java`

**改动内容：**

```java
// 新增字段
private static final Semaphore FFMPEG_SEMAPHORE = new Semaphore(4);
private final FFmpegUtil ffmpegUtil; // 构造注入

@Override
public InputStream getVideoSnapshot(String relativeKey, long timeSec) {
    if (relativeKey == null || relativeKey.isBlank()) return null;
    Path sourcePath = resolvePath(relativeKey);
    if (!Files.exists(sourcePath)) return null;

    Path cacheDir = rootPath.resolve(".thumbnails");
    String hash = DigestUtils.md5DigestAsHex((relativeKey + "_" + timeSec).getBytes(StandardCharsets.UTF_8));
    Path cacheFile = cacheDir.resolve(hash + ".jpg");

    // 缓存命中
    if (Files.exists(cacheFile)) {
        try { return Files.newInputStream(cacheFile); } catch (IOException e) { /* fall through */ }
    }

    // 限流 + 生成
    try {
        if (!FFMPEG_SEMAPHORE.tryAcquire(5, TimeUnit.SECONDS)) {
            log.warn("FFmpeg semaphore timeout, skip poster: {}", relativeKey);
            return null;
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
    }

    try {
        Files.createDirectories(cacheDir);
        Optional<byte[]> result = ffmpegUtil.captureFrame(sourcePath.toAbsolutePath().toString(), timeSec);
        if (result.isEmpty()) return null;

        // 原子写入缓存
        Path tmpFile = cacheDir.resolve(hash + ".tmp");
        Files.write(tmpFile, result.get());
        try {
            Files.move(tmpFile, cacheFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmpFile, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return new ByteArrayInputStream(result.get());
    } catch (IOException e) {
        log.warn("Poster cache write failed: {}", relativeKey, e);
        return null;
    } finally {
        FFMPEG_SEMAPHORE.release();
    }
}
```

**TDD 步骤：**

Step 1 RED: 编写测试
- mock FFmpegUtil.captureFrame 返回已知 byte[]
- 首次调用 → 无缓存 → 调用 FFmpegUtil → 写入缓存 → 返回 InputStream
- 再次调用 → 命中缓存 → 直接返回 Files.newInputStream

Step 2: 确认测试因未实现而 FAIL
Step 3 GREEN: 实现 getVideoSnapshot
Step 4: 确认测试 PASS
Step 5: 全量 mvn test 确认无回归
Step 6: Commit

---

## Task 4: 创建 FileCreatedEvent

**目标：** Spring 事件，携带 Item 对象，用于跨事务边界的异步触发

**文件：**
- 新建: `netdisk-file/src/main/java/com/m78/netdisk/file/event/FileCreatedEvent.java`

**内容：**
```java
@Getter
public class FileCreatedEvent extends ApplicationEvent {
    private final Item item;
    public FileCreatedEvent(Object source, Item item) {
        super(source);
        this.item = item;
    }
}
```

纯 POJO，无需测试。

---

## Task 5: 创建 MediaProcessingService

**目标：** 统一的缩略图生成服务，图片用 ImageIO，视频用 getVideoSnapshot

**文件：**
- 新建: `netdisk-file/src/main/java/com/m78/netdisk/file/service/impl/MediaProcessingService.java`
- 测试: `netdisk-bootstrap/src/test/java/com/m78/netdisk/file/MediaProcessingServiceTest.java`

**逻辑：**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private final StorageService storageService;
    private final ItemMapper itemMapper;
    private final FFmpegUtil ffmpegUtil;
    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024; // 20MB

    public void generateThumbnail(Item item) {
        if (Boolean.TRUE.equals(item.getIsDirectory())) return;
        if (item.getMimeType() == null) return;

        try {
            byte[] thumbnailBytes = null;
            if (item.getMimeType().startsWith("image/")) {
                thumbnailBytes = generateImageThumbnail(item);
            } else if (item.getMimeType().startsWith("video/")) {
                thumbnailBytes = generateVideoThumbnail(item);
            }
            if (thumbnailBytes == null) return;

            String thumbKey = "thumbnails/" + item.getId() + ".jpg";
            storageService.store(thumbKey, thumbnailBytes);

            // 绕过 version 乐观锁，只更新 thumbnailKey
            itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, item.getId())
                .set(Item::getThumbnailKey, thumbKey));
        } catch (Exception e) {
            log.warn("Thumbnail generation failed: itemId={}, mime={}",
                item.getId(), item.getMimeType(), e);
        }
    }

    private byte[] generateImageThumbnail(Item item) throws IOException {
        // 格式检查
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(item.getMimeType());
        if (readers == null || !readers.hasNext()) {
            log.warn("Unsupported image format: {}, skip thumbnail", item.getMimeType());
            return null;
        }

        BufferedImage original;
        try (InputStream in = storageService.getInputStream(item.getStorageKey())) {
            // 大图跳过
            if (item.getSize() != null && item.getSize() > MAX_IMAGE_SIZE) {
                log.warn("Image too large ({}), skip thumbnail: itemId={}", item.getSize(), item.getId());
                return null;
            }
            original = ImageIO.read(in);
        }

        if (original == null) return null;

        // Resize to 300px width
        int newW = 300;
        int newH = (int) (original.getHeight() * (300.0 / original.getWidth()));
        BufferedImage thumbnail = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = thumbnail.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newW, newH, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(thumbnail, "jpg", baos);
        return baos.toByteArray();
    }

    private byte[] generateVideoThumbnail(Item item) {
        // 本地存储：通过 LocalStorageService 获取文件路径
        // OSS 存储：通过 OssStorageService.getVideoSnapshot()
        // 统一使用 storageService.getVideoSnapshot()
        try (InputStream in = storageService.getVideoSnapshot(item.getStorageKey(), 0)) {
            if (in == null) return null;
            return in.readAllBytes();
        } catch (Exception e) {
            log.warn("Video thumbnail failed: itemId={}", item.getId(), e);
            return null;
        }
    }
}
```

**TDD 步骤：**

Step 1 RED: 编写测试
- mock StorageService.getInputStream 返回测试图片 → 验证 resize 后的 bytes 宽度为 300
- mock StorageService.getVideoSnapshot 返回测试 JPEG → 验证返回非空 bytes
- mock mimeType=image/webp → 验证格式检查跳过
- mock size=50MB → 验证大图跳过

Step 2: 确认测试 FAIL
Step 3 GREEN: 实现 MediaProcessingService
Step 4: 确认测试 PASS
Step 5: 全量 mvn test
Step 6: Commit

---

## Task 6: 创建 ThumbnailGenerationListener + 发布事件

**目标：** 事务提交后异步触发缩略图生成，覆盖所有上传路径

**文件：**
- 新建: `netdisk-file/src/main/java/com/m78/netdisk/file/event/ThumbnailGenerationListener.java`
- 修改: `netdisk-file/src/main/java/com/m78/netdisk/file/service/impl/FileServiceImpl.java`
- 修改: `netdisk-file/src/main/java/com/m78/netdisk/file/service/impl/UploadMergeService.java`

**Listener：**
```java
@Component
@RequiredArgsConstructor
public class ThumbnailGenerationListener {

    private final MediaProcessingService mediaProcessingService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFileCreated(FileCreatedEvent event) {
        mediaProcessingService.generateThumbnail(event.getItem());
    }
}
```

**FileServiceImpl 修改（3 处 publish）：**
- `createFile()` 第 146 行 `return toItemVO(item);` 前加 `eventPublisher.publishEvent(new FileCreatedEvent(this, item));`
- `completeUpload()` 第 731 行 `task.setStatus("completed")` 前加 publish（OSS 路径）
- `createFolder()` 不加（目录不需要缩略图）

**UploadMergeService 修改（1 处 publish）：**
- `performMerge()` 第 92 行 `log.info("上传完成...")` 前加 publish

**注意：** FileCreatedEvent 的 source 用 `this`（service 实例）。

无需额外测试（Event 机制由 Spring 测试验证）。

---

## Task 7: 添加缩略图端点

**目标：** GET /api/files/thumbnail/{id} 提供缩略图文件

**文件：**
- 修改: `netdisk-file/src/main/java/com/m78/netdisk/file/controller/FileController.java`
- 测试: `netdisk-bootstrap/src/test/java/com/m78/netdisk/file/FileControllerThumbnailTest.java`

**端点逻辑：**
```java
/**
 * 获取缩略图（供 <img :src> 使用）
 */
@GetMapping("/thumbnail/{id}")
public void getThumbnail(@PathVariable Long id, HttpServletResponse response) throws IOException {
    FileDownloadVO info = fileService.getPreviewInfo(UserContext.getUserId(), id);

    // 需要先从 DB 获取完整的 Item（含 thumbnailKey）
    // 使用 fileService.getPreviewInfo 鉴权，再读 thumbnailKey
    // 直接通过 storageKey 获取缩略图文件
    String thumbKey = "thumbnails/" + id + ".jpg";
    try (InputStream in = storageService.getInputStream(thumbKey)) {
        response.setContentType("image/jpeg");
        response.setHeader("Cache-Control", "max-age=86400");
        copy(in, response.getOutputStream(), Long.MAX_VALUE);
    } catch (Exception e) {
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Thumbnail not available");
        }
    }
}
```

**TDD 步骤：**

Step 1 RED: 编写 Spring MVC 测试
- 模拟已认证用户
- mock fileService.getPreviewInfo 返回有效信息
- mock storageService.getInputStream 返回测试 bytes
- 验证响应状态 200、Content-Type=image/jpeg

Step 2: 确认 FAIL
Step 3 GREEN: 实现端点
Step 4: 确认 PASS + 全量测试
Step 5: Commit

---

## Task 8: toItemVO + permanentlyDelete 联动

**目标：** thumbnailKey 映射为浏览器可访问 URL；永久删除时清理缩略图

**文件：**
- 修改: `netdisk-file/src/main/java/com/m78/netdisk/file/service/impl/FileServiceImpl.java`

**toItemVO 修改（第 1027 行）：**
```java
// 原: .thumbnailKey(item.getThumbnailKey())
// 新:
if (item.getThumbnailKey() != null) {
    String publicUrl = storageService.getPublicUrl(item.getThumbnailKey());
    vo.setThumbnailKey(publicUrl != null
        ? publicUrl                                 // OSS: 直链 URL
        : "/api/files/thumbnail/" + item.getId());  // 本地: 端点 URL
}
```

**permanentlyDelete 修改（第 379 行后）：**
```java
// 清理缩略图
if (item.getThumbnailKey() != null) {
    storageService.delete(item.getThumbnailKey());
}
```

**TDD 步骤：**

Step 1 RED: 编写测试
- 创建 item 带 thumbnailKey
- toItemVO → 验证 thumbnailKey 被映射为 URL
- permanentlyDelete → 验证 storageService.delete 被调用

Step 2 GREEN: 实现
Step 3: 确认 PASS + 全量测试
Step 4: Commit

---

## Task 9: 前端 slideshow 使用原图

**目标：** grid view 用缩略图，slideshow 始终用原图

**文件：**
- 修改: `AlbumDetailView.vue`（2 处）
- 修改: `AlbumShareAccessView.vue`（2 处）

**AlbumDetailView.vue 第 264 行：**
```javascript
// 原
.map(i => i.thumbnailKey || getPreviewUrl(i.itemId))
// 新
.map(i => getPreviewUrl(i.itemId))
```

**AlbumDetailView.vue 第 362 行：**
```javascript
// 原
thumbnailKey: i.thumbnailKey || getPreviewUrl(i.itemId)
// 新
thumbnailKey: getPreviewUrl(i.itemId)
```

**AlbumShareAccessView.vue 第 38 行：**
```html
<!-- 原 -->
:src="item.thumbnailKey || getSharePreviewUrl(item.itemId)"
<!-- 新 -->
:src="getSharePreviewUrl(item.itemId)"
```

**AlbumShareAccessView.vue 第 88 行：**
```javascript
// 原
.map(i => i.thumbnailKey || getSharePreviewUrl(i.itemId))
// 新
.map(i => getSharePreviewUrl(i.itemId))
```

无需测试，手动验证。

---

## Task 10: 全量验证

1. 运行 `mvn test -pl netdisk-bootstrap -am` 确认全部通过
2. 启动服务
3. 上传图片 → 检查 DB items.thumbnail_key 是否被填充
4. 上传视频 → 检查 poster 端点是否返回图片
5. 打开相册 grid view → 检查缩略图显示
6. 打开相册 slideshow → 检查使用原图（非 300px 缩略图）
7. 永久删除文件 → 检查缩略图文件是否清理
8. Commit

---

## 任务依赖图

```
Task 1 (FFmpegUtil) ──────────────────────────────────┐
                                                        │
Task 2 (Config) ────→ Task 3 (LocalStorageService P0) ─┤
                                                        │
Task 4 (Event) ──→ Task 5 (MediaProcessingService) ────┤
                   ↗                                    │
Task 6 (Listener + publish) ────────────────────────────┤
                                                        │
Task 7 (Thumbnail endpoint) ────────────────────────────┤
                                                        │
Task 8 (toItemVO + permanentlyDelete) ──────────────────┤
                                                        │
Task 9 (Frontend) ──────────────────────────────────────┘
                                                        ↓
                                              Task 10 (验证)
```

顺序执行：1→2→3→4→5→6→7→8→9→10。
