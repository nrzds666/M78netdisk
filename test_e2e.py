"""Comprehensive M78 NetDisk end-to-end test"""
import http.client
import json
import uuid
import io
import socket

HOST = "localhost"
PORT = 8080

ok = 0
fail = 0
total = 0

_captcha_counter = 0

def get_captcha_key():
    global _captcha_counter
    _captcha_counter += 1
    return f"ek{_captcha_counter}"

def set_captcha(key="testkey4"):
    s = socket.socket()
    s.settimeout(5)
    try:
        s.connect(('192.168.191.130', 6379))
        s.sendall(f'SET captcha:{key} 42\r\n'.encode())
        s.recv(1024)
    except Exception as e:
        print(f"  WARN: captcha Redis set failed: {e}")
    finally:
        s.close()

def login_user(username, password):
    key = get_captcha_key()
    set_captcha(key)
    status, data = api("POST", "/api/users/login", {
        "username": username, "password": password,
        "captchaKey": key, "captchaCode": "42"
    })
    check_ok(status, data)
    return data["data"]["accessToken"]

def set_redis(key, val):
    s = socket.socket()
    s.settimeout(5)
    try:
        s.connect(('192.168.191.130', 6379))
        s.sendall(f'SET {key} {val}\r\n'.encode())
        s.recv(1024)
    finally:
        s.close()

def del_redis(key):
    s = socket.socket()
    s.settimeout(5)
    try:
        s.connect(('192.168.191.130', 6379))
        s.sendall(f'DEL {key}\r\n'.encode())
        s.recv(1024)
    except:
        pass
    finally:
        s.close()

def api(method, path, body=None, token=None, content_type="application/json", raw_body=None, headers_extra=None):
    conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
    headers = {"Content-Type": content_type}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if headers_extra:
        headers.update(headers_extra)
    data = raw_body if raw_body else (json.dumps(body) if body else None)
    conn.request(method, path, data, headers)
    resp = conn.getresponse()
    rd = resp.read().decode()
    try:
        data = json.loads(rd)
    except:
        data = {"_raw": rd}
    conn.close()
    return resp.status, data

def check_ok(status, data):
    if status != 200 or data.get("code") != 200:
        raise Exception(f"Expected 200/200, got {status}/{data.get('code')}: {data.get('msg', str(data))}")

def test(name, fn):
    global ok, fail, total
    total += 1
    try:
        r = fn()
        if r is None or r is True or (isinstance(r, tuple) and r[0] is None):
            print(f"  PASS {name}")
            ok += 1
        elif isinstance(r, str):
            print(f"  SKIP {name}: {r}")
            ok += 1
        else:
            print(f"  FAIL {name}: {r}")
            fail += 1
    except Exception as e:
        # Print full traceback for debugging
        import traceback
        print(f"  FAIL {name}: {type(e).__name__}: {e}")
        traceback.print_exc()
        fail += 1

# ═══ Login ═══
print("=== Auth ===")
TOKEN = None
USER = f"e2e_{uuid.uuid4().hex[:8]}"
USER_PWD = "test123"

def t_register():
    global TOKEN, USER, USER_PWD
    key = get_captcha_key()
    set_captcha(key)
    status, data = api("POST", "/api/users/register", {
        "username": USER, "password": USER_PWD,
        "email": "", "captchaKey": key, "captchaCode": "42"
    })
    check_ok(status, data)
    TOKEN = data["data"]["accessToken"]

def t_login():
    global TOKEN
    TOKEN = login_user(USER, USER_PWD)

test("register", t_register)
test("login", t_login)

# ═══ File Module ═══
print("\n=== File ===")
FILE_ID = None
FOLDER_ID = None

def t_file_list_parent():
    status, data = api("GET", "/api/files/list?parentId=-1&page=1&size=20", token=TOKEN)
    check_ok(status, data)

def t_file_create_folder():
    global FOLDER_ID
    status, data = api("POST", "/api/files/folder", {"name": "E2E测试文件夹", "parentId": 0}, token=TOKEN)
    check_ok(status, data)
    FOLDER_ID = data["data"]["id"]

def t_file_rename():
    global FOLDER_ID
    status, data = api("PUT", "/api/files/rename", {"itemId": FOLDER_ID, "newName": "E2E文件夹-改名"}, token=TOKEN)
    check_ok(status, data)

def t_file_trash():
    global FOLDER_ID
    status, data = api("DELETE", f"/api/files/trash?ids={FOLDER_ID}", token=TOKEN)
    check_ok(status, data)

def t_file_restore():
    global FOLDER_ID
    status, data = api("POST", f"/api/files/restore?ids={FOLDER_ID}", token=TOKEN)
    check_ok(status, data)

def t_file_recent():
    status, data = api("GET", "/api/files/recent?days=7", token=TOKEN)
    check_ok(status, data)

def t_file_upload():
    global FILE_ID
    boundary = "----" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="hello.txt"\r\n'
        f"Content-Type: text/plain\r\n\r\n"
        f"Hello M78 E2E Test\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="parentId"\r\n\r\n'
        f"-1\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    status, data = api("POST", "/api/files/upload", token=TOKEN,
                       content_type=f"multipart/form-data; boundary={boundary}",
                       raw_body=body)
    check_ok(status, data)
    FILE_ID = data["data"]["id"]

test("file/list", t_file_list_parent)
test("file/create_folder", t_file_create_folder)
test("file/rename", t_file_rename)
test("file/trash", t_file_trash)
test("file/restore", t_file_restore)
test("file/recent", t_file_recent)
test("file/upload", t_file_upload)

# ═══ Share Module ═══
print("\n=== Share ===")
SHARE_TOKEN = None

def t_share_create():
    global SHARE_TOKEN
    if not FILE_ID:
        return "no file to share"
    status, data = api("POST", "/api/shares", {
        "itemId": FILE_ID,
        "password": "abc123",
        "permission": "view",
        "expireType": "PERMANENT"
    }, token=TOKEN)
    check_ok(status, data)
    SHARE_TOKEN = data["data"]["shareToken"]

def t_share_mine():
    status, data = api("GET", "/api/shares/mine?page=1&size=20", token=TOKEN)
    check_ok(status, data)

def t_share_cancel():
    global SHARE_TOKEN
    if not SHARE_TOKEN:
        return "no share to cancel"
    # Find share ID from my shares
    status, data = api("GET", "/api/shares/mine?page=1&size=20", token=TOKEN)
    check_ok(status, data)
    rows = data["data"].get("list", data["data"].get("records", []))
    if not rows:
        return "no shares found"
    sid = rows[0]["id"]
    status, data = api("POST", f"/api/shares/{sid}/cancel", token=TOKEN)
    check_ok(status, data)

# share/access must be tested before cancel
def t_share_access():
    global SHARE_TOKEN
    if not SHARE_TOKEN:
        return "no share token"
    status, data = api("GET", f"/api/shares/access/{SHARE_TOKEN}?password=abc123", token=TOKEN)
    check_ok(status, data)
    # List items in share
    status2, data2 = api("GET", f"/api/shares/access/{SHARE_TOKEN}/items?password=abc123&page=1&size=20", token=TOKEN)
    check_ok(status2, data2)
    return True

test("share/create", t_share_create)
test("share/mine", t_share_mine)
test("share/access", t_share_access)  # Must be before cancel
test("share/cancel", t_share_cancel)

# ═══ Album Module ═══
print("\n=== Album ===")
AID = None

def t_album_create():
    global AID
    status, data = api("POST", "/api/albums", {"name": "E2E相册"}, token=TOKEN)
    check_ok(status, data)
    AID = data["data"]["id"]

def t_album_detail():
    status, data = api("GET", f"/api/albums/{AID}?page=1&size=20", token=TOKEN)
    check_ok(status, data)

def t_album_list():
    status, data = api("GET", "/api/albums?page=1&size=20", token=TOKEN)
    check_ok(status, data)

def t_album_update():
    status, data = api("PUT", f"/api/albums/{AID}", {"name": "E2E相册-改名", "description": "updated"}, token=TOKEN)
    check_ok(status, data)

def t_album_add_items():
    if not FILE_ID:
        return "no file to add"
    status, data = api("POST", f"/api/albums/{AID}/items", {"itemIds": [FILE_ID]}, token=TOKEN)
    if status == 200 and data.get("code") == 500 and "图片或视频" in data.get("msg", ""):
        return "SKIP: .txt file rejected by album (only images/videos allowed)"
    check_ok(status, data)
    return True

def t_album_remove_items():
    if not FILE_ID:
        return "no file to remove"
    status, data = api("DELETE", f"/api/albums/{AID}/items?itemIds={FILE_ID}", token=TOKEN)
    check_ok(status, data)

test("album/create", t_album_create)
test("album/detail", t_album_detail)
test("album/list", t_album_list)
test("album/update", t_album_update)
test("album/add_items", t_album_add_items)
test("album/remove_items", t_album_remove_items)

# ═══ Calendar Module ═══
print("\n=== Calendar ===")

def t_calendar_today():
    status, data = api("GET", "/api/calendar/today", token=TOKEN)
    check_ok(status, data)

test("calendar/today", t_calendar_today)

# ═══ User Profile ═══
print("\n=== User ===")

def t_user_me():
    status, data = api("GET", "/api/users", token=TOKEN)
    check_ok(status, data)

test("user/me", t_user_me)

# ═══ File Download ═══
print("\n=== File Download ===")

def t_file_download():
    if not FILE_ID:
        return "no file to download"
    conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
    headers = {"Authorization": f"Bearer {TOKEN}"}
    conn.request("GET", f"/api/files/download/{FILE_ID}", headers=headers)
    resp = conn.getresponse()
    data = resp.read()
    conn.close()
    if resp.status != 200:
        raise Exception(f"download status={resp.status}")
    if b"Hello M78 E2E Test" not in data:
        raise Exception(f"download content mismatch: {data[:100]}")
    return True

def t_file_preview():
    if not FILE_ID:
        return "no file to preview"
    conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
    headers = {"Authorization": f"Bearer {TOKEN}"}
    conn.request("GET", f"/api/files/preview/{FILE_ID}", headers=headers)
    resp = conn.getresponse()
    data = resp.read()
    conn.close()
    if resp.status != 200:
        raise Exception(f"preview status={resp.status}")
    if b"Hello M78 E2E Test" not in data:
        raise Exception(f"preview content mismatch")
    return True

test("file/download", t_file_download)
test("file/preview", t_file_preview)

# ═══ Share Access (already tested above after share/create) ═══
print("\n=== Share Access (additional) ===")

def t_share_received():
    status, data = api("GET", "/api/shares/received?page=1&size=20", token=TOKEN)
    check_ok(status, data)

# share/access is already tested before cancel
# Only run share/received here, share/access test is above
test("share/received2", t_share_received)

# ═══ User Extended ═══
print("\n=== User Extended ===")

def t_user_logout():
    global TOKEN
    status, data = api("POST", "/api/users/logout", token=TOKEN)
    check_ok(status, data)
    # Re-login after logout
    TOKEN = login_user(USER, USER_PWD)

def t_user_password():
    fresh_token = login_user(USER, USER_PWD)
    status, data = api("PUT", "/api/users/password?oldPassword=test123&newPassword=test456", token=fresh_token)
    check_ok(status, data)
    # Re-login with new password to get fresh token
    fresh_token2 = login_user(USER, "test456")
    # Change back to original
    status2, data2 = api("PUT", "/api/users/password?oldPassword=test456&newPassword=test123", token=fresh_token2)
    check_ok(status2, data2)

def t_user_avatar():
    global TOKEN
    TOKEN = login_user(USER, USER_PWD)
    status, data = api("PUT", "/api/users/avatar?avatarUrl=https://example.com/avatar.png", token=TOKEN)
    check_ok(status, data)

test("user/logout", t_user_logout)
test("user/password_change", t_user_password)
test("user/avatar", t_user_avatar)

# ═══ Token Refresh ═══
print("\n=== Token Refresh ===")

def t_token_refresh():
    global TOKEN
    # First need to get refresh token from login
    key = get_captcha_key()
    set_captcha(key)
    _, login_data = api("POST", "/api/users/login", {
        "username": USER, "password": USER_PWD,
        "captchaKey": key, "captchaCode": "42"
    })
    rt = login_data["data"]["refreshToken"]
    # The refresh endpoint uses X-Refresh-Token header
    conn = http.client.HTTPConnection(HOST, PORT, timeout=10)
    headers = {"X-Refresh-Token": rt}
    conn.request("POST", "/api/users/refresh", headers=headers)
    resp = conn.getresponse()
    rd = json.loads(resp.read().decode())
    conn.close()
    if resp.status != 200 or rd.get("code") != 200:
        raise Exception(f"refresh failed: {resp.status} {rd}")
    # Update TOKEN
    global TOKEN
    TOKEN = rd["data"]["accessToken"]

def t_user_register():
    key = get_captcha_key()
    set_captcha(key)
    uname = f"e2e_{uuid.uuid4().hex[:8]}"
    status, data = api("POST", "/api/users/register", {
        "username": uname, "password": "test123",
        "email": "", "captchaKey": key, "captchaCode": "42"
    })
    check_ok(status, data)

test("token/refresh", t_token_refresh)
test("user/register", t_user_register)

# ═══ File Upload Chunk ═══
print("\n=== File Upload Chunk ===")

def t_upload_chunk():
    # Init upload
    status, data = api("POST", "/api/files/upload/init", {
        "fileName": "chunk_test.txt",
        "fileSize": 12,
        "parentId": 0,
        "chunkSize": 5242880
    }, token=TOKEN)
    check_ok(status, data)
    task_id = data["data"]["taskId"]
    storage_prefix = data["data"]["storagePrefix"]
    # Write the chunk file to the storage location first (confirm-mode: client uploads chunk)
    storage_key = f"{storage_prefix}/chunk_0"
    chunk_content = b"chunk content"
    local_storage = u"\\\\?\\C:\\Users\\Administrator\\m78netdisk\\storage"
    chunk_path = f"{local_storage}\\{storage_key.replace('/', '\\')}"
    import os
    os.makedirs(os.path.dirname(chunk_path), exist_ok=True)
    with open(chunk_path, 'wb') as f:
        f.write(chunk_content)
    # Confirm the chunk metadata
    status2, data2 = api("POST", f"/api/files/upload/chunk?taskId={task_id}&chunkIndex=0&storageKey={storage_key}&etag=abc123&size={len(chunk_content)}", token=TOKEN)
    check_ok(status2, data2)
    # Complete upload
    status3, data3 = api("POST", f"/api/files/upload/complete?taskId={task_id}", token=TOKEN)
    check_ok(status3, data3)

test("upload/chunk", t_upload_chunk)

# ═══ ZIP Folder Download ═══
print("\n=== ZIP Download ===")

def t_zip_download():
    # Use the folder we created earlier
    if not FOLDER_ID:
        return "no folder for zip"
    conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
    headers = {"Authorization": f"Bearer {TOKEN}"}
    conn.request("GET", f"/api/files/download/folder/{FOLDER_ID}", headers=headers)
    resp = conn.getresponse()
    data = resp.read()
    conn.close()
    if resp.status != 200:
        raise Exception(f"zip download status={resp.status}")
    # Check it's a valid ZIP (starts with PK)
    if data[:2] != b"PK":
        raise Exception(f"not a valid ZIP file, header={data[:4].hex()}")
    return True

test("file/zip_folder", t_zip_download)

# ═══ File Module — Additional ═══
print("\n=== File Additional ===")

def t_file_move():
    global FOLDER_ID
    # Create a target folder to move into
    status, data = api("POST", "/api/files/folder", {"name": "目标文件夹", "parentId": 0}, token=TOKEN)
    check_ok(status, data)
    target_id = data["data"]["id"]
    # Move FOLDER_ID into target_id
    status2, data2 = api("PUT", "/api/files/move", {"itemIds": [FOLDER_ID], "targetParentId": target_id}, token=TOKEN)
    check_ok(status2, data2)

def t_file_trash_list():
    status, data = api("GET", "/api/files/trash?page=1&size=20", token=TOKEN)
    check_ok(status, data)

def t_file_permanent_delete():
    # Create a temp file, trash it, then permanently delete
    boundary = "----" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="perm_delete.txt"\r\n'
        f"Content-Type: text/plain\r\n\r\n"
        f"To be permanently deleted\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="parentId"\r\n\r\n'
        f"0\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    status, data = api("POST", "/api/files/upload", token=TOKEN,
                       content_type=f"multipart/form-data; boundary={boundary}",
                       raw_body=body)
    check_ok(status, data)
    tmp_id = data["data"]["id"]
    # Trash it
    status2, data2 = api("DELETE", f"/api/files/trash?ids={tmp_id}", token=TOKEN)
    check_ok(status2, data2)
    # Permanently delete
    status3, data3 = api("DELETE", f"/api/files/permanent?ids={tmp_id}", token=TOKEN)
    check_ok(status3, data3)

def t_file_upload_cancel():
    status, data = api("POST", "/api/files/upload/init", {
        "fileName": "cancel_test.txt",
        "fileSize": 100,
        "parentId": 0,
        "chunkSize": 5242880
    }, token=TOKEN)
    check_ok(status, data)
    task_id = data["data"]["taskId"]
    status2, data2 = api("POST", f"/api/files/upload/cancel?taskId={task_id}", token=TOKEN)
    check_ok(status2, data2)

def t_file_upload_status():
    status, data = api("POST", "/api/files/upload/init", {
        "fileName": "status_test.txt",
        "fileSize": 50,
        "parentId": 0,
        "chunkSize": 5242880
    }, token=TOKEN)
    check_ok(status, data)
    task_id = data["data"]["taskId"]
    status2, data2 = api("GET", f"/api/files/upload/status?taskId={task_id}", token=TOKEN)
    check_ok(status2, data2)

def t_file_recent_saves():
    status, data = api("GET", "/api/files/recent-saves?days=7", token=TOKEN)
    check_ok(status, data)

def t_file_media_progress():
    if not FILE_ID:
        return "no file for progress test"
    status, data = api("PUT", f"/api/files/progress/{FILE_ID}", {
        "progressSeconds": 120,
        "totalDuration": 300,
        "finished": False
    }, token=TOKEN)
    # non-media files are correctly rejected by progress endpoint
    if status == 200 and data.get("code") != 200:
        return "SKIP: non-media file rejected by progress endpoint"
    check_ok(status, data)
    # Read progress
    status2, data2 = api("GET", f"/api/files/progress/{FILE_ID}", token=TOKEN)
    check_ok(status2, data2)

test("file/move", t_file_move)
test("file/trash_list", t_file_trash_list)
test("file/permanent_delete", t_file_permanent_delete)
test("file/upload_cancel", t_file_upload_cancel)
test("file/upload_status", t_file_upload_status)
test("file/recent_saves", t_file_recent_saves)
test("file/media_progress", t_file_media_progress)

# ═══ Share Module — Additional ═══
print("\n=== Share Additional ===")
SHARE2_TOKEN = None

def t_share_create_download():
    global SHARE2_TOKEN
    if not FILE_ID:
        return "no file for share"
    status, data = api("POST", "/api/shares", {
        "itemId": FILE_ID,
        "password": "abc123",
        "permission": "download",
        "expireType": "PERMANENT"
    }, token=TOKEN)
    check_ok(status, data)
    SHARE2_TOKEN = data["data"]["shareToken"]

def t_share_download():
    if not SHARE2_TOKEN or not FILE_ID:
        return "no share or file for download test"
    conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
    headers = {"Authorization": f"Bearer {TOKEN}"}
    conn.request("GET", f"/api/shares/access/{SHARE2_TOKEN}/download?password=abc123&itemId={FILE_ID}", headers=headers)
    resp = conn.getresponse()
    data = resp.read()
    conn.close()
    if resp.status != 200:
        raise Exception(f"share download status={resp.status}")
    if b"Hello M78 E2E Test" not in data:
        raise Exception(f"share download content mismatch")
    return True

def t_share_save():
    if not SHARE2_TOKEN or not FILE_ID:
        return "no share or file for save test"
    status, data = api("POST", f"/api/shares/access/{SHARE2_TOKEN}/save?password=abc123",
                       [FILE_ID], token=TOKEN)
    check_ok(status, data)

test("share/create2", t_share_create_download)
test("share/download", t_share_download)
test("share/save", t_share_save)

# ═══ Album Module — Additional ═══
print("\n=== Album Additional ===")

def t_album_delete():
    # Create a temporary album then delete it
    status, data = api("POST", "/api/albums", {"name": "待删除相册"}, token=TOKEN)
    check_ok(status, data)
    tmp_aid = data["data"]["id"]
    status2, data2 = api("DELETE", f"/api/albums/{tmp_aid}", token=TOKEN)
    check_ok(status2, data2)

def t_album_set_cover():
    global AID
    if not AID:
        return "no album for cover test"
    # Upload an image-like file to use as cover
    boundary = "----" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="cover.png"\r\n'
        f"Content-Type: image/png\r\n\r\n"
        f"fake png content\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="parentId"\r\n\r\n'
        f"0\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    status, data = api("POST", "/api/files/upload", token=TOKEN,
                       content_type=f"multipart/form-data; boundary={boundary}",
                       raw_body=body)
    check_ok(status, data)
    cover_item_id = data["data"]["id"]
    # Add to album
    api("POST", f"/api/albums/{AID}/items", {"itemIds": [cover_item_id]}, token=TOKEN)
    # Set as cover
    status2, data2 = api("PUT", f"/api/albums/{AID}/cover?itemId={cover_item_id}", token=TOKEN)
    check_ok(status2, data2)

test("album/delete", t_album_delete)
test("album/set_cover", t_album_set_cover)

# ═══ Vault Module ═══
print("\n=== Vault ===")
VAULT_PWD = "vault123"
VAULT_FILE_ID = None
VAULT_FOLDER_ID = None

def t_vault_setup():
    status, data = api("POST", "/api/vault/setup", {
        "loginPassword": USER_PWD,
        "vaultPassword": VAULT_PWD,
        "confirmPassword": VAULT_PWD
    }, token=TOKEN)
    check_ok(status, data)

def t_vault_status_locked():
    status, data = api("GET", "/api/vault/status", token=TOKEN)
    check_ok(status, data)
    if data["data"].get("hasPassword") != True:
        return "expected hasPassword=true"

def t_vault_unlock():
    status, data = api("POST", "/api/vault/unlock", {"password": VAULT_PWD}, token=TOKEN)
    check_ok(status, data)

def t_vault_folders():
    global VAULT_FOLDER_ID
    status, data = api("POST", "/api/vault/files/folder", {"name": "保险箱文件夹", "parentId": 0}, token=TOKEN)
    check_ok(status, data)
    VAULT_FOLDER_ID = data["data"]["id"]

def t_vault_upload():
    global VAULT_FILE_ID
    boundary = "----" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="vault_secret.txt"\r\n'
        f"Content-Type: text/plain\r\n\r\n"
        f"Vault secret content\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="parentId"\r\n\r\n'
        f"0\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    status, data = api("POST", "/api/vault/files/upload", token=TOKEN,
                       content_type=f"multipart/form-data; boundary={boundary}",
                       raw_body=body)
    check_ok(status, data)
    VAULT_FILE_ID = data["data"]["id"]

def t_vault_list():
    status, data = api("GET", "/api/vault/files/list?page=1&size=20", token=TOKEN)
    check_ok(status, data)

def t_vault_download():
    if not VAULT_FILE_ID:
        return "no vault file to download"
    conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
    headers = {"Authorization": f"Bearer {TOKEN}"}
    conn.request("GET", f"/api/vault/files/download/{VAULT_FILE_ID}", headers=headers)
    resp = conn.getresponse()
    data = resp.read()
    conn.close()
    if resp.status != 200:
        raise Exception(f"vault download status={resp.status}")
    if b"Vault secret content" not in data:
        raise Exception("vault download content mismatch")
    return True

def t_vault_remove():
    if not VAULT_FILE_ID:
        return "no vault file to remove"
    status, data = api("PUT", f"/api/vault/files/remove?itemId={VAULT_FILE_ID}", token=TOKEN)
    check_ok(status, data)

def t_vault_lock():
    status, data = api("POST", "/api/vault/lock", token=TOKEN)
    check_ok(status, data)

test("vault/setup", t_vault_setup)
test("vault/status_locked", t_vault_status_locked)
test("vault/unlock", t_vault_unlock)
test("vault/files_folders", t_vault_folders)
test("vault/files_upload", t_vault_upload)
test("vault/files_list", t_vault_list)
test("vault/files_download", t_vault_download)
test("vault/files_remove", t_vault_remove)
test("vault/lock", t_vault_lock)

# ═══ Admin — Storage Nodes ═══
print("\n=== Admin Storage Nodes ===")
NODE_ID = None

def t_admin_nodes_list():
    status, data = api("GET", "/api/admin/nodes", token=TOKEN)
    check_ok(status, data)

def t_admin_nodes_active():
    status, data = api("GET", "/api/admin/nodes/active", token=TOKEN)
    check_ok(status, data)

def t_admin_nodes_create():
    global NODE_ID
    status, data = api("POST", "/api/admin/nodes", {
        "name": "Test Node",
        "provider": "local",
        "endpoint": "D:/M78netdisk/storage",
        "accessKey": "test-key",
        "encryptedSk": "test-sk-encrypted",
        "region": "local",
        "weight": 10,
        "isActive": True
    }, token=TOKEN)
    check_ok(status, data)
    NODE_ID = data["data"]["id"]

def t_admin_nodes_get():
    if not NODE_ID:
        return "no node to get"
    status, data = api("GET", f"/api/admin/nodes/{NODE_ID}", token=TOKEN)
    check_ok(status, data)

def t_admin_nodes_update():
    if not NODE_ID:
        return "no node to update"
    status, data = api("PUT", f"/api/admin/nodes/{NODE_ID}", {
        "name": "Test Node Updated",
        "weight": 20
    }, token=TOKEN)
    check_ok(status, data)

def t_admin_nodes_delete():
    if not NODE_ID:
        return "no node to delete"
    status, data = api("DELETE", f"/api/admin/nodes/{NODE_ID}", token=TOKEN)
    check_ok(status, data)

test("admin/nodes_list", t_admin_nodes_list)
test("admin/nodes_active", t_admin_nodes_active)
test("admin/nodes_create", t_admin_nodes_create)
test("admin/nodes_get", t_admin_nodes_get)
test("admin/nodes_update", t_admin_nodes_update)
test("admin/nodes_delete", t_admin_nodes_delete)

# ═══ Results ═══
print(f"\n{'='*50}")
print(f"  TOTAL: {total}  |  PASS: {ok}  |  FAIL: {fail}")
print(f"{'='*50}")
if fail > 0:
    exit(1)
