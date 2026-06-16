"""Full integration test for M78 NetDisk"""
import http.client
import json

HOST = "localhost"
PORT = 8080
CAPTCHA_KEY = "testkey4"
CAPTCHA_CODE = "42"
USER = "test"
PASS = "test123"

ok = 0
fail = 0

def api(method, path, body=None, token=None):
    conn = http.client.HTTPConnection(HOST, PORT, timeout=10)
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    conn.request(method, path, json.dumps(body) if body else None, headers)
    resp = conn.getresponse()
    data = json.loads(resp.read().decode())
    conn.close()
    return resp.status, data

def test(name, fn):
    global ok, fail
    try:
        result = fn()
        if result is True or result is None:
            print(f"  PASS {name}")
            ok += 1
        else:
            print(f"  FAIL {name}: {result}")
            fail += 1
    except Exception as e:
        print(f"  FAIL {name}: {e}")
        fail += 1

def check_ok(status, data):
    if status != 200 or data.get("code") != 200:
        raise Exception(f"Expected 200, got {status} {data}")

# Login
r = api("POST", "/api/users/login", {
    "username": USER, "password": PASS,
    "captchaKey": CAPTCHA_KEY, "captchaCode": CAPTCHA_CODE
})
check_ok(*r)
token = r[1]["data"]["accessToken"]
print("=" * 40)
print("LOGIN OK")
print("=" * 40)

# ─── Album Tests ───
print("\n=== Album ===")

def t_album_create():
    r = api("POST", "/api/albums", {"name": "Test Album"}, token)
    check_ok(*r)
    global AID
    AID = r[1]["data"]["id"]
    return True

def t_album_detail():
    r = api("GET", f"/api/albums/{AID}?page=1&size=20", token=token)
    check_ok(*r)
    return True

def t_album_list():
    r = api("GET", "/api/albums?page=1&size=20", token=token)
    check_ok(*r)
    return True

AID = None
test("album/create", t_album_create)
test("album/detail", t_album_detail)
test("album/list", t_album_list)

# ─── Vault Tests ───
print("\n=== Vault ===")

def t_vault_setup():
    r = api("POST", "/api/vault/setup", {
        "loginPassword": PASS,
        "vaultPassword": "vault789",
        "confirmPassword": "vault789"
    }, token)
    check_ok(*r)

def t_vault_status():
    r = api("GET", "/api/vault/status", token=token)
    check_ok(*r)

def t_vault_lock():
    r = api("POST", "/api/vault/lock", token=token)
    check_ok(*r)

def t_vault_unlock():
    r = api("POST", "/api/vault/unlock", {"password": "vault789"}, token)
    check_ok(*r)

def t_vault_list():
    r = api("GET", "/api/vault/files/list?page=1&size=20", token=token)
    check_ok(*r)

test("vault/setup", t_vault_setup)
test("vault/status", t_vault_status)
test("vault/lock", t_vault_lock)
test("vault/unlock", t_vault_unlock)
test("vault/files/list", t_vault_list)

print(f"\n{'='*40}")
print(f"RESULTS: {ok} PASS, {fail} FAIL")
print(f"{'='*40}")
