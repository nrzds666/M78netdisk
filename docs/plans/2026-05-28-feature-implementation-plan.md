# M78 网盘功能增强 — 实现计划

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Use delegate_task per major task group.

**Goal:** 实现分享增强（过期预设+分类标记）、机密文件箱、日历黄历三个功能

**Architecture:** 新增 `netdisk-vault` 和 `netdisk-calendar` 两个 Maven 子模块，改造现有 `netdisk-share` 和 `netdisk-file` 模块。数据库新增 2 张表（received_shares, user_vaults），items 表加 1 字段（is_vaulted）。所有新增模块遵循现有包结构（com.m78.netdisk.{vault,calendar}）。

**Tech Stack:** Spring Boot 2.7.18 + MyBatis-Plus 3.7.5 + MySQL 8.0 + Redis + BCrypt + JWT

---

## Phase 1: 基础设施 — 数据库变更 + Maven 模块 + Item 改造

### Task 1: 数据库 DDL 更新（init.sql）

**Objective:** 在 init.sql 中添加 received_shares 表、user_vaults 表、items.is_vaulted 字段

**Files:**
- Modify: database/init.sql

**Step 1:** 在"3. 分享"章节的 shares 表之后，添加 received_shares 表

```sql
-- ============================================================
-- 3b. 接收分享记录
-- ============================================================

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

**Step 2:** 在"7."节（当前最后是 6. 存储节点），添加"7. 机密文件箱"

```sql
-- ============================================================
-- 7. 机密文件箱
-- ============================================================

CREATE TABLE IF NOT EXISTS user_vaults (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Step 3:** 在 items 表定义中，在 `version` 行之后添加：

```sql
    is_vaulted    TINYINT(1) NOT NULL DEFAULT 0,
```

**Step 4:** 添加 vault 相关索引，在 `idx_items_path` 之后：

```sql
CREATE INDEX idx_items_vaulted
    ON items(owner_id, is_vaulted, parent_id);
```

**Step 5:** 更新 schema-design.md 和 PROJECT.md 同步数据库变更

### Task 2: Item POJO 增加 isVaulted 字段

**Objective:** Item.java 加 isVaulted 字段，供所有模块引用

**Files:**
- Modify: netdisk-file/src/main/java/com/m78/netdisk/file/domain/po/Item.java

**Step:** 在 `private Integer version;` 之后添加：

```java
    private Boolean isVaulted;
```

### Task 3: 根 pom.xml 注册新模块

**Objective:** 把 netdisk-vault 和 netdisk-calendar 加入 Maven 父 POM

**Files:**
- Modify: pom.xml

**Step:** 在 `<modules>` 段中添加：

```xml
<module>netdisk-vault</module>
<module>netdisk-calendar</module>
```

### Task 4: 创建 netdisk-vault 模块骨架

**Objective:** 新建 netdisk-vault Maven 模块，包含基础依赖配置

**Files:**
- Create: netdisk-vault/pom.xml
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/package-info.java (empty)

**pom.xml 内容：**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.m78</groupId>
        <artifactId>netdisk-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>netdisk-vault</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.m78</groupId>
            <artifactId>netdisk-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.m78</groupId>
            <artifactId>netdisk-file</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.m78</groupId>
            <artifactId>netdisk-user</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
    </dependencies>
</project>
```

### Task 5: 创建 netdisk-calendar 模块骨架

**Objective:** 新建 netdisk-calendar Maven 模块

**Files:**
- Create: netdisk-calendar/pom.xml
- Create: netdisk-calendar/src/main/java/com/m78/netdisk/calendar/package-info.java

**pom.xml 内容：**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://pom.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.m78</groupId>
        <artifactId>netdisk-parent</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>netdisk-calendar</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.m78</groupId>
            <artifactId>netdisk-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
    </dependencies>
</project>
```

### Task 6: 普通文件列表过滤 vault 文件

**Objective:** ItemMapper SQL 和 FileServiceImpl 中，默认查询排除 is_vaulted = true 的文件

**Files:**
- Modify: netdisk-file/src/main/java/com/m78/netdisk/file/mapper/ItemMapper.java
- Modify: netdisk-file/src/main/resources/mapper/ItemMapper.xml

**Step 1:** 在 ItemMapper 中，修改 selectRootItems/selectChildren 的 SQL（在 is_deleted 条件后加上 `AND NOT is_vaulted`）

**Step 2:** 在 ItemMapper.xml 中检查并修改对应的 XML SQL

---

## Phase 2: 分享模块增强（过期预设 + 分类标记）

### Task 7: 创建 ShareExpire 枚举

**Objective:** 定义分享过期类型枚举

**Files:**
- Create: netdisk-share/src/main/java/com/m78/netdisk/share/domain/enums/ShareExpire.java

```java
package com.m78.netdisk.share.domain.enums;

public enum ShareExpire {
    ONE_DAY(24L, "一天"),
    ONE_WEEK(168L, "一周"),
    ONE_MONTH(720L, "一个月"),
    PERMANENT(null, "永久");

    private final Long hours;
    private final String label;

    ShareExpire(Long hours, String label) {
        this.hours = hours;
        this.label = label;
    }

    public Long getHours() { return hours; }
    public String getLabel() { return label; }

    public static ShareExpire fromType(String type) {
        if (type == null) return PERMANENT;
        for (ShareExpire e : values()) {
            if (e.name().equalsIgnoreCase(type)) return e;
        }
        return PERMANENT;
    }
}
```

### Task 8: 修改 CreateShareDTO

**Objective:** 替换 expireHours 为 expireType

**Files:**
- Modify: netdisk-share/src/main/java/com/m78/netdisk/share/domain/dto/CreateShareDTO.java

**Change:**
- 删除 `expireHours` 字段
- 新增 `expireType` 字段（String，默认 "PERMANENT"）

### Task 9: 修改 ShareServiceImpl.createShare()

**Objective:** 用 ShareExpire 枚举计算 expireAt

**Files:**
- Modify: netdisk-share/src/main/java/com/m78/netdisk/share/service/impl/ShareServiceImpl.java

**Change:**
- 在 createShare() 中，用 `ShareExpire.fromType(dto.getExpireType())` 获取枚举
- 若 hours != null 则 `expireAt = LocalDateTime.now().plusHours(hours)`
- 若 PERMANENT 则 expireAt = null（现有逻辑兼容）

### Task 10: 创建 ReceivedShare PO / Mapper

**Objective:** 接收分享记录的数据层

**Files:**
- Create: netdisk-share/src/main/java/com/m78/netdisk/share/domain/po/ReceivedShare.java
- Create: netdisk-share/src/main/java/com/m78/netdisk/share/mapper/ReceivedShareMapper.java

**ReceivedShare POJO：**
```java
package com.m78.netdisk.share.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("received_shares")
public class ReceivedShare {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long shareId;
    private Long itemId;
    private Long ownerId;
    private String accessToken;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime accessedAt;
}
```

**ReceivedShareMapper：**
```java
package com.m78.netdisk.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.share.domain.po.ReceivedShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReceivedShareMapper extends BaseMapper<ReceivedShare> {

    @Select("SELECT rs.*, s.share_token, s.permission, s.expire_at, s.created_at as share_created_at " +
            "FROM received_shares rs " +
            "JOIN shares s ON rs.share_id = s.id " +
            "WHERE rs.user_id = #{userId} " +
            "ORDER BY rs.accessed_at DESC")
    IPage<ReceivedShare> selectByUserId(Page<ReceivedShare> page, @Param("userId") Long userId);

    @Select("SELECT COUNT(1) FROM received_shares WHERE user_id = #{userId} AND share_id = #{shareId}")
    int countByUserAndShare(@Param("userId") Long userId, @Param("shareId") Long shareId);
}
```

### Task 11: 修改 accessShare() 记录接收

**Objective:** 在 accessShare 成功后，如果用户已登录则插入 received_shares

**Files:**
- Modify: netdisk-share/src/main/java/com/m78/netdisk/share/service/impl/ShareServiceImpl.java

**Change:** 在 accessShare() 方法末尾，`return toShareVO(share)` 之前添加：
```java
// 如果用户已登录，记录接收的分享（幂等）
Long currentUserId = com.m78.netdisk.common.utils.UserContext.getUserId();
if (currentUserId != null && !currentUserId.equals(share.getOwnerId())) {
    int exists = receivedShareMapper.countByUserAndShare(currentUserId, share.getId());
    if (exists == 0) {
        ReceivedShare rs = new ReceivedShare()
                .setUserId(currentUserId)
                .setShareId(share.getId())
                .setItemId(share.getItemId())
                .setOwnerId(share.getOwnerId())
                .setAccessToken(shareToken);
        receivedShareMapper.insert(rs);
    }
}
```

### Task 12: 新增 GET /api/shares/received 接口

**Objective:** 分页查询用户接收的分享列表

**Files:**
- Modify: netdisk-share/src/main/java/com/m78/netdisk/share/controller/ShareController.java
- Modify: netdisk-share/src/main/java/com/m78/netdisk/share/service/IShareService.java
- Modify: netdisk-share/src/main/java/com/m78/netdisk/share/service/impl/ShareServiceImpl.java

**Controller 新增：**
```java
@GetMapping("/received")
public R<IPage<ShareVO>> receivedShares(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size) {
    return R.ok(shareService.listReceivedShares(UserContext.getUserId(), page, size));
}
```

**IShareService 新增：**
```java
IPage<ShareVO> listReceivedShares(Long userId, Integer pageNum, Integer size);
```

**实现：** 通过 ReceivedShareMapper 分页查询，每条记录转成 ShareVO（isReceived=true）

### Task 13: ShareVO 增加 expireLabel 和 isReceived

**Objective:** VO 字段扩充

**Files:**
- Modify: netdisk-share/src/main/java/com/m78/netdisk/share/domain/vo/ShareVO.java

**新增字段：**
```java
private String expireLabel;
private Boolean isReceived;
```

**修改 toShareVO()：** 根据 expireAt 推导 expireLabel

---

## Phase 3: 机密文件箱模块

### Task 14: Vault 相关 DTOs

**Objective:** 创建 vault 模块的 DTO/VO 类

**Files:**
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/domain/dto/SetupVaultDTO.java
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/domain/dto/UnlockVaultDTO.java
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/domain/vo/VaultStatusVO.java

**SetupVaultDTO：**
```java
package com.m78.netdisk.vault.domain.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class SetupVaultDTO {
    @NotBlank(message = "登录密码不能为空")
    private String loginPassword;

    @NotBlank(message = "保险箱密码不能为空")
    @Size(min = 6, max = 32, message = "保险箱密码长度需在6-32位之间")
    private String vaultPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
```

**UnlockVaultDTO：**
```java
package com.m78.netdisk.vault.domain.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class UnlockVaultDTO {
    @NotBlank(message = "保险箱密码不能为空")
    private String password;
}
```

**VaultStatusVO：**
```java
package com.m78.netdisk.vault.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultStatusVO {
    private Boolean enabled;       // 是否已设置保险箱
    private Boolean unlocked;      // 当前是否已解锁
}
```

### Task 15: Vault PO/Mapper

**Objective:** user_vaults 表的数据层

**Files:**
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/domain/po/UserVault.java
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/mapper/UserVaultMapper.java

**UserVault：**
```java
package com.m78.netdisk.vault.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("user_vaults")
public class UserVault {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String passwordHash;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

**UserVaultMapper：** extends BaseMapper<UserVault>

### Task 16: IVaultService + VaultServiceImpl

**Objective:** 核心业务逻辑

**Files:**
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/service/IVaultService.java
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/service/impl/VaultServiceImpl.java

**IVaultService 接口：**
```java
package com.m78.netdisk.vault.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.file.domain.vo.ItemVO;
import com.m78.netdisk.vault.domain.dto.SetupVaultDTO;
import com.m78.netdisk.vault.domain.vo.VaultStatusVO;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;

public interface IVaultService {
    void setup(Long userId, SetupVaultDTO dto);
    void unlock(Long userId, String password);
    void lock(Long userId);
    VaultStatusVO getStatus(Long userId);
    IPage<ItemVO> listItems(Long userId, Long parentId, Integer page, Integer size);
    ItemVO createFolder(Long userId, Long parentId, String name);
    ItemVO uploadFile(Long userId, Long parentId, MultipartFile file);
    void downloadFile(Long userId, Long itemId, HttpServletResponse response);
    void removeFromVault(Long userId, Long itemId);
}
```

**VaultServiceImpl 关键逻辑：**
- setup：从 UserMapper 查用户密码 → BCrypt.match(loginPassword) → BCrypt.encode(vaultPassword) → 插入 user_vaults
- unlock：查 user_vaults → BCrypt.match(password) → Redis set "vault:unlock:{userId}" = "1" 3600秒
- lock：Redis delete "vault:unlock:{userId}"
- listItems：itemMapper 查 ownerId + is_vaulted = true + parentId
- createFolder：调用 itemMapper.insert，设置 isVaulted = true
- uploadFile：调用 storageService.store + itemMapper.insert，设置 isVaulted = true
- downloadFile：复用 FileController 的 streamFile 逻辑
- removeFromVault：item.setIsVaulted(false)，itemMapper.updateById

### Task 17: VaultAccessInterceptor

**Objective:** 拦截 vault 操作，校验 unlock 状态

**Files:**
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/config/VaultAccessInterceptor.java

```java
package com.m78.netdisk.vault.config;

import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class VaultAccessInterceptor implements HandlerInterceptor {

    private static final String VAULT_UNLOCK_KEY = "vault:unlock:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "需要登录");
        }
        String unlockFlag = redisTemplate.opsForValue().get(VAULT_UNLOCK_KEY + userId);
        if (!"1".equals(unlockFlag)) {
            throw new BizException(403, "保险箱未解锁，请先输入保险箱密码");
        }
        return true;
    }
}
```

### Task 18: VaultConfig 注册 Interceptor

**Objective:** 在 WebMvcConfigurer 中注册 VaultAccessInterceptor

**Files:**
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/config/VaultConfig.java

```java
package com.m78.netdisk.vault.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class VaultConfig implements WebMvcConfigurer {

    private final VaultAccessInterceptor vaultAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(vaultAccessInterceptor)
                .addPathPatterns("/api/vault/files/**", "/api/vault/lock");
    }
}
```

### Task 19: VaultController

**Objective:** 完整 Controller 实现

**Files:**
- Create: netdisk-vault/src/main/java/com/m78/netdisk/vault/controller/VaultController.java

**API 映射：**
- POST /api/vault/setup → setup()
- POST /api/vault/unlock → unlock()
- POST /api/vault/lock → lock()
- GET /api/vault/status → getStatus()
- GET /api/vault/files/list → listItems (with parentId, page, size params)
- POST /api/vault/files/folder → createFolder (with CreateFolderDTO)
- POST /api/vault/files/upload → uploadFile (MultipartFile + parentId)
- GET /api/vault/files/download/{id} → downloadFile
- PUT /api/vault/files/remove → removeFromVault (itemId param)

### Task 20: 分享模块校验 vault

**Objective:** 禁止分享保险箱内的文件

**Files:**
- Modify: netdisk-share/src/main/java/com/m78/netdisk/share/service/impl/ShareServiceImpl.java

**Change:** 在 createShare() 中，查询 item 后添加：
```java
if (Boolean.TRUE.equals(item.getIsVaulted())) {
    throw new BizException("机密文件箱中的文件无法分享");
}
```

---

## Phase 4: 日历黄历模块

### Task 21: 农历数据表

**Objective:** 内置 1900-2100 年农历数据

**Files:**
- Create: netdisk-calendar/src/main/java/com/m78/netdisk/calendar/util/LunarCalendarData.java

包含静态数组 `lunarInfo[]`：每个元素编码了该年的农历信息（闰月月份、大小月天数等），标准农历算法数据表。

### Task 22: LunarCalendarUtil — 公历转农历

**Objective:** 农历转换核心算法

**Files:**
- Create: netdisk-calendar/src/main/java/com/m78/netdisk/calendar/util/LunarCalendarUtil.java

**核心方法：**
```java
public static LunarDate solarToLunar(int year, int month, int day);
```

**LunarDate 内部类字段：**
- lunarYear, lunarMonth, lunarDay (int)
- isLeap (boolean)
- heavenlyStem (String) — 年天干
- earthlyBranch (String) — 年地支
- zodiac (String) — 生肖
- monthHeavenlyStem, monthEarthlyBranch — 月干支
- dayHeavenlyStem, dayEarthlyBranch — 日干支
- jieQi (String) — 节气/节气段

### Task 23: 建除十二神计算

**Objective:** 根据农历月/日推算当日建除

**Files:**
- Add to: LunarCalendarUtil.java

**方法：**
```java
public static String getJianChu(int month, int day);
```

算法：根据月地支（寅=正月）确定月建，日地支与月建的关系确定建除。

### Task 24: 宜忌映射 + CalendarController

**Objective:** 根据建除返回宜忌列表，暴露 API

**Files:**
- Create: netdisk-calendar/src/main/java/com/m78/netdisk/calendar/domain/vo/CalendarVO.java
- Create: netdisk-calendar/src/main/java/com/m78/netdisk/calendar/controller/CalendarController.java

**CalendarVO：**
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarVO {
    private String date;                 // 公历 yyyy-MM-dd
    private LunarInfo lunar;
    private List<String> yi;             // 宜
    private List<String> ji;             // 忌
    private ShareAdvice shareAdvice;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class LunarInfo {
    private String year;
    private String month;
    private String day;
    private String zodiac;
    private String heavenlyStem;
    private String earthlyBranch;
    private String monthStem;
    private String monthBranch;
    private String dayStem;
    private String dayBranch;
    private String jieQi;
    private boolean isLeapMonth;
    private String jianChu;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class ShareAdvice {
    private boolean favorableForShare;
    private boolean favorableForUpload;
    private boolean favorableForDownload;
    private boolean favorableForCancel;
}
```

**宜忌映射逻辑：**
```java
private static final Map<String, List<String>> YI_MAP = Map.of(
    "建", List.of("新建分享", "上传文件"),
    "除", List.of("取消分享", "清理过期分享"),
    // ... 完整 12 建除映射
);

private static final Map<String, List<String>> JI_MAP = Map.of(
    "破", List.of("新建分享", "上传文件", "下载文件", "取消分享"),
    // ... 完整 12 建除映射
);
```

**CalendarController：**
```java
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    @GetMapping("/today")
    public R<CalendarVO> today() {
        LocalDate now = LocalDate.now();
        LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(
                now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        String jianChu = LunarCalendarUtil.getJianChu(lunar.lunarMonth, lunar.lunarDay);
        // 构建 CalendarVO 返回
    }
}
```

---

## 执行计划

1. **Phase 1 (Task 1-6):** 基础设施 — 先完成，后续所有代码依赖
2. **Phase 2 (Task 7-13):** 分享增强 — 独立，不依赖 Phase 3/4
3. **Phase 3 (Task 14-20):** 机密文件箱 — 依赖 Phase 1 的 Item.isVaulted
4. **Phase 4 (Task 21-24):** 日历黄历 — 完全独立，可并行
