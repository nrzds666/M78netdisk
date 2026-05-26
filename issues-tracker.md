# M78 NetDisk — 问题追踪

> 记录代码审查中发现的所有问题，按模块分类，修复后标记 ✅

---

## netdisk-common（公共层）

| # | 问题 | 状态 | 备注 |
|---|------|------|------|
| C1 | 密码哈希用 MD5，安全性较弱 | ✅ | 改为 BCrypt（`BCryptPasswordEncoder`），新增 `spring-security-crypto` 依赖 |
| C2 | `R<T>` 的 data=null 时仍序列化 "data": null，建议加 `@JsonInclude(Include.NON_NULL)` | ✅ | 类级别加 `@JsonInclude(Include.NON_NULL)`，null 字段不再序列化 |

---

## netdisk-user（用户模块）

| # | 问题 | 状态 | 备注 |
|---|------|------|------|
| U1 | `GET /api/users/{id}` 无权限校验，登录用户可查任意用户信息 | ✅ | 已删除该接口，`GET /api/users` 代替 `/me` 只查当前用户 |
| U2 | `buildLoginVO(user, "")` 传空字符串 hack，语义不清晰 | ✅ | 新增无参重载 `buildLoginVO(user)`，语义清晰 |

---

## netdisk-file（文件模块）

| # | 问题 | 状态 | 备注 |
|---|------|------|------|
| F1 | 分片上传只记录元数据 — 补充了单文件上传接口 `POST /api/files/upload`，MultipartFile 写入本地磁盘 | ✅ | 分片上传仍为"确认模式"设计，新增直接上传路径 |
| F2 | `confirmChunk` 中 `UploadChunk.setSize(0)` 硬编码 | ✅ | 新增 `@RequestParam Integer size`，传 null 时兜底 0 |
| F3 | `permanentlyDelete` 不清除磁盘文件 | ✅ | 新增 `FileStorageService`，永久删除时清理 storageKey 对应文件+版本文件 |
| F4 | `move` 方法逐条循环操作，批量移动时效率低 | ✅ | 优化为预查父目录路径、目标 parentId 外提，跳过无权限项而非抛异常 |
| F5 | `CreateFolderDTO.parentId` 标 `@NotNull` 但 Service 逻辑支持 null | ✅ | 去掉 `@NotNull`，加注释说明 null/0 表示根目录 |
| F6 | 没有定时任务清理过期上传任务和回收站（30天前的软删除记录） | ✅ | 新增 `FileCleanupTask` + `@EnableScheduling` |

### 代码审查发现 ✅

| # | 问题 | 修复 |
|---|------|------|
| R1 | 🔒 `FileStorageService.resolvePath()` 路径穿越漏洞 — 文件名含 `../` 可逃逸 storage 目录 | 追加 `resolved.startsWith(rootPath)` 校验，越界抛 SecurityException |
| R2 | `FileCleanupTask` 使用 FQN `com.m78.netdisk.file.domain.po.Item` 而非 import | 改为 import + 简写类名 |

---

## netdisk-share（分享模块）

| # | 问题 | 状态 | 备注 |
|---|------|------|------|
| S1 | ShareVO 的 `fileName`、`fileSize`、`isDirectory`、`mimeType` 字段**始终为空**，未 JOIN items 表取值 | ✅ | `toShareVO()` 通过 itemMapper 补查文件信息并填充 |
| S2 | `accessShare` 不增加 `download_count`，`max_downloads` 限制形同虚设 | ✅ | 访问分享时原子 +1 并 updateById |
| S3 | `permission` 字段（view/download/edit）只存储不校验 | ✅ | createShare 时校验枚举值，无效值拒绝；后续下载/编辑接口需追加实际权限拦截 |

---

## netdisk-bootstrap（启动层）

| # | 问题 | 状态 | 备注 |
|---|------|------|------|
| B1 | `netdisk.storage.local-path: D:/M78netdisk/storage` 已配置，但**代码中无任何地方引用** | ✅ | `FileStorageService` 已使用此配置初始化存储根目录 |
| B2 | SwaggerConfig 文件模块路径包含 `/api/upload/**`，但实际上传路径是 `/api/files/upload/**` | ✅ | 已移除 `/api/upload/**` 路径 |

---

## 数据库 & 文档

| # | 问题 | 状态 | 备注 |
|---|------|------|------|
| D1 | `operation_logs` 和 `storage_nodes` 表有建表但无任何代码操作 | ✅ | 审计日志 AOP 切面 + 存储节点 CRUD 管理接口已实现 |
| D2 | MyBatisConfig `DbType.POSTGRE_SQL` → `DbType.MYSQL` | ✅ | 已修正 |
| D3 | netdisk-common/pom.xml 残留 postgresql 依赖 | ✅ | 已移除 |
| D4 | schema-design.md 全篇 PostgreSQL → MySQL | ✅ | 已修正 |
| D5 | PROJECT.md 运行方式 psql → mysql | ✅ | 已修正 |

---

### 状态图例

| 符号 | 含义 |
|------|------|
| ⏳ | 待修复 |
| 🔧 | 修复中 |
| ✅ | 已完成 |
| ❌ | 已关闭（无需修复） |
