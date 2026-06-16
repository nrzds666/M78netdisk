# M78 NetDisk API 测试文档

> 版本: 1.0.0  
> 更新日期: 2026-06-04  
> 基础路径: `http://localhost:8080/api`  
> 认证方式: `Authorization: Bearer <token>`  
> 响应格式: `R<T>` 统一包装 `{"code":200, "msg":"success", "data": {...}}`

---

## 目录

1. [用户模块](#1-用户模块)
2. [文件模块](#2-文件模块)
3. [分享模块](#3-分享模块)
4. [保险箱模块](#4-保险箱模块)
5. [相册模块](#5-相册模块)
6. [日历模块](#6-日历模块)
7. [存储节点管理](#7-存储节点管理)

---

## 1. 用户模块

### 1.1 获取验证码

**GET** `/users/captcha`

无需认证。返回算术验证码图片（base64）。

**响应示例:**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "key": "e83d1361dd3e4f4b",
    "imageBase64": "data:image/png;base64,..."
  }
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.1-1 正常获取 | 访问 GET /api/users/captcha | 200，key 非空，imageBase64 非空 |

---

### 1.2 注册

**POST** `/users/register`

**请求体:**
```json
{
  "username": "testuser",
  "password": "test123",
  "email": "test@example.com",
  "captchaKey": "验证码key",
  "captchaCode": "验证码答案"
}
```

**成功响应:**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "userId": 1,
    "username": "testuser",
    "avatarUrl": null,
    "accessToken": "eyJhbG...",
    "refreshToken": "eyJhbG...",
    "expiresIn": 86400
  }
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.2-1 正常注册 | 先获取验证码，用正确验证码注册新用户 | 200，返回 token |
| TC-1.2-2 用户名已存在 | 用已注册的用户名再次注册 | 400，"用户名已存在" |
| TC-1.2-3 密码过短 | 密码少于6位 | 400，参数校验失败 |
| TC-1.2-4 验证码错误 | 使用错误验证码 | 400，"验证码错误或已过期" |
| TC-1.2-5 缺少必填字段 | 不传 username | 400 |

---

### 1.3 登录

**POST** `/users/login`

**请求体:**
```json
{
  "username": "testuser",
  "password": "test123",
  "captchaKey": "验证码key",
  "captchaCode": "验证码答案"
}
```

**成功响应:** 同注册响应（返回 token）

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.3-1 正常登录 | 先获取验证码，用正确凭据登录 | 200，返回 token |
| TC-1.3-2 密码错误 | 正确验证码 + 错误密码 | 400，"用户名或密码错误" |
| TC-1.3-3 用户不存在 | 不存在的用户名 | 400，"用户名或密码错误" |
| TC-1.3-4 验证码错误 | 错误验证码 | 400，"验证码错误或已过期" |

---

### 1.4 获取当前用户信息

**GET** `/users`

需认证。Header: `Authorization: Bearer <token>`

**响应:**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "avatarUrl": null,
    "status": 1,
    "quotaBytes": 10737418240,
    "usedBytes": 0,
    "createdAt": "2026-06-04T10:00:00"
  }
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.4-1 正常获取 | 带有效 token 访问 | 200，返回用户信息 |
| TC-1.4-2 无认证 | 不带 token | 401，"未登录" |
| TC-1.4-3 token 过期 | 携带过期 token | 401，"请重新登录" |

---

### 1.5 更新用户资料

**PUT** `/users/profile?username=新用户名`

需认证。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.5-1 正常修改 | 用有效 token 修改用户名 | 200 |
| TC-1.5-2 用户名已存在 | 改为已存在的用户名 | 400，"用户名已被使用" |
| TC-1.5-3 用户名为空 | 传空字符串 | 400，"用户名不能为空" |

---

### 1.6 修改密码

**PUT** `/users/password?oldPassword=旧密码&newPassword=新密码`

需认证。修改密码后强制退出登录。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.6-1 正常修改 | 正确旧密码 + 6位以上新密码 | 200，之后旧 token 失效 |
| TC-1.6-2 旧密码错误 | 错误旧密码 | 400，"原密码错误" |
| TC-1.6-3 新密码过短 | 少于6位 | 400，"新密码长度需在6-72个字符之间" |

---

### 1.7 修改头像

**PUT** `/users/avatar?avatarUrl=头像URL`

需认证。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.7-1 正常修改 | 有效 URL | 200 |
| TC-1.7-2 URL 为空 | 空字符串 | 400，"头像URL不能为空" |

---

### 1.8 刷新 Token

**POST** `/users/refresh`

Header: `X-Refresh-Token: <refreshToken>`

**响应:** 同登录响应（返回新 token 对）

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.8-1 正常刷新 | 携带有效的 refreshToken | 200，返回新 token 对 |
| TC-1.8-2 空 token | 不带 header | 401，"刷新令牌不能为空" |
| TC-1.8-3 无效 token | 非法 refreshToken | 500 |

---

### 1.9 退出登录

**POST** `/users/logout`

需认证。清除 Redis 中的 token 黑名单。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-1.9-1 正常退出 | 有效 token | 200 |
| TC-1.9-2 未登录 | 不带 token | 401，"未登录" |

---

## 2. 文件模块

### 2.1 列出文件/文件夹

**GET** `/files/list?parentId=&page=1&size=20&query=&type=&dateFrom=&dateTo=`

需认证。`parentId` 为空时列出根目录。

**Query 参数:**
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| parentId | Long | 否 | 父目录 ID，空=根目录 |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 20，最大 100 |
| query | String | 否 | 文件名模糊搜索 |
| type | String | 否 | 筛选类型：image/video/audio/document/archive/other |
| dateFrom | String | 否 | 开始日期 yyyy-MM-dd |
| dateTo | String | 否 | 结束日期 yyyy-MM-dd |

**响应:**
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": 1, "parentId": null, "name": "图片",
        "isDirectory": true, "size": 0, "mimeType": null,
        "path": "/图片", "version": 1,
        "createdAt": "2026-06-04 10:00:00",
        "updatedAt": "2026-06-04 10:00:00"
      },
      {
        "id": 2, "parentId": null, "name": "report.pdf",
        "isDirectory": false, "size": 102400, "mimeType": "application/pdf",
        "storageKey": "uploads/uuid/report.pdf",
        "path": "/report.pdf", "version": 1,
        "createdAt": "2026-06-04 10:00:00",
        "updatedAt": "2026-06-04 10:00:00"
      }
    ],
    "total": 2, "size": 20, "current": 1, "pages": 1
  }
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.1-1 列出根目录 | parentId 不传 | 200，返回根目录下文件 |
| TC-2.1-2 列出子目录 | parentId=文件夹ID | 200，返回子目录文件 |
| TC-2.1-3 分页 | page=2, size=5 | 200，返回第2页 |
| TC-2.1-4 搜索文件名 | query=report | 200，返回匹配文件 |
| TC-2.1-5 类型筛选 | type=image | 200，仅返回图片 |
| TC-2.1-6 日期范围 | dateFrom/dateTo | 200，返回日期范围内文件 |
| TC-2.1-7 无认证 | 不带 token | 返回空列表（token 为空时 userId 为 null） |

---

### 2.2 创建文件夹

**POST** `/files/folder`

**请求体:**
```json
{
  "name": "新建文件夹",
  "parentId": null
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.2-1 正常创建 | 合法名称 | 200，返回文件夹信息 |
| TC-2.2-2 同名已存在 | 同一目录下重名 | 400，"同名文件夹已存在" |
| TC-2.2-3 名称为空 | 空 name | 400，"文件夹名称不能为空" |
| TC-2.2-4 非法字符 | 包含 / \ .. \0 | 400，"文件夹名称包含非法字符" |

---

### 2.3 重命名

**PUT** `/files/rename`

```json
{
  "itemId": 1,
  "newName": "新名字"
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.3-1 正常重命名 | 合法新名称 | 200，名称更新 |
| TC-2.3-2 重名冲突 | 同一目录下已存在 | 400，"该名称已存在" |
| TC-2.3-3 非法字符 | 含 / | 400，"文件名包含非法字符" |

---

### 2.4 移动到回收站

**DELETE** `/files/trash?ids=1,2,3`

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.4-1 单个删除 | 单个 ID | 200 |
| TC-2.4-2 批量删除 | 多个 ID | 200 |
| TC-2.4-3 删除不存在的文件 | 无效 ID | 200（静默跳过） |

---

### 2.5 从回收站恢复

**POST** `/files/restore?ids=1,2`

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.5-1 正常恢复 | 回收站中的文件 | 200 |
| TC-2.5-2 目标目录重名 | 恢复时已存在同名文件 | 400，"请先处理冲突" |

---

### 2.6 永久删除

**DELETE** `/files/permanent?ids=1,2`

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.6-1 永久删除 | 回收站中文件 | 200，文件被彻底删除 |
| TC-2.6-2 非回收站文件 | 未删除的文件 | 400，"文件不在回收站中" |

---

### 2.7 列出回收站

**GET** `/files/trash?page=1&size=20`

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.7-1 正常列出 | 有效 token | 200，返回回收站文件列表 |

---

### 2.8 单文件上传

**POST** `/files/upload`

**请求体:** `multipart/form-data`
| 字段 | 类型 | 说明 |
|------|------|------|
| file | File | 上传的文件 |
| parentId | Long | 目标目录 ID，空=根目录 |

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.8-1 上传图片 | 上传 jpg/png 文件 | 200，返回文件信息 |
| TC-2.8-2 上传文档 | 上传 pdf/docx 文件 | 200 |
| TC-2.8-3 上传空文件 | 空文件 | 400，"上传文件不能为空" |
| TC-2.8-4 同名文件 | 同一目录下已存在 | 400，"该目录下已存在同名文件" |
| TC-2.8-5 超大文件 | 超过配置限制 | 413 |

---

### 2.9 文件预览（弹窗展示）

**GET** `/files/preview/{id}?token=<jwt>`

不经过 axios，URL 参数传 token。直接返回文件字节流。

**Header 行为:**
- 普通文件：`Content-Disposition: inline`，浏览器弹窗展示
- Office 文件（doc/docx/xls/xlsx/ppt/pptx）：后端实时转 PDF 后返回 `Content-Type: application/pdf`
- 失败时回退原始文件流

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.9-1 预览图片 | 双击图片文件 | 弹窗显示图片 |
| TC-2.9-2 预览 PDF | 双击 PDF 文件 | iframe 显示 PDF |
| TC-2.9-3 预览 Word | 双击 docx 文件 | 后端转 PDF 后显示 |
| TC-2.9-4 预览视频 | 双击 mp4 文件 | video 标签播放 |
| TC-2.9-5 预览音频 | 双击 mp3 文件 | audio 标签播放 |
| TC-2.9-6 不可预览类型 | 双击 zip 文件 | 弹窗显示"不支持预览"+ 下载按钮 |
| TC-2.9-7 文件不存在 | ID 无效 | 40x 错误 |

---

### 2.10 文件下载（axios blob）

**GET** `/files/download/{id}`

需认证（axios 自动携带 token）。返回文件字节流，`Content-Disposition: attachment`。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.10-1 下载文件 | 点击下载按钮 | 浏览器下载文件 |
| TC-2.10-2 文件不存在 | ID 无效 | 弹窗提示"下载失败：请求的资源不存在" |

---

### 2.11 文件夹下载（ZIP）

**GET** `/files/download/folder/{id}`

需认证，URL 参数传 token（window.open）。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.11-1 下载文件夹 | 有效文件夹 ID | 下载 ZIP 包 |
| TC-2.11-2 下载文件（非文件夹） | 文件 ID | 400 |

---

### 2.12 分片上传

| API | 方法 | 说明 |
|-----|------|------|
| `/files/upload/init` | POST | 初始化分片上传任务 |
| `/files/upload/chunk` | POST | 确认已上传的分片 |
| `/files/upload/complete` | POST | 完成上传（合并分片） |
| `/files/upload/cancel` | POST | 取消上传 |
| `/files/upload/status` | GET | 查询上传状态 |

---

### 2.13 最近文件

**GET** `/files/recent?days=3`

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.13-1 最近上传 | 有效 token | 200，返回最近 3 天上传的文件 |

**GET** `/files/recent-saves?days=3`

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-2.14-1 最近提取 | 有效 token | 200，返回最近提取的文件 |

---

### 2.15 媒体播放进度

| API | 方法 | 说明 |
|-----|------|------|
| `/files/progress/{itemId}` | GET | 读取播放进度 |
| `/files/progress/{itemId}` | PUT | 保存播放进度 |

---

## 3. 分享模块

### 3.1 创建分享

**POST** `/api/shares`

```json
{
  "itemIds": [1, 2],
  "expireTime": "2026-06-05T10:00:00",
  "password": "1234",
  "maxDownloadCount": 10
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-3.1-1 正常创建 | 选择文件创建分享 | 200，返回分享链接 token |

### 3.2 取消分享

**POST** `/api/shares/{id}/cancel`

### 3.3 我的分享列表

**GET** `/api/shares/mine?page=1&size=20`

### 3.4 接收的分享记录

**GET** `/api/shares/received?page=1&size=20`

### 3.5 访问分享

**GET** `/api/shares/access/{token}?password=1234`

无需认证（拦截器放行）。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-3.5-1 有密码访问 | 正确密码 | 200，返回分享内容 |
| TC-3.5-2 密码错误 | 错误密码 | 403 |

### 3.6 浏览分享文件夹

**GET** `/api/shares/access/{token}/items?parentId=&page=1&size=20`

### 3.7 从分享下载文件

**GET** `/api/shares/access/{token}/download?itemId=&password=`

### 3.8 保存分享文件

**POST** `/api/shares/access/{token}/save`

```json
[1, 2, 3]
```

Header: `Authorization: Bearer <token>`

---

## 4. 保险箱模块

### 4.1 查看保险箱状态

**GET** `/api/vault/status`

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "enabled": true,
    "unlocked": true
  }
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-4.1-1 未设置 | 首次访问 | enabled=false |
| TC-4.1-2 已设置已解锁 | 设置后 | enabled=true, unlocked=true |
| TC-4.1-3 已设置已锁定 | 锁定后 | enabled=true, unlocked=false |

### 4.2 设置保险箱密码

**POST** `/api/vault/setup`

```json
{
  "loginPassword": "用户登录密码",
  "vaultPassword": "保险箱密码6-72位",
  "confirmPassword": "确认密码"
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-4.2-1 正常设置 | 正确登录密码 + 两次一致 | 200，自动解锁 |
| TC-4.2-2 重复设置 | 保险箱已存在 | 400，"保险箱已存在" |
| TC-4.2-3 登录密码错误 | 错误密码 | 400，"登录密码错误" |
| TC-4.2-4 密码不一致 | 两次密码不同 | 400，"两次输入的保险箱密码不一致" |
| TC-4.2-5 密码太短 | 少于6位 | 400，参数校验失败 |

### 4.3 解锁保险箱

**POST** `/api/vault/unlock`

```json
{
  "password": "保险箱密码"
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-4.3-1 正常解锁 | 正确密码 | 200，Redis 记录解锁状态1小时 |
| TC-4.3-2 密码错误 | 错误密码 | 403，"保险箱密码错误" |
| TC-4.3-3 5次错误锁定 | 连续5次 | 429，"保险箱已锁定，请10分钟后再试" |

### 4.4 锁定保险箱

**POST** `/api/vault/lock`

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-4.4-1 正常锁定 | 已解锁状态 | 200，Redis 清除解锁标记 |

### 4.5 列出保险箱文件

**GET** `/api/vault/files/list?parentId=&page=1&size=20`

需要解锁状态（拦截器校验 Redis 解锁标记）。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-4.5-1 已解锁可查看 | 解锁后 | 200，返回文件列表 |
| TC-4.5-2 已锁定被拦截 | 锁定状态 | 403，"保险箱未解锁" |

### 4.6 保险箱创建文件夹

**POST** `/api/vault/files/folder`

同文件模块创建文件夹，自动标记 `is_vaulted=true`

### 4.7 上传文件到保险箱

**POST** `/api/vault/files/upload`

`multipart/form-data`，同文件上传，自动标记 `is_vaulted=true`

### 4.8 从保险箱下载

**GET** `/api/vault/files/download/{id}`

### 4.9 移出保险箱

**PUT** `/api/vault/files/remove?itemId=`

清除 `is_vaulted` 标记（不删除文件）。

---

## 5. 相册模块

### 5.1 创建相册

**POST** `/api/albums`

```json
{
  "name": "旅行照片",
  "description": "2026年旅行",
  "itemIds": [1, 2, 3]
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-5.1-1 正常创建 | 合法名称 | 200，返回相册信息 |
| TC-5.1-2 名称为空 | 空 name | 400 |

### 5.2 列出相册

**GET** `/api/albums?page=1&size=20`

### 5.3 相册详情

**GET** `/api/albums/{id}?page=1&size=20`

返回相册信息 + 分页的照片列表。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-5.3-1 查看详情 | 点击相册 | 弹窗显示相册内文件 |
| TC-5.3-2 相册不存在 | 无效 ID | 400，"相册不存在" |

### 5.4 更新相册

**PUT** `/api/albums/{id}`

```json
{
  "name": "新名称",
  "description": "新描述"
}
```

### 5.5 删除相册

**DELETE** `/api/albums/{id}`

级联删除关联记录，不删除实际文件。

### 5.6 添加文件到相册

**POST** `/api/albums/{id}/items`

```json
{
  "itemIds": [4, 5, 6]
}
```

仅允许添加图片和视频（mimeType 以 image/ 或 video/ 开头）。

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-5.6-1 添加图片 | 选择图片文件 | 200 |
| TC-5.6-2 添加文档 | 选择非图片/视频 | 400，"只能添加图片或视频文件" |
| TC-5.6-3 重复添加 | 已存在的文件 | 静默跳过 |

### 5.7 从相册移除文件

**DELETE** `/api/albums/{id}/items?itemIds=1,2`

### 5.8 设置封面

**PUT** `/api/albums/{id}/cover?itemId=1`

---

## 6. 日历模块

### 6.1 获取今日运势

**GET** `/api/calendar/today`

无需认证。返回当日农历、黄历、宜忌、分享建议。

**响应:**
```json
{
  "code": 200,
  "data": {
    "date": "2026-06-04",
    "lunar": {
      "year": "丙午",
      "month": "四月",
      "day": "十九",
      "zodiac": "马",
      "heavenlyStem": "丙",
      "earthlyBranch": "午",
      "monthStem": "癸",
      "monthBranch": "巳",
      "dayStem": "庚",
      "dayBranch": "辰",
      "jieQi": "芒种",
      "jianChu": "满",
      "isLeapMonth": false
    },
    "yi": ["祭祀", "祈福"],
    "ji": ["出行", "嫁娶"],
    "shareAdvice": {
      "favorableForShare": true,
      "favorableForUpload": true,
      "favorableForDownload": true,
      "favorableForCancel": false
    }
  }
}
```

| 测试用例 | 步骤 | 预期 |
|---------|------|------|
| TC-6.1-1 正常获取 | 访问接口 | 200，返回完整黄历信息 |

---

## 7. 存储节点管理

需认证。所有接口均需 token。

| API | 方法 | 说明 |
|-----|------|------|
| `/api/admin/nodes` | GET | 获取所有存储节点 |
| `/api/admin/nodes/active` | GET | 获取可用存储节点 |
| `/api/admin/nodes/{id}` | GET | 获取节点详情 |
| `/api/admin/nodes` | POST | 新增存储节点 |
| `/api/admin/nodes/{id}` | PUT | 更新存储节点 |
| `/api/admin/nodes/{id}` | DELETE | 删除存储节点 |

---

## 通用错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数校验失败 / 业务错误（具体见 msg） |
| 401 | 未登录 / token 过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 429 | 请求频率限制（保险箱暴力破解锁定） |
| 500 | 服务器内部错误 |

## 测试环境准备

1. 启动后端: `mvn spring-boot:run -pl netdisk-bootstrap -am`
2. 启动前端: `cd frontend && npm run dev`
3. 验证后端健康: `curl http://localhost:8080/api/users/captcha`
4. 验证前端: 浏览器打开 `http://localhost:5173`
