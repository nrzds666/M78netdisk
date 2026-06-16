# M78 NetDisk 上传/传输问题修复方案

> 登记时间：2026-06-10 18:00
> 状态：待执行（主人已下班，下次会话开始）

## 背景

基于 server.log 和代码审计发现的 7 项问题。

## 修复清单

### ① 验证码 NPE（Java 17 + easy-captcha）

**根因**：`ArithmeticCaptcha` 内部使用 Nashorn JS 引擎，JDK 15+ 已移除。服务端运行于 Java 17.0.12。

**方案**：创建 `JavaArithmeticCaptcha` 纯 Java 实现（不依赖 Nashorn），生成算术表达式图片。

**文件**：
- 新增：`netdisk-common/.../utils/JavaArithmeticCaptcha.java`
- 修改：`CaptchaUtil.java`（替换 `ArithmeticCaptcha`）

### ② 单文件上传进度回调参数误导

**根因**：`FileListView.vue:820` 回调参数名为 `progressEvent` 但实际收到的是数字 `percent`。`progressEvent.loaded > 0` 永不为 true。

**方案**：重命名为 `percent`，去掉 `.loaded` 判断。

**文件**：`FileListView.vue:820-825`

### ③ resumeTask 立即移除 task

**根因**：`TransferView.vue:214` 在 `dispatchEvent` 后立即 filter 移除 task，不等文件选择完成。

**方案**：不在 resumeTask 中移除，改为在 `handleResumeUpload`（FileListView.vue）确认文件选择并开始上传后才移除。

**文件**：`TransferView.vue:210-214` + `FileListView.vue:867-912`

### ④ TransferView 缺 ElMessage import

**根因**：`TransferView.vue:220,222` 使用 `ElMessage.success/error` 但未 import。

**方案**：添加 `ElMessage` 到 import。

**文件**：`TransferView.vue:157`

### ⑤ uploadChunk() 不改 status→uploading

**根因**：新端点 `uploadChunk()` 只存分片，不把 task status 从 `pending` 改为 `uploading`。旧 `confirmChunk` 有改。

**方案**：在 `uploadChunk()` 中若 status 为 pending，改为 uploading。

**文件**：`FileServiceImpl.java:498-534`

### ⑥ 上传任务不存在根因排查

**根因**：server.log 显示三次连续 `BizException("上传任务不存在")`。原因待定——可能为 `@Transactional` 事务传播问题或前端 taskId 传递异常。

**方案**：在 `uploadChunk` 中增强日志输出（打印 taskId 和请求详情），便于下次复现时定位。

**文件**：`FileServiceImpl.java:501-503`

### ⑦ OssStorageService.store() 异常包装

**根因**：OSS SDK 抛出 `RuntimeException`（非 IOException），穿透 `catch (IOException e)` 到达 `@Transactional` 隔离层和全局兜底 handler。

**方案**：在 OssStorageService.store() 外层包裹 try-catch，将 OSS 异常转为 `RuntimeException` 并记录详细日志。

**文件**：`OssStorageService.java:69-75`

## 执行顺序

① → ② → ④ → ⑤ → ③ → ⑥ → ⑦

每项严格执行 TDD（RED→GREEN→REFACTOR）。后端测试用：
```
cmd.exe /c "cd /d D:\M78netdisk && D:\maven\apache-maven-3.9.9\bin\mvn test -pl netdisk-bootstrap -am"
```
