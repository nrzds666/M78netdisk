# M78 NetDisk

基于 Spring Boot 的私有网盘系统 — 文件存储、分享、相册管理一站式解决方案。

## 技术栈

| 层面 | 技术 |
|:-----|:-----|
| 运行时 | Java 11 · Spring Boot 2.7.18 |
| 数据库 | MySQL 8.0 · MyBatis-Plus 3.5.7 |
| 缓存 | Redis（JWT Token / 验证码 / 保险箱解锁状态） |
| 认证 | JWT 双 Token（Access 24h + Refresh 30天）· BCrypt 密码加密 |
| 存储后端 | 阿里云 OSS / 本地文件系统（可切换） |
| 文档预览 | LibreOffice + jodconverter（Office → PDF） |
| 视频处理 | FFmpeg（首帧截图） |

## 模块结构

```
m78-netdisk（根 POM）
├── netdisk-bootstrap    启动入口 + 全局配置
├── netdisk-common       公共工具 · 响应体 · 异常处理 · 存储抽象 · 审计日志
├── netdisk-user         用户注册/登录 · JWT 拦截器 · 验证码
├── netdisk-file         文件 CRUD · 分片上传 · 回收站 · ZIP 打包 · 缩略图
├── netdisk-share        分享链接（密码/时效/下载上限）· 文件保存到网盘
├── netdisk-album        相册管理 · 封面设置 · 幻灯片 · 相册分享
├── netdisk-vault        机密文件箱（独立密码保护）
├── netdisk-calendar     农历日历 · 黄历宜忌
```

## 功能一览

- **文件管理** — 上传/下载/预览/重命名/移动/搜索/分页/视图切换
- **分片上传** — 大文件分片（>10MB 自动启用）+ 断点续传 + 暂停/取消
- **回收站** — 软删除/恢复/永久删除 + 30 天自动清理
- **分享** — 提取码 + 过期时间（1天/7天/30天/永久）+ 权限 + 下载次数限制
- **相册** — 创建/删除/封面 + 图片幻灯片 + 分享
- **保险箱** — 独立密码 + 锁定/解锁 + 文件移入移出
- **日历** — 每日黄历 + 宜忌 + 操作吉凶建议
- **文档预览** — Office（doc/docx/xls/xlsx/ppt/pptx）实时转 PDF 预览

## 快速启动

### 本地运行

**前置要求：** JDK 11 · Maven 3.9+ · MySQL 8.0 · Redis

```bash
# 1. 初始化数据库
mysql -u root -p < database/init.sql

# 2. 配置开发环境密钥
# 编辑 netdisk-bootstrap/src/main/resources/application-dev.yaml
# 填入 DB / JWT / OSS 相关密钥

# 3. 启动
mvn clean package -pl netdisk-bootstrap -am
java -jar netdisk-bootstrap/target/m78-netdisk.jar
```

访问 http://localhost:8080

### Docker 部署

```bash
# 1. 配置密钥
cp .env  # 填入实际值

# 2. 启动
docker compose up -d

# 3. 查看日志
docker compose logs -f backend
```

## 文档

| 文档 | 说明 |
|:-----|:-----|
| [PROJECT.md](PROJECT.md) | 完整项目文档（API 表格、分页规范、代码示例） |
| [module-logic.md](module-logic.md) | 各模块业务逻辑详解 |
| [docs/api-test-doc.md](docs/api-test-doc.md) | 接口测试用例全集（700+ 行） |
| [database/schema-design.md](database/schema-design.md) | 数据库设计文档 |
| [TEST_REPORT.md](TEST_REPORT.md) | 测试报告（58/58 通过） |
