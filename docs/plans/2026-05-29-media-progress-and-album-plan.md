# Media Progress & Album Module Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Add media playback progress tracking and a full album (photo/video gallery) module to M78 NetDisk.

**Architecture:**
- Media progress goes into existing `netdisk-file` module (new table + mapper + service methods)
- Album feature is a new `netdisk-album` module (controller → service → mapper → po), same pattern as `netdisk-vault`/`netdisk-calendar`
- Database changes in `database/init.sql`

**Tech Stack:** Spring Boot 2.7.18, MyBatis-Plus 3.5.7, MySQL 8.0

---

## Phase 1: Database Schema

### Task 1: Add media_progress, albums, album_items tables to init.sql

**Objective:** Append new table DDL + indexes to the existing init.sql

**Files:**
- Modify: `database/init.sql` (append after line 246, before the final `-- 完成` comment)

**Step 1: Read the current end of init.sql**

Read the file to find the exact insertion point.

**Step 2: Append media_progress table**

Append (after the vault section, before the final `-- ============================================================`):

```sql
-- ============================================================
-- 8. 媒体播放进度
-- ============================================================

CREATE TABLE IF NOT EXISTS media_progress (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    item_id          BIGINT NOT NULL,
    progress_seconds INT NOT NULL DEFAULT 0,
    total_duration   INT NOT NULL DEFAULT 0,
    finished         TINYINT(1) NOT NULL DEFAULT 0,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (user_id, item_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_media_progress_user ON media_progress(user_id);
CREATE INDEX idx_media_progress_item ON media_progress(item_id);


-- ============================================================
-- 9. 相册
-- ============================================================

CREATE TABLE IF NOT EXISTS albums (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    name          VARCHAR(128) NOT NULL,
    cover_item_id BIGINT,
    description   TEXT,
    sort_order    INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (cover_item_id) REFERENCES items(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_albums_user ON albums(user_id, sort_order);

CREATE TABLE IF NOT EXISTS album_items (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    album_id  BIGINT NOT NULL,
    item_id   BIGINT NOT NULL,
    added_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (album_id, item_id),
    FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_album_items_album ON album_items(album_id, added_at DESC);
CREATE INDEX idx_album_items_item ON album_items(item_id);
```

**Step 3: Verify**

Read the file to ensure proper formatting.

**Step 4: Commit**

```bash
git add database/init.sql
git commit -m "feat: add media_progress, albums, album_items tables"
```

---

## Phase 2: Media Progress (netdisk-file module)

### Task 2: Create MediaProgress PO entity

**Objective:** Create the MyBatis-Plus entity for media_progress table

**Files:**
- Create: `netdisk-file/src/main/java/com/m78/netdisk/file/domain/po/MediaProgress.java`

```java
package com.m78.netdisk.file.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("media_progress")
public class MediaProgress {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    private Integer progressSeconds;

    private Integer totalDuration;

    private Boolean finished;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

**Commit:**
```bash
git add netdisk-file/src/main/java/com/m78/netdisk/file/domain/po/MediaProgress.java
git commit -m "feat: add MediaProgress PO entity"
```

### Task 3: Create MediaProgressMapper

**Objective:** MyBatis-Plus mapper for media_progress

**Files:**
- Create: `netdisk-file/src/main/java/com/m78/netdisk/file/mapper/MediaProgressMapper.java`

```java
package com.m78.netdisk.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.file.domain.po.MediaProgress;

public interface MediaProgressMapper extends BaseMapper<MediaProgress> {
}
```

**Commit:**
```bash
git add netdisk-file/src/main/java/com/m78/netdisk/file/mapper/MediaProgressMapper.java
git commit -m "feat: add MediaProgressMapper"
```

### Task 4: Create DTO and VO for progress

**Files:**
- Create: `netdisk-file/src/main/java/com/m78/netdisk/file/domain/dto/SaveProgressDTO.java`
- Create: `netdisk-file/src/main/java/com/m78/netdisk/file/domain/vo/MediaProgressVO.java`

```java
// SaveProgressDTO.java
package com.m78.netdisk.file.domain.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class SaveProgressDTO {
    @NotNull
    @Min(0)
    private Integer progressSeconds;

    @NotNull
    @Min(0)
    private Integer totalDuration;

    private Boolean finished;
}
```

```java
// MediaProgressVO.java
package com.m78.netdisk.file.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaProgressVO {
    private Long itemId;
    private Integer progressSeconds;
    private Integer totalDuration;
    private Boolean finished;
    private String updatedAt;
}
```

**Commit:**
```bash
git add netdisk-file/src/main/java/com/m78/netdisk/file/domain/dto/SaveProgressDTO.java
git add netdisk-file/src/main/java/com/m78/netdisk/file/domain/vo/MediaProgressVO.java
git commit -m "feat: add SaveProgressDTO and MediaProgressVO"
```

### Task 5: Add progress methods to IFileService interface

**Objective:** Define getProgress and saveProgress in the interface

**Files:**
- Modify: `netdisk-file/src/main/java/com/m78/netdisk/file/service/IFileService.java`

Add after the existing methods:

```java
MediaProgressVO getProgress(Long userId, Long itemId);

MediaProgressVO saveProgress(Long userId, Long itemId, SaveProgressDTO dto);
```

Also add the import:
```java
import com.m78.netdisk.file.domain.dto.SaveProgressDTO;
import com.m78.netdisk.file.domain.vo.MediaProgressVO;
```

**Commit:**
```bash
git add netdisk-file/src/main/java/com/m78/netdisk/file/service/IFileService.java
git commit -m "feat: add getProgress/saveProgress to IFileService"
```

### Task 6: Implement progress in FileServiceImpl

**Objective:** Implement the progress methods

**Files:**
- Modify: `netdisk-file/src/main/java/com/m78/netdisk/file/service/impl/FileServiceImpl.java`

Inject MediaProgressMapper. Add two methods:

```java
private final MediaProgressMapper mediaProgressMapper;

@Override
public MediaProgressVO getProgress(Long userId, Long itemId) {
    // Validate item ownership
    Item item = itemMapper.selectById(itemId);
    validateItemAccess(item, userId);
    // Validate media type
    if (item.getMimeType() == null ||
        !(item.getMimeType().startsWith("video/") ||
          item.getMimeType().startsWith("audio/") ||
          item.getMimeType().startsWith("image/"))) {
        throw new BizException("不是媒体文件");
    }

    MediaProgress mp = mediaProgressMapper.selectOne(
        new LambdaQueryWrapper<MediaProgress>()
            .eq(MediaProgress::getUserId, userId)
            .eq(MediaProgress::getItemId, itemId));
    if (mp == null) {
        return MediaProgressVO.builder()
            .itemId(itemId)
            .progressSeconds(0)
            .totalDuration(0)
            .finished(false)
            .build();
    }
    return toMediaProgressVO(mp);
}

@Override
@Transactional
public MediaProgressVO saveProgress(Long userId, Long itemId, SaveProgressDTO dto) {
    Item item = itemMapper.selectById(itemId);
    validateItemAccess(item, userId);
    if (item.getMimeType() == null ||
        !(item.getMimeType().startsWith("video/") ||
          item.getMimeType().startsWith("audio/") ||
          item.getMimeType().startsWith("image/"))) {
        throw new BizException("不是媒体文件");
    }

    MediaProgress mp = mediaProgressMapper.selectOne(
        new LambdaQueryWrapper<MediaProgress>()
            .eq(MediaProgress::getUserId, userId)
            .eq(MediaProgress::getItemId, itemId));
    boolean finished = dto.getFinished() != null && dto.getFinished();

    if (mp == null) {
        mp = new MediaProgress()
            .setUserId(userId)
            .setItemId(itemId)
            .setProgressSeconds(dto.getProgressSeconds())
            .setTotalDuration(dto.getTotalDuration())
            .setFinished(finished);
        mediaProgressMapper.insert(mp);
    } else {
        mp.setProgressSeconds(dto.getProgressSeconds())
          .setTotalDuration(dto.getTotalDuration())
          .setFinished(finished);
        mediaProgressMapper.updateById(mp);
    }

    return toMediaProgressVO(mp);
}

private MediaProgressVO toMediaProgressVO(MediaProgress mp) {
    return MediaProgressVO.builder()
        .itemId(mp.getItemId())
        .progressSeconds(mp.getProgressSeconds())
        .totalDuration(mp.getTotalDuration())
        .finished(mp.getFinished())
        .updatedAt(mp.getUpdatedAt() != null ? mp.getUpdatedAt().toString() : null)
        .build();
}
```

Also add a helper `validateItemAccess` (or reuse existing `validateOwner` pattern if it exists — check the code). Looking at the existing code, there's a `validateOwner(item, ownerId)` method. I'll use that.

Wait, let me re-check - does FileServiceImpl already have validateOwner? Let me look.

Looking at the code we read earlier, line 147: `validateOwner(item, ownerId);` — yes, it exists.

But for progress, we also want to check the media type. I'll add a `validateMediaItem` private method.

Add imports:
```java
import com.m78.netdisk.file.domain.dto.SaveProgressDTO;
import com.m78.netdisk.file.domain.vo.MediaProgressVO;
import com.m78.netdisk.file.mapper.MediaProgressMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
```

**Commit:**
```bash
git add netdisk-file/src/main/java/com/m78/netdisk/file/service/impl/FileServiceImpl.java
git commit -m "feat: implement media progress tracking in FileServiceImpl"
```

### Task 7: Add progress endpoints to FileController

**Objective:** Expose progress APIs

**Files:**
- Modify: `netdisk-file/src/main/java/com/m78/netdisk/file/controller/FileController.java`

Add endpoints:

```java
@GetMapping("/progress/{itemId}")
public R<MediaProgressVO> getProgress(@PathVariable Long itemId) {
    return R.ok(fileService.getProgress(UserContext.getUserId(), itemId));
}

@PutMapping("/progress/{itemId}")
public R<MediaProgressVO> saveProgress(@PathVariable Long itemId,
                                        @Valid @RequestBody SaveProgressDTO dto) {
    return R.ok(fileService.saveProgress(UserContext.getUserId(), itemId, dto));
}
```

**Commit:**
```bash
git add netdisk-file/src/main/java/com/m78/netdisk/file/controller/FileController.java
git commit -m "feat: add media progress API endpoints"
```

---

## Phase 3: Album Module Setup

### Task 8: Create netdisk-album/pom.xml

**Objective:** Maven module for the album feature

**Files:**
- Create: `netdisk-album/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.m78</groupId>
        <artifactId>m78-netdisk</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>netdisk-album</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.m78</groupId>
            <artifactId>netdisk-common</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>com.m78</groupId>
            <artifactId>netdisk-file</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
        </dependency>
        <!-- validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
    </dependencies>
</project>
```

**Commit:**
```bash
git add netdisk-album/pom.xml
mkdir -p netdisk-album/src/main/java/com/m78/netdisk/album
mkdir -p netdisk-album/src/main/java/com/m78/netdisk/album/controller
mkdir -p netdisk-album/src/main/java/com/m78/netdisk/album/service
mkdir -p netdisk-album/src/main/java/com/m78/netdisk/album/service/impl
mkdir -p netdisk-album/src/main/java/com/m78/netdisk/album/domain/po
mkdir -p netdisk-album/src/main/java/com/m78/netdisk/album/domain/vo
mkdir -p netdisk-album/src/main/java/com/m78/netdisk/album/domain/dto
mkdir -p netdisk-album/src/main/java/com/m78/netdisk/album/mapper
git commit -m "feat: add netdisk-album module with pom.xml"
```

### Task 9: Register module in root pom.xml

**Objective:** Add netdisk-album to the parent POM module list

**Files:**
- Modify: `pom.xml`

Add `<module>netdisk-album</module>` after `<module>netdisk-calendar</module>`.

**Commit:**
```bash
git add pom.xml
git commit -m "feat: register netdisk-album module in root pom"
```

### Task 10: Update SwaggerConfig for album group

**Objective:** Add GroupedOpenApi bean for album API

**Files:**
- Modify: `netdisk-bootstrap/src/main/java/com/m78/netdisk/config/SwaggerConfig.java`

Add after the vaultApi bean:

```java
@Bean
public GroupedOpenApi albumApi() {
    return GroupedOpenApi.builder()
            .group("相册模块")
            .pathsToMatch("/api/albums/**")
            .build();
}
```

**Commit:**
```bash
git add netdisk-bootstrap/src/main/java/com/m78/netdisk/config/SwaggerConfig.java
git commit -m "feat: add album API group to SwaggerConfig"
```

---

## Phase 4: Album Domain Objects

### Task 11: Create Album PO

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/domain/po/Album.java`

```java
package com.m78.netdisk.album.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("albums")
public class Album {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private Long coverItemId;

    private String description;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/domain/po/Album.java
git commit -m "feat: add Album PO entity"
```

### Task 12: Create AlbumItem PO

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/domain/po/AlbumItem.java`

```java
package com.m78.netdisk.album.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("album_items")
public class AlbumItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long albumId;

    private Long itemId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime addedAt;
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/domain/po/AlbumItem.java
git commit -m "feat: add AlbumItem PO entity"
```

### Task 13: Create DTOs

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/domain/dto/CreateAlbumDTO.java`
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/domain/dto/UpdateAlbumDTO.java`
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/domain/dto/AddItemsDTO.java`

```java
// CreateAlbumDTO.java
package com.m78.netdisk.album.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class CreateAlbumDTO {
    @NotBlank(message = "相册名称不能为空")
    @Size(max = 128, message = "相册名称最长128个字符")
    private String name;

    private Long coverItemId;

    @Size(max = 1000)
    private String description;

    private List<Long> itemIds; // optional initial items
}
```

```java
// UpdateAlbumDTO.java
package com.m78.netdisk.album.domain.dto;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class UpdateAlbumDTO {
    @Size(max = 128, message = "相册名称最长128个字符")
    private String name;

    private Long coverItemId;

    @Size(max = 1000)
    private String description;

    private Integer sortOrder;
}
```

```java
// AddItemsDTO.java
package com.m78.netdisk.album.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class AddItemsDTO {
    @NotEmpty(message = "文件ID列表不能为空")
    private List<Long> itemIds;
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/domain/dto/
git commit -m "feat: add album DTOs"
```

### Task 14: Create AlbumVO

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/domain/vo/AlbumVO.java`

```java
package com.m78.netdisk.album.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumVO {
    private Long id;
    private String name;
    private Long coverItemId;
    private String coverThumbnailKey;
    private String description;
    private Integer itemCount;
    private Integer sortOrder;
    private String createdAt;
    private String updatedAt;
    private List<AlbumItemVO> items; // only populated in detail view
}
```

```java
// AlbumItemVO.java
package com.m78.netdisk.album.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumItemVO {
    private Long id;
    private Long itemId;
    private String name;
    private String mimeType;
    private Long size;
    private String thumbnailKey;
    private String addedAt;
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/domain/vo/
git commit -m "feat: add AlbumVO and AlbumItemVO"
```

---

## Phase 5: Album Mappers

### Task 15: Create AlbumMapper

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/mapper/AlbumMapper.java`

```java
package com.m78.netdisk.album.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.album.domain.po.Album;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AlbumMapper extends BaseMapper<Album> {

    @Select("SELECT COUNT(*) FROM album_items WHERE album_id = #{albumId}")
    int countItems(@Param("albumId") Long albumId);

    @Select("SELECT a.* FROM albums a WHERE a.user_id = #{userId} ORDER BY a.sort_order ASC, a.created_at DESC")
    IPage<Album> selectByUserId(Page<?> page, @Param("userId") Long userId);

    @Select("SELECT ai.item_id FROM album_items ai WHERE ai.album_id = #{albumId} ORDER BY ai.added_at DESC LIMIT 1")
    Long selectLatestItemId(@Param("albumId") Long albumId);
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/mapper/AlbumMapper.java
git commit -m "feat: add AlbumMapper"
```

### Task 16: Create AlbumItemMapper

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/mapper/AlbumItemMapper.java`

```java
package com.m78.netdisk.album.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.album.domain.po.AlbumItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AlbumItemMapper extends BaseMapper<AlbumItem> {

    @Select("SELECT ai.*, i.name, i.mime_type, i.size, i.thumbnail_key " +
            "FROM album_items ai " +
            "JOIN items i ON i.id = ai.item_id " +
            "WHERE ai.album_id = #{albumId} " +
            "ORDER BY ai.added_at DESC")
    IPage<AlbumItemVO> selectItemsByAlbumId(Page<?> page, @Param("albumId") Long albumId);

    @Select("SELECT i.thumbnail_key FROM album_items ai " +
            "JOIN items i ON i.id = ai.item_id " +
            "WHERE ai.album_id = #{albumId} " +
            "ORDER BY ai.added_at DESC LIMIT 1")
    String selectLatestThumbnailKey(@Param("albumId") Long albumId);
}
```

Wait, this approach uses a join and returns AlbumItemVO directly from the mapper, which uses MyBatis result mapping. It might be cleaner to use MyBatis `@Results` or just do it in the service layer. Let me reconsider.

Actually, for simplicity and following the existing project patterns (which use LambdaQueryWrapper everywhere), I'll keep it simpler: the mapper does basic CRUD, and the service layer does the joins/fetching.

Let me revise:

```java
package com.m78.netdisk.album.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.m78.netdisk.album.domain.po.AlbumItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AlbumItemMapper extends BaseMapper<AlbumItem> {

    @Select("SELECT item_id FROM album_items WHERE album_id = #{albumId} ORDER BY added_at DESC")
    List<Long> selectItemIdsByAlbumId(@Param("albumId") Long albumId);

    @Select("SELECT item_id FROM album_items WHERE album_id = #{albumId} ORDER BY added_at DESC LIMIT 1")
    Long selectLatestItemId(@Param("albumId") Long albumId);
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/mapper/AlbumItemMapper.java
git commit -m "feat: add AlbumItemMapper"
```

---

## Phase 6: Album Service

### Task 17: Create IAlbumService interface

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/service/IAlbumService.java`

```java
package com.m78.netdisk.album.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.album.domain.dto.CreateAlbumDTO;
import com.m78.netdisk.album.domain.dto.UpdateAlbumDTO;
import com.m78.netdisk.album.domain.dto.AddItemsDTO;
import com.m78.netdisk.album.domain.vo.AlbumVO;
import com.m78.netdisk.album.domain.vo.AlbumItemVO;

import java.util.List;

public interface IAlbumService {

    AlbumVO createAlbum(Long userId, CreateAlbumDTO dto);

    void deleteAlbum(Long userId, Long albumId);

    AlbumVO updateAlbum(Long userId, Long albumId, UpdateAlbumDTO dto);

    IPage<AlbumVO> listAlbums(Long userId, Integer page, Integer size);

    AlbumVO getAlbumDetail(Long userId, Long albumId, Integer page, Integer size);

    void addItems(Long userId, Long albumId, AddItemsDTO dto);

    void removeItems(Long userId, Long albumId, List<Long> itemIds);

    AlbumVO setCover(Long userId, Long albumId, Long itemId);
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/service/IAlbumService.java
git commit -m "feat: add IAlbumService interface"
```

### Task 18: Implement AlbumServiceImpl

**Objective:** Full implementation of all album service methods

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/service/impl/AlbumServiceImpl.java`

```java
package com.m78.netdisk.album.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.m78.netdisk.album.domain.dto.AddItemsDTO;
import com.m78.netdisk.album.domain.dto.CreateAlbumDTO;
import com.m78.netdisk.album.domain.dto.UpdateAlbumDTO;
import com.m78.netdisk.album.domain.po.Album;
import com.m78.netdisk.album.domain.po.AlbumItem;
import com.m78.netdisk.album.domain.vo.AlbumItemVO;
import com.m78.netdisk.album.domain.vo.AlbumVO;
import com.m78.netdisk.album.mapper.AlbumItemMapper;
import com.m78.netdisk.album.mapper.AlbumMapper;
import com.m78.netdisk.album.service.IAlbumService;
import com.m78.netdisk.common.exception.BizException;
import com.m78.netdisk.file.domain.po.Item;
import com.m78.netdisk.file.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlbumServiceImpl implements IAlbumService {

    private final AlbumMapper albumMapper;
    private final AlbumItemMapper albumItemMapper;
    private final ItemMapper itemMapper;

    @Override
    @Transactional
    public AlbumVO createAlbum(Long userId, CreateAlbumDTO dto) {
        Album album = new Album()
                .setUserId(userId)
                .setName(dto.getName().trim())
                .setCoverItemId(dto.getCoverItemId())
                .setDescription(dto.getDescription());
        albumMapper.insert(album);

        // Optional: add initial items
        if (dto.getItemIds() != null && !dto.getItemIds().isEmpty()) {
            addItemAssociations(userId, album.getId(), dto.getItemIds());
            // If no cover explicitly set, use first item
            if (dto.getCoverItemId() == null) {
                Long firstId = albumItemMapper.selectLatestItemId(album.getId());
                if (firstId != null) {
                    album.setCoverItemId(firstId);
                    albumMapper.updateById(album);
                }
            }
        }

        return toAlbumVO(album);
    }

    @Override
    @Transactional
    public void deleteAlbum(Long userId, Long albumId) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);
        albumMapper.deleteById(albumId);
        // album_items cascade deleted by FK
        log.info("相册已删除: userId={}, albumId={}", userId, albumId);
    }

    @Override
    @Transactional
    public AlbumVO updateAlbum(Long userId, Long albumId, UpdateAlbumDTO dto) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);

        if (dto.getName() != null) {
            album.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            album.setDescription(dto.getDescription());
        }
        if (dto.getCoverItemId() != null) {
            // Verify the item exists and belongs to user
            Item coverItem = itemMapper.selectById(dto.getCoverItemId());
            if (coverItem == null || !coverItem.getOwnerId().equals(userId)) {
                throw new BizException("封面图片不存在");
            }
            album.setCoverItemId(dto.getCoverItemId());
        }
        if (dto.getSortOrder() != null) {
            album.setSortOrder(dto.getSortOrder());
        }

        albumMapper.updateById(album);
        return toAlbumVO(album);
    }

    @Override
    public IPage<AlbumVO> listAlbums(Long userId, Integer pageNum, Integer size) {
        Page<Album> page = new Page<>(pageNum, Math.min(size, 100));
        IPage<Album> albumPage = albumMapper.selectByUserId(page, userId);
        return albumPage.convert(this::toAlbumVO);
    }

    @Override
    public AlbumVO getAlbumDetail(Long userId, Long albumId, Integer pageNum, Integer size) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);

        // Get paginated item IDs from the album
        Page<AlbumItem> itemPage = new Page<>(pageNum, Math.min(size, 100));
        IPage<AlbumItem> aiPage = albumItemMapper.selectPage(itemPage,
                new LambdaQueryWrapper<AlbumItem>()
                        .eq(AlbumItem::getAlbumId, albumId)
                        .orderByDesc(AlbumItem::getAddedAt));

        // Fetch item details
        List<Long> itemIds = aiPage.getRecords().stream()
                .map(AlbumItem::getItemId)
                .collect(Collectors.toList());

        List<AlbumItemVO> items = new ArrayList<>();
        if (!itemIds.isEmpty()) {
            List<Item> itemList = itemMapper.selectBatchIds(itemIds);
            // Build a map for quick lookup
            java.util.Map<Long, Item> itemMap = itemList.stream()
                    .collect(Collectors.toMap(Item::getId, i -> i));
            for (AlbumItem ai : aiPage.getRecords()) {
                Item item = itemMap.get(ai.getItemId());
                if (item != null && !item.getIsDeleted()) {
                    items.add(AlbumItemVO.builder()
                            .itemId(item.getId())
                            .name(item.getName())
                            .mimeType(item.getMimeType())
                            .size(item.getSize())
                            .thumbnailKey(item.getThumbnailKey())
                            .addedAt(ai.getAddedAt() != null ? ai.getAddedAt().toString() : null)
                            .build());
                }
            }
        }

        AlbumVO vo = toAlbumVO(album);
        vo.setItems(items);
        // Override itemCount with actual page total
        vo.setItemCount((int) aiPage.getTotal());
        return vo;
    }

    @Override
    @Transactional
    public void addItems(Long userId, Long albumId, AddItemsDTO dto) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);
        addItemAssociations(userId, albumId, dto.getItemIds());

        // Auto-set cover if not set
        if (album.getCoverItemId() == null) {
            Long latestId = albumItemMapper.selectLatestItemId(albumId);
            if (latestId != null) {
                album.setCoverItemId(latestId);
                albumMapper.updateById(album);
            }
        }
    }

    @Override
    @Transactional
    public void removeItems(Long userId, Long albumId, List<Long> itemIds) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);

        albumItemMapper.delete(new LambdaQueryWrapper<AlbumItem>()
                .eq(AlbumItem::getAlbumId, albumId)
                .in(AlbumItem::getItemId, itemIds));

        // If removed item was the cover, clear or auto-set
        if (album.getCoverItemId() != null && itemIds.contains(album.getCoverItemId())) {
            Long latestId = albumItemMapper.selectLatestItemId(albumId);
            album.setCoverItemId(latestId);
            albumMapper.updateById(album);
        }
    }

    @Override
    @Transactional
    public AlbumVO setCover(Long userId, Long albumId, Long itemId) {
        Album album = albumMapper.selectById(albumId);
        validateOwnership(album, userId);

        // Verify item belongs to this album
        Long count = albumItemMapper.selectCount(
                new LambdaQueryWrapper<AlbumItem>()
                        .eq(AlbumItem::getAlbumId, albumId)
                        .eq(AlbumItem::getItemId, itemId));
        if (count == 0) {
            throw new BizException("该文件不在相册中");
        }

        album.setCoverItemId(itemId);
        albumMapper.updateById(album);
        return toAlbumVO(album);
    }

    // ==================== Private helpers ====================

    private void validateOwnership(Album album, Long userId) {
        if (album == null) {
            throw new BizException("相册不存在");
        }
        if (!album.getUserId().equals(userId)) {
            throw new BizException("无权操作此相册");
        }
    }

    private void addItemAssociations(Long userId, Long albumId, List<Long> itemIds) {
        // Validate each item: exists, belongs to user, is image/video
        List<Item> items = itemMapper.selectBatchIds(itemIds);
        java.util.Map<Long, Item> itemMap = items.stream()
                .collect(Collectors.toMap(Item::getId, i -> i));

        for (Long itemId : itemIds) {
            Item item = itemMap.get(itemId);
            if (item == null || !item.getOwnerId().equals(userId)) {
                throw new BizException("文件不存在: id=" + itemId);
            }
            if (item.getIsDeleted()) {
                throw new BizException("文件已被删除: " + item.getName());
            }
            String mime = item.getMimeType();
            if (mime == null || !(mime.startsWith("image/") || mime.startsWith("video/"))) {
                throw new BizException("只能添加图片或视频文件: " + item.getName());
            }
        }

        // Batch insert (ignore duplicates)
        for (Long itemId : itemIds) {
            try {
                AlbumItem ai = new AlbumItem()
                        .setAlbumId(albumId)
                        .setItemId(itemId);
                albumItemMapper.insert(ai);
            } catch (DuplicateKeyException e) {
                // Skip duplicates silently
                log.debug("文件已在相册中: itemId={}, albumId={}", itemId, albumId);
            }
        }
    }

    private AlbumVO toAlbumVO(Album album) {
        if (album == null) return null;

        // Get cover thumbnail
        String coverThumbnailKey = null;
        Long coverItemId = album.getCoverItemId();
        if (coverItemId != null) {
            Item coverItem = itemMapper.selectById(coverItemId);
            if (coverItem != null) {
                coverThumbnailKey = coverItem.getThumbnailKey();
            }
        } else {
            // Auto-pick latest item's thumbnail
            coverThumbnailKey = albumItemMapper.selectLatestThumbnailKey(album.getId());
        }

        // Get item count
        int itemCount = albumMapper.countItems(album.getId());

        return AlbumVO.builder()
                .id(album.getId())
                .name(album.getName())
                .coverItemId(album.getCoverItemId())
                .coverThumbnailKey(coverThumbnailKey)
                .description(album.getDescription())
                .itemCount(itemCount)
                .sortOrder(album.getSortOrder())
                .createdAt(album.getCreatedAt() != null ? album.getCreatedAt().toString() : null)
                .updatedAt(album.getUpdatedAt() != null ? album.getUpdatedAt().toString() : null)
                .build();
    }
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/service/impl/AlbumServiceImpl.java
git commit -m "feat: implement AlbumServiceImpl"
```

---

## Phase 7: Album Controller

### Task 19: Create AlbumController

**Objective:** REST controller exposing all album APIs

**Files:**
- Create: `netdisk-album/src/main/java/com/m78/netdisk/album/controller/AlbumController.java`

```java
package com.m78.netdisk.album.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.m78.netdisk.album.domain.dto.AddItemsDTO;
import com.m78.netdisk.album.domain.dto.CreateAlbumDTO;
import com.m78.netdisk.album.domain.dto.UpdateAlbumDTO;
import com.m78.netdisk.album.domain.vo.AlbumVO;
import com.m78.netdisk.album.service.IAlbumService;
import com.m78.netdisk.common.domain.R;
import com.m78.netdisk.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
public class AlbumController {

    private final IAlbumService albumService;

    @PostMapping
    public R<AlbumVO> createAlbum(@Valid @RequestBody CreateAlbumDTO dto) {
        return R.ok(albumService.createAlbum(UserContext.getUserId(), dto));
    }

    @GetMapping
    public R<IPage<AlbumVO>> listAlbums(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(albumService.listAlbums(UserContext.getUserId(), page, size));
    }

    @GetMapping("/{id}")
    public R<AlbumVO> getAlbumDetail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return R.ok(albumService.getAlbumDetail(UserContext.getUserId(), id, page, size));
    }

    @PutMapping("/{id}")
    public R<AlbumVO> updateAlbum(@PathVariable Long id,
                                   @Valid @RequestBody UpdateAlbumDTO dto) {
        return R.ok(albumService.updateAlbum(UserContext.getUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public R<Void> deleteAlbum(@PathVariable Long id) {
        albumService.deleteAlbum(UserContext.getUserId(), id);
        return R.ok();
    }

    @PostMapping("/{id}/items")
    public R<Void> addItems(@PathVariable Long id,
                             @Valid @RequestBody AddItemsDTO dto) {
        albumService.addItems(UserContext.getUserId(), id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id}/items")
    public R<Void> removeItems(@PathVariable Long id,
                                @RequestParam List<Long> itemIds) {
        albumService.removeItems(UserContext.getUserId(), id, itemIds);
        return R.ok();
    }

    @PutMapping("/{id}/cover")
    public R<AlbumVO> setCover(@PathVariable Long id,
                                @RequestParam Long itemId) {
        return R.ok(albumService.setCover(UserContext.getUserId(), id, itemId));
    }
}
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/controller/AlbumController.java
git commit -m "feat: add AlbumController REST API"
```

---

## Phase 8: Update AlbumItemMapper (add missing method)

### Task 20: Update AlbumItemMapper with thumbnail query

In the AlbumItemMapper, I referenced `selectLatestThumbnailKey` in the service. Let me add that.

Add to `AlbumItemMapper.java`:

```java
@Select("SELECT i.thumbnail_key FROM album_items ai " +
        "JOIN items i ON i.id = ai.item_id " +
        "WHERE ai.album_id = #{albumId} " +
        "ORDER BY ai.added_at DESC LIMIT 1")
String selectLatestThumbnailKey(@Param("albumId") Long albumId);
```

**Commit:**
```bash
git add netdisk-album/src/main/java/com/m78/netdisk/album/mapper/AlbumItemMapper.java
git commit -m "feat: add selectLatestThumbnailKey to AlbumItemMapper"
```

---

## Verification

1. Compile: `mvn compile -pl netdisk-album -am`
2. Full build: `mvn clean compile`
3. Review each commit for correctness
4. Push to origin

---

## Summary of All Changes

| Phase | Files | Type |
|-------|-------|------|
| 1 | `database/init.sql` | Modify |
| 2 | `MediaProgress.java`, `MediaProgressMapper.java`, `SaveProgressDTO.java`, `MediaProgressVO.java`, `IFileService.java`, `FileServiceImpl.java`, `FileController.java` | Create/Modify |
| 3 | `netdisk-album/pom.xml`, `pom.xml`, `SwaggerConfig.java` | Create/Modify |
| 4 | `Album.java`, `AlbumItem.java`, DTOs, VOs | Create |
| 5 | `AlbumMapper.java`, `AlbumItemMapper.java` | Create |
| 6 | `IAlbumService.java`, `AlbumServiceImpl.java` | Create |
| 7 | `AlbumController.java` | Create |
| 8 | `AlbumItemMapper.java` (update) | Modify |
