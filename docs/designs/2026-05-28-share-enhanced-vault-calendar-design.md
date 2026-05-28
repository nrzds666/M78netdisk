# M78 网盘 — 功能增强设计文档

> 日期：2026-05-28
> 作者：Hermes Agent
> 状态：待审查

---

## 1. 文件分享模块增强

### 1a. 分享过期时间预设

**需求：** 创建分享时可选择"一天、一周、一个月、永久"四个预选项，过期后他人无法下载，但可重新创建分享。

**设计：**

**新增枚举：** `netdisk-share/.../domain/enums/ShareExpire.java`

```java
public enum ShareExpire {
    ONE_DAY(24),
    ONE_WEEK(168),
    ONE_MONTH(720),
    PERMANENT(null);

    private final Long hours;
}
```

**修改 `CreateShareDTO`：**

| 字段 | 操作 | 类型 | 说明 |
|------|------|------|------|
| itemId | 保留 | Long | |
| password | 保留 | String | |
| permission | 保留 | String | |
| expireHours | **删除** | Long | 被 expireType 取代 |
| maxDownloads | 保留 | Integer | |
| **expireType** | **新增** | String | `1D` / `1W` / `1M` / `PERMANENT` |
| **expireLabel** | **新增** | String | 前端展示用："一天"、"一周"、"一个月"、"永久" |

**修改 `ShareServiceImpl.createShare()`：**
- 根据 `expireType` 计算 `expireAt`：
  - `1D` → now + 24 小时
  - `1W` → now + 168 小时
  - `1M` → now + 720 小时（约 30 天）
  - `PERMANENT` → `expireAt = null`（永不过期）
- SQL 中 `selectValidShare` 已有 `expire_at IS NULL OR expire_at > now()` 逻辑，兼容不变

**共享逻辑：** 过期后 `selectValidShare` 返回 null，前端显示"已过期"。用户调用 `/api/shares` POST 重新创建新分享。

### 1b. 分享分类标记

**需求：** 对"自己分享的文件"和"接收别人分享的文件"做分类标记，前端分开展示。

**设计：**

**新建表 `received_shares`：**

```sql
CREATE TABLE IF NOT EXISTS received_shares (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    share_id      BIGINT NOT NULL,
    item_id       BIGINT NOT NULL,
    owner_id      BIGINT NOT NULL,
    access_token  VARCHAR(32) NOT NULL,
    accessed_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (user_id, share_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (share_id) REFERENCES shares(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_received_shares_user ON received_shares(user_id, accessed_at DESC);
```

**新增 `ReceivedShareMapper`：** 提供分页查询和插入方法。

**修改 `ShareServiceImpl.accessShare()`：**
- `accessShare()` 成功后，检查 `received_shares` 表，若无则插入记录（幂等，UNIQUE 约束保证）

**新增 API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/shares/received` | 分页查询用户接收的分享记录 |

**改造 `ShareVO`：** 新增字段 `isReceived`（Boolean），`GET /mine` 返回 false，`GET /received` 返回 true。

**前端展示：** 两个 Tab / 分类：
- "我的分享" → `/api/shares/mine`
- "我接收的" → `/api/shares/received`

---

## 2. 机密文件箱

### 2a. 数据结构

**新建表 `user_vaults`：**

```sql
CREATE TABLE IF NOT EXISTS user_vaults (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**修改 `items` 表：** 新增字段

```sql
ALTER TABLE items ADD COLUMN is_vaulted TINYINT(1) NOT NULL DEFAULT 0;
CREATE INDEX idx_items_vaulted ON items(owner_id, is_vaulted, parent_id);
```

**修改 `Item` POJO：** 新增 `isVaulted` 字段

### 2b. 业务逻辑

**新增模块：`netdisk-vault`**（独立 Maven 子模块，遵循现有包结构）

**Controller：** `VaultController` — `@RequestMapping("/api/vault")`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/vault/setup` | ⾸次激活：验证用户密码 → 设置保险箱密码 |
| POST | `/api/vault/unlock` | 验证保险箱密码 → Redis 标记 1 小时 |
| POST | `/api/vault/lock` | 清除 Redis unlock 标记 |
| GET | `/api/vault/status` | 查询是否已开启 + 当前是否解锁 |
| GET | `/api/vault/files/list` | 分页浏览保险箱文件/文件夹 |
| POST | `/api/vault/files/folder` | 在保险箱内新建文件夹 |
| POST | `/api/vault/files/upload` | 上传文件到保险箱 |
| GET | `/api/vault/files/download/{id}` | 下载保险箱文件 |
| PUT | `/api/vault/files/remove` | 将文件移出保险箱（is_vaulted = false） |

**核心 Service：`VaultService`**

```java
public interface IVaultService {
    void setup(Long userId, SetupVaultDTO dto);        // 验证用户密码 + 设置保险箱密码
    void unlock(Long userId, String vaultPassword);    // 解锁 1 小时
    void lock(Long userId);                            // 手动锁箱
    VaultStatusVO getStatus(Long userId);              // 查询状态
    IPage<ItemVO> listItems(Long userId, Long parentId, Integer page, Integer size);
    ItemVO createFolder(Long userId, CreateFolderDTO dto);
    ItemVO uploadFile(Long userId, Long parentId, MultipartFile file);
    void downloadFile(Long userId, Long itemId, HttpServletResponse response);
    void removeFromVault(Long userId, Long itemId);    // 移出保险箱
}
```

**安全设计：**
- **Session 保持：** 解锁成功后在 Redis 存 `vault:unlock:{userId} = "1"`，TTL = 3600 秒
- **Interceptor Check：** 新增 `VaultAccessInterceptor`，拦截 `/api/vault/**` 路径，校验 Redis unlock 标记
- **每次操作前自动校验：** 所有 vault 文件操作先检查 unlock 状态，未解锁返回 403

**与其他模块的交互：**

| 场景 | 处理 |
|------|------|
| **分享保险箱文件** | `ShareService.createShare()` 中新增校验：item.isVaulted = true → 抛出 BizException("机密文件箱中的文件无法分享") |
| **移出保险箱后分享** | 调用 `PUT /api/vault/files/remove` → is_vaulted = false → 即可正常分享 |
| **普通文件列表过滤** | `FileServiceImpl.listItems()` 查询中新增条件：`is_vaulted = false`，不显示保险箱文件 |
| **回收站** | 保险箱内的文件删除逻辑走回收站流程，但始终标记 is_vaulted |

**密码验证流程（首次）：**
```
POST /api/vault/setup
{
  "loginPassword": "xxx",     // 验证用户登录密码
  "vaultPassword": "yyy",     // 设置保险箱密码
  "confirmPassword": "yyy"
}
```

**解锁流程：**
```
POST /api/vault/unlock
{
  "password": "yyy"           // 保险箱密码
}
```

**修改 `WebConfig` 拦截器白名单：**
- `/api/vault/setup` 和 `/api/vault/unlock` 放行
- `/api/vault/**` 受 `VaultAccessInterceptor` 校验
- `/api/shares/access/**` 仍放行

---

## 3. 日历黄历 + 分享宜忌

### 3a. 模块结构

**新增模块：`netdisk-calendar`**（独立 Maven 子模块）

### 3b. 农历转换算法

**核心工具类：`LunarCalendarUtil`**

- 内置 1900-2100 年农历数据表（包含每月大小月、闰月信息）
- 实现公历 → 农历转换：年、月、日、是否闰月
- 计算天干地支（年柱、月柱、日柱、时柱）
- 计算生肖
- 计算二十四节气

**参考算法：** 基于经典农历转换算法（数据表驱动），已验证广泛使用。

### 3c. 建除十二神 + 宜忌映射

**计算规则：**
1. 根据农历月的地支确定"月建"
2. 根据日的地支与月建的关系确定"建除十二神"（建、除、满、平、定、执、破、危、成、收、开、闭）
3. 根据建除神煞映射到分享相关宜忌

**宜忌映射表：**

| 建除 | 宜 | 忌 |
|------|----|----|
| 建日 | 建立分享、上传文件 | — |
| 除日 | 取消分享、清理过期分享 | 新建分享、上传 |
| 满日 | 上传文件、存储备份 | 取消分享 |
| 平日 | 日常浏览、管理分享 | 重大操作 |
| 定日 | 创建长期/永久分享 | 更改分享设置 |
| 执日 | 文件备份、存档 | 删除文件 |
| 破日 | — | 所有分享操作、上传下载 |
| 危日 | 检查分享状态 | 修改分享、新建分享 |
| 成日 | 新建分享、上传文件 | 取消分享 |
| 收日 | 收取他人分享、下载文件 | 取消分享 |
| 开日 | 新建分享、开启新项目 | 关闭分享 |
| 闭日 | 文件归档、关闭分享 | 新建分享、上传 |

### 3d. API 设计

**Controller：** `CalendarController` — `@RequestMapping("/api/calendar")`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/calendar/today` | 获取当日完整黄历信息 |

**响应结构：**

```json
{
  "date": "2026-05-28",
  "lunar": {
    "year": "丙午",
    "month": "四月",
    "day": "十二",
    "zodiac": "马",
    "heavenlyStem": "丙",
    "earthlyBranch": "午",
    "jieQi": "小满后",
    "isLeapMonth": false,
    "jianChu": "成"
  },
  "yi": ["新建分享", "上传文件"],
  "ji": ["取消分享"],
  "shareAdvice": {
    "favorableForShare": true,
    "favorableForUpload": true,
    "favorableForDownload": true,
    "favorableForCancel": false
  }
}
```

### 3e. 前端集成

- 页面角落或侧边栏展示今日黄历
- 图标/颜色标识宜忌状态
- 创建分享时可根据黄历建议提示用户

---

## 4. 数据库变更汇总

| 变更类型 | 对象 | SQL |
|----------|------|-----|
| 新建表 | received_shares | 见 1b |
| 新建表 | user_vaults | 见 2a |
| 新增字段 | items.is_vaulted | `ALTER TABLE items ADD COLUMN is_vaulted TINYINT(1) NOT NULL DEFAULT 0;` |
| 新增索引 | items(owner_id, is_vaulted, parent_id) | `CREATE INDEX idx_items_vaulted ON items(owner_id, is_vaulted, parent_id);` |

---

## 5. Maven 模块变更

| 操作 | 模块名 | 说明 |
|------|--------|------|
| 修改 | netdisk-share | 改造现有分享逻辑 |
| 修改 | netdisk-file | Item 加 is_vaulted 字段，列表过滤 |
| 新增 | netdisk-vault | 机密文件箱 |
| 新增 | netdisk-calendar | 日历黄历 |
| 修改 | pom.xml (root) | 注册新模块 |

---

## 6. 安全与边界情况

| 场景 | 处理 |
|------|------|
| 未设置保险箱就解锁 | 返回错误"请先设置保险箱密码" |
| 已设置保险箱再次 setup | 返回错误"保险箱已存在，无法重复设置" |
| 保险箱密码连续输错 5 次 | Redis 锁定 10 分钟（复用现有模式） |
| 访问已过期的分享 | 返回 404 "分享链接不存在或已失效" |
| 分享保险箱文件 | 返回 400 "机密文件箱中的文件无法分享" |
| 移出保险箱后文件路径 | 文件仍保留在原位置，仅 is_vaulted 改为 false |
| 黄历节气不存在 | 返回空字符串 |
