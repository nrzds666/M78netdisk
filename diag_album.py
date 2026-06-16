import http.client
import json

HOST = "localhost"
PORT = 8080

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

# 1. Login
status, data = api("POST", "/api/users/login", {
    "username": "test", "password": "test123",
    "captchaKey": "testkey4", "captchaCode": "42"
})
assert status == 200 and data.get("code") == 200, f"Login failed: {status} {data}"
token = data["data"]["accessToken"]
print(f"LOGIN OK, token={token[:20]}...")

# 2. Create album
status, data = api("POST", "/api/albums", {"name": "Diag Album"}, token)
print(f"CREATE ALBUM => status={status}, body={data}")
if data.get("code") != 200:
    print("ALBUM CREATE FAILED, aborting")
    exit(1)
aid = data["data"]["id"]
print(f"Album id={aid}")

# 3. Get album detail (the 500 endpoint)
status, data = api("GET", f"/api/albums/{aid}?page=1&size=20", token=token)
print(f"ALBUM DETAIL => status={status}, body={data}")
