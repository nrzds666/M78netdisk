# Media Progress Tracking & Album Module Design

> **Date:** 2026-05-29
> **Author:** Hermes Agent
> **Status:** Approved

## Overview

Add two features to M78 NetDisk:
1. **Media Progress Tracking** — remember where users left off watching videos / listening to audio
2. **Album Module** — organize photos and videos into albums for categorization

## 1. Media Progress Tracking

### Table: `media_progress`

```sql
CREATE TABLE media_progress (
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
```

### API (added to `netdisk-file` module's `FileController`)

| Method | Path | Body / Params | Response |
|--------|------|--------------|----------|
| `GET` | `/api/files/progress/{itemId}` | — | `{itemId, progressSeconds, totalDuration, finished, updatedAt}` |
| `PUT` | `/api/files/progress/{itemId}` | `{progressSeconds, totalDuration, finished?}` | Updated record |

- Progress is per-user per-item (single record, upsert on PUT)
- Backend validates item exists, belongs to user, and is a media type (image/*, video/*, audio/*)
- No new module needed — goes in `netdisk-file`

## 2. Album Module

### New Module: `netdisk-album`

- Package: `com.m78.netdisk.album`
- Maven artifact: `netdisk-album`
- Follows existing module patterns (controller → service → mapper → po)

### Tables

**albums:**
```sql
CREATE TABLE albums (
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
```

**album_items:**
```sql
CREATE TABLE album_items (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    album_id  BIGINT NOT NULL,
    item_id   BIGINT NOT NULL,
    added_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (album_id, item_id),
    FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Indexes

```sql
CREATE INDEX idx_albums_user ON albums(user_id, sort_order);
CREATE INDEX idx_album_items_album ON album_items(album_id, added_at DESC);
CREATE INDEX idx_album_items_item ON album_items(item_id);
```

### Cover Logic

- If `cover_item_id` is set → use that item's thumbnail
- If `cover_item_id` is NULL → auto-select the most recently added item (by `added_at DESC`) in the album
- User can change cover via dedicated API

### API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/albums` | Create album (name required; optional coverItemId, description) |
| `GET` | `/api/albums` | List my albums (paginated, with cover thumbnail for each) |
| `GET` | `/api/albums/{id}` | Get album detail with paginated items (only image/video) |
| `PUT` | `/api/albums/{id}` | Update album (name, description, coverItemId) |
| `DELETE` | `/api/albums/{id}` | Delete album (soft — only deletes album and album_items, not items) |
| `POST` | `/api/albums/{id}/items` | Batch add items (itemIds list; validates ownership + image/video mimeType) |
| `DELETE` | `/api/albums/{id}/items` | Batch remove items from album |
| `PUT` | `/api/albums/{id}/cover` | Set album cover (itemId; must belong to album) |

### Album VO (response shape)

```json
{
  "id": 1,
  "name": "旅行相册",
  "coverItemId": 42,
  "coverThumbnailKey": "thumbnails/xxx.jpg",
  "description": "2025年旅行记录",
  "itemCount": 15,
  "sortOrder": 0,
  "createdAt": "2026-05-29T10:00:00"
}
```

### Shared File → Album Flow

1. User saves/downloads shared files → these become items in user's storage
2. User (or frontend) calls `POST /api/albums/{albumId}/items` with `itemIds` list
3. Backend validates: each item belongs to user, mimeType is image/* or video/*
4. Adds associations

No automatic or implicit album addition — always explicit API call.

## Modified Files

- `pom.xml` — add `<module>netdisk-album</module>`
- `database/init.sql` — append albums, album_items, media_progress tables + indexes
- `netdisk-file/.../FileController.java` — add progress endpoints
- `netdisk-file/.../IFileService.java` — add progress interface methods
- `netdisk-file/.../FileServiceImpl.java` — add progress implementation
- `netdisk-file/.../mapper/ItemMapper.java` — add media type check query
- `netdisk-bootstrap/.../SwaggerConfig.java` — add album API group

## New Files

- `netdisk-album/pom.xml`
- `netdisk-album/src/main/java/com/m78/netdisk/album/controller/AlbumController.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/service/IAlbumService.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/service/impl/AlbumServiceImpl.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/domain/po/Album.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/domain/po/AlbumItem.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/domain/vo/AlbumVO.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/domain/dto/CreateAlbumDTO.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/domain/dto/UpdateAlbumDTO.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/domain/dto/AddItemsDTO.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/mapper/AlbumMapper.java`
- `netdisk-album/src/main/java/com/m78/netdisk/album/mapper/AlbumItemMapper.java`
- `netdisk-file/src/main/java/com/m78/netdisk/file/domain/po/MediaProgress.java`
- `netdisk-file/src/main/java/com/m78/netdisk/file/mapper/MediaProgressMapper.java`
- `netdisk-file/src/main/java/com/m78/netdisk/file/domain/dto/SaveProgressDTO.java`
- `netdisk-file/src/main/java/com/m78/netdisk/file/domain/vo/MediaProgressVO.java`
