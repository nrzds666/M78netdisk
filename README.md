# M78 NetDisk 后端

基于 Spring Boot 的私有网盘系统 — 文件存储、分享、相册管理、AI 助手一站式解决方案。

## 技术栈

| 层面 | 技术 |
|:-----|:-----|
| 运行时 | Java 17 · Spring Boot 3.3.7 |
| 数据库 | MySQL 8.0 · MyBatis-Plus 3.5.7 |
| 缓存 | Redis（JWT Token / 验证码 / 保险箱解锁状态） |
| 认证 | JWT 双 Token（Access 24h + Refresh 30天）· BCrypt 密码加密 |
| 存储 | 阿里云 OSS / 本地文件系统（可切换） |
| 文档预览 | LibreOffice + jodconverter（Office → PDF） |
| 视频处理 | FFmpeg（首帧截图 / 缩略图生成） |
| AI 对话 | Spring AI + DeepSeek（SSE 流式对话 / RAG 知识库增强） |
| AI 生图 | ComfyUI + SDXL Turbo（LLM 自动扩写提示词） |
| AI 文档 | LLM 自动生成文档（md/docx/xlsx/html/json/csv） |

## 模块结构

```
m78-netdisk（根 POM）
├── netdisk-bootstrap    启动入口 + 全局配置 + 管理后台 API + 前端静态资源
├── netdisk-common       公共工具 · 响应体 · 异常处理 · 存储抽象 · 审计日志
├── netdisk-ai           AI 助手（SSE 流式对话 · RAG 增强 · 文档生成 · 图片生成）
├── netdisk-user         用户注册/登录 · JWT 拦截器 · 验证码
├── netdisk-file         文件 CRUD · 分片上传 · 回收站 · ZIP 打包 · 缩略图 · 媒体处理
├── netdisk-share        分享链接（密码/时效/下载上限）· 文件保存到网盘
├── netdisk-album        相册管理 · 封面设置 · 幻灯片 · 相册分享
├── netdisk-vault        机密文件箱（独立密码保护）
├── netdisk-calendar     农历日历 · 黄历宜忌
```

## 功能一览

- **文件管理** — 上传/下载/预览/重命名/移动/搜索/分页/视图切换
- **分片上传** — 大文件（>10MB）自动分片 + 断点续传 + 暂停/取消
- **回收站** — 软删除/恢复/永久删除 + 30 天自动清理
- **分享** — 提取码 + 过期时间 + 下载次数限制
- **相册** — 创建/删除/封面 + 幻灯片 + 相册分享
- **保险箱** — 独立密码 + 锁定/解锁 + 文件移入移出
- **日历** — 农历日历 + 黄历宜忌
- **文档预览** — Office 文档实时转 PDF 预览
- **AI 助手** — 浮动对话窗口 · SSE 流式输出 · RAG 知识库检索
- **AI 生图** — 对话中描述需求 → LLM 扩写提示词 → SDXL Turbo 生成 → 自动保存到网盘
- **AI 文档** — 对话中生成文档（Markdown/Word/Excel/HTML 等），一键保存
- **管理后台** — 仪表盘 · 用户管理 · 节点监控 · 操作日志

## 快速启动

**前置要求：** JDK 17 · Maven 3.9+ · MySQL 8.0 · Redis

```bash
# 1. 初始化数据库
mysql -u root -p < database/init.sql

# 2. 配置开发环境密钥
# 编辑 netdisk-bootstrap/src/main/resources/application-dev.yaml
# 填入 DB 密码 / JWT Secret / OSS 密钥 / DeepSeek API Key

# 3. AI 生图需要 ComfyUI Desktop 运行在 localhost:8188
#    下载 SDXL Turbo 模型放入 models/checkpoints/

# 4. 编译启动
mvn clean package -pl netdisk-bootstrap -am -DskipTests
java -jar netdisk-bootstrap/target/m78-netdisk.jar
```

访问 http://localhost:8081

## 配置说明

```yaml
# application.yaml 关键配置
netdisk:
  comfyui:
    base-url: http://localhost:8188      # ComfyUI 服务地址
    default-width: 768                   # 默认生图分辨率
    default-height: 768
    default-steps: 6                     # SDXL Turbo 步数
    modelFileName: sd_xl_turbo_1.0_fp16.safetensors
  storage:
    type: oss                            # local | oss
  jwt:
    secret: ${JWT_SECRET}
```

## 文档

| 文档 | 说明 |
|:-----|:-----|
| [PROJECT.md](PROJECT.md) | 完整项目文档（API 表格、分页规范） |
| [database/schema-design.md](database/schema-design.md) | 数据库设计文档 |
| [docs/ai-image-generation-plan.md](docs/ai-image-generation-plan.md) | AI 生图 V1 设计文档 |
| [docs/ai-image-generation-v2-design.md](docs/ai-image-generation-v2-design.md) | AI 生图 V2 统一对话模式设计 |
