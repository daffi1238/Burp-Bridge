# Burp Bridge

Burp Suite Pro extension that exposes a local HTTP API on `127.0.0.1` to control Burp from external scripts (Python, Bash, automation tools, AI agents, etc.).

Built with the [Montoya API](https://portswigger.github.io/burp-extensions-montoya-api/) — Burp's modern, official extension framework.

## Why Burp Bridge

- **Automation**: query proxy history, send requests, or manage scope from any language.
- **AI agent integration**: connect LLMs or orchestrators that consume JSON over HTTP.
- **Quick scripting**: replace repetitive manual tasks with a few lines of curl or Python.
- **Zero external network dependencies**: uses the JDK's built-in HTTP server (`com.sun.net.httpserver`).

## Installation

### Option 1 — Use a prebuilt jar

1. Copy `burp-bridge.jar` to your Burp extensions folder.
2. In Burp: **Extensions > Installed > Add**.
3. Extension type: **Java**.
4. Select the `burp-bridge.jar` file.
5. Check the extension's **Output** tab for the URL and token.

### Option 2 — Build from source

```bash
git clone <repo-url>
cd burp-bridge
gradle shadowJar
```

The jar is generated at `build/libs/burp-bridge.jar`. Load it in Burp as described above.

> **Build requirements**: Java 17+ JDK, Gradle installed.

## Configuration

When the extension loads, it prints to the Output tab:

```
=== Burp Bridge ===
URL:   http://127.0.0.1:8765
Token: aBcDeFgHiJkLmNoPqRsTuVwXyZaB

export BURP_BRIDGE_TOKEN=aBcDeFgHiJkLmNoPqRsTuVwXyZaB
```

- **Default port**: `8765`. Override it by adding `-Dburpbridge.port=9999` to Burp's JVM args (Project options > Misc or startup command line).
- **Token**: randomly generated each time the extension is loaded or reloaded. Copy the `export` line into your terminal to use it in scripts.

## Authentication

All endpoints except `/health` require the header:

```
Authorization: Bearer <token>
```

If the token is missing or incorrect, the server responds with `401 {"error":"unauthorized"}`.

## Endpoints

All endpoints return JSON with `Content-Type: application/json`.

### GET `/health` — Check that the bridge is running

No authentication required.

```bash
curl http://127.0.0.1:8765/health
```

```json
{"ok": true}
```

---

### GET `/history` — Query proxy history

Lists requests that have passed through Burp's proxy.

**Query parameters** (all optional):

| Parameter | Type | Description |
|-----------|------|-------------|
| `host` | string | Filter by substring match on hostname |
| `method` | string | Filter by HTTP method (case-insensitive) |
| `status` | int | Filter by response status code (skips items without a response) |
| `limit` | int | Maximum results (default: 1000) |
| `full` | bool | If `true`, include full raw request and response as strings |

```bash
# Last 5 requests
curl -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  "http://127.0.0.1:8765/history?limit=5"

# Only POSTs to example.com that returned 200
curl -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  "http://127.0.0.1:8765/history?host=example.com&method=POST&status=200"
```

**Response**:

```json
{
  "count": 2,
  "total": 487,
  "items": [
    {
      "index": 0,
      "url": "https://example.com/api/login",
      "method": "POST",
      "host": "example.com",
      "port": 443,
      "secure": true,
      "status": 200,
      "length": 1234,
      "mime": "JSON"
    }
  ]
}
```

When `full=true`, each item also includes `"request"` and `"response"` with the full raw HTTP representation.

---

### GET `/history/{index}` — Get a specific history item

Always returns full request and response.

```bash
curl -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  http://127.0.0.1:8765/history/42
```

Returns `404` if the index is out of range, `400` if it's not a valid number.

---

### GET `/sitemap` — Query the site map

Lists request/response pairs known to Burp's site map.

| Parameter | Type | Description |
|-----------|------|-------------|
| `prefix` | string | Filter URLs starting with this prefix |

```bash
# Full site map
curl -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  http://127.0.0.1:8765/sitemap

# Only URLs under a specific domain
curl -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  "http://127.0.0.1:8765/sitemap?prefix=https://example.com"
```

**Response**:

```json
{
  "count": 15,
  "items": [
    {"url": "https://example.com/", "method": "GET", "status": 200},
    {"url": "https://example.com/api/users", "method": "GET", "status": 200}
  ]
}
```

---

### GET `/scope` — Check if a URL is in scope

```bash
curl -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  "http://127.0.0.1:8765/scope?url=https://example.com"
```

```json
{"url": "https://example.com", "inScope": true}
```

Returns `400` if the `url` parameter is missing.

---

### POST `/scope` — Add a URL to scope

```bash
curl -X POST -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}' \
  http://127.0.0.1:8765/scope
```

```json
{"added": "https://example.com"}
```

---

### POST `/repeater` — Send a request to Repeater

**Mode 1** — From proxy history:

```bash
curl -X POST -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"index": 42, "tab": "my-test"}' \
  http://127.0.0.1:8765/repeater
```

**Mode 2** — From a raw request:

```bash
curl -X POST -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "raw": "GET /api/users HTTP/1.1\r\nHost: example.com\r\n\r\n",
    "host": "example.com",
    "port": 443,
    "tls": true,
    "tab": "users-test"
  }' \
  http://127.0.0.1:8765/repeater
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `index` | int | — | Index of the item in proxy history |
| `raw` | string | — | Raw HTTP request |
| `host` | string | — | Target hostname |
| `port` | int | 443 | Target port |
| `tls` | bool | true | Use HTTPS |
| `tab` | string | `"from-bridge"` | Repeater tab name |

```json
{"sent": true, "tab": "my-test"}
```

---

### POST `/send` — Send a request and get the response

Sends an HTTP request through Burp and returns the full response.

```bash
curl -X POST -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "raw": "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n",
    "host": "example.com",
    "port": 443,
    "tls": true
  }' \
  http://127.0.0.1:8765/send
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `raw` | string | *required* | Raw HTTP request |
| `host` | string | *required* | Target hostname |
| `port` | int | 443 | Target port |
| `tls` | bool | true | Use HTTPS |

```json
{
  "status": 200,
  "response": "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n..."
}
```

## Python client

A reference client is included in `client/burp_bridge.py`.

```python
from client.burp_bridge import BurpBridge

# Reads token from the BURP_BRIDGE_TOKEN environment variable
bridge = BurpBridge()

# Query history
items = bridge.history(host="example.com", limit=10)
for item in items["items"]:
    print(f"[{item.get('status')}] {item['method']} {item['url']}")

# Check scope
print(bridge.in_scope("https://example.com"))

# Add to scope
bridge.add_scope("https://new-target.com")

# Send to Repeater from history
bridge.to_repeater_from_history(index=5, tab="test-login")

# Send raw request to Repeater
bridge.to_repeater_raw(
    raw="GET /api/v1/users HTTP/1.1\r\nHost: example.com\r\n\r\n",
    host="example.com"
)

# Send request and get response
resp = bridge.send(
    raw="GET / HTTP/1.1\r\nHost: example.com\r\n\r\n",
    host="example.com"
)
print(f"Status: {resp['status']}")
```

**Quick smoke test**:

```bash
export BURP_BRIDGE_TOKEN=<your-token>
python3 client/burp_bridge.py
```

## Error codes

| Code | Meaning |
|------|---------|
| 400 | Missing or invalid parameter |
| 401 | Token missing or incorrect |
| 404 | Resource not found (e.g. index out of range) |
| 405 | HTTP method not allowed on that endpoint |
| 500 | Internal error (check the extension's Errors tab in Burp) |

All errors return JSON: `{"error": "description"}`.

## Extending the plugin

Adding a new endpoint takes 3 steps:

1. Create a method in `Handlers.java`:
   ```java
   public void myEndpoint(HttpExchange ex) {
       if (!"GET".equals(ex.getRequestMethod())) {
           ApiServer.respond(ex, 405, "{\"error\":\"method\"}");
           return;
       }
       // your logic here
       ApiServer.respond(ex, 200, "{\"result\":\"ok\"}");
   }
   ```

2. Register the route in `ApiServer.registerRoutes()`:
   ```java
   server.createContext("/my-endpoint", auth(handlers::myEndpoint));
   ```

3. Rebuild:
   ```bash
   gradle shadowJar
   ```

### Future endpoint ideas

| Endpoint | Montoya API | Description |
|----------|-------------|-------------|
| `/scanner` | `api.scanner()` | Launch active/passive scans |
| `/intruder` | `api.intruder()` | Send to Intruder |
| `/collaborator` | `api.collaborator()` | Burp Collaborator interaction |
| `/issues` | `api.scanner().issues()` | Query discovered vulnerabilities |

See the [Montoya API documentation](https://portswigger.github.io/burp-extensions-montoya-api/) for all available interfaces.

## Security

- **Loopback only**: the server listens exclusively on `127.0.0.1` — not reachable from the network.
- **Rotating token**: a random 24-byte token (base64url) is generated each time the extension loads or reloads.
- **No token persistence**: the token only exists in memory and in Burp's Output tab. It is never written to disk.
- **Treat it like a password**: anyone with the token and local access can control your Burp instance (send requests, read history, modify scope).

## Project structure

```
burp-bridge/
├── build.gradle                        # Build config (Gradle + Shadow)
├── settings.gradle                     # Project name
├── src/main/java/burpbridge/
│   ├── BurpBridgeExtension.java        # Entry point — generates token, starts server
│   ├── ApiServer.java                  # HTTP server, routing, auth middleware
│   └── Handlers.java                   # Endpoint logic
└── client/
    └── burp_bridge.py                  # Reference Python client
```
