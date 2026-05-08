# Burp Bridge

Burp Suite Pro extension (Montoya API) that exposes a local HTTP API on `127.0.0.1` so you can drive Burp from external scripts.

## Build

```bash
gradle shadowJar
```

Output: `build/libs/burp-bridge.jar`

## Install in Burp

1. **Extensions > Installed > Add**
2. Extension type: **Java**
3. Select `build/libs/burp-bridge.jar`
4. Check the Output tab for the token and URL

The extension prints something like:

```
=== Burp Bridge ===
URL:   http://127.0.0.1:8765
Token: <random-base64url-token>

export BURP_BRIDGE_TOKEN=<random-base64url-token>
```

Override the port with `-Dburpbridge.port=9999` in Burp's JVM args.

## Endpoints

All endpoints return JSON. All except `/health` require `Authorization: Bearer <token>`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Liveness probe. Returns `{"ok":true}` |
| GET | `/history` | Proxy history. Params: `host`, `method`, `status`, `limit` (default 1000), `full` |
| GET | `/history/{index}` | Single history item (always full) |
| GET | `/sitemap` | Site map. Param: `prefix` |
| GET | `/scope?url=...` | Check if URL is in scope |
| POST | `/scope` | Add URL to scope. Body: `{"url":"..."}` |
| POST | `/repeater` | Send to Repeater. Body: `{"index":N,"tab":"..."}` or `{"raw":"...","host":"...","port":443,"tls":true,"tab":"..."}` |
| POST | `/send` | Send request and get response. Body: `{"raw":"...","host":"...","port":443,"tls":true}` |

## Examples

### curl

```bash
export BURP_BRIDGE_TOKEN=<token>

# Health check (no auth needed)
curl http://127.0.0.1:8765/health

# Get last 5 history items
curl -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  "http://127.0.0.1:8765/history?limit=5"

# Check scope
curl -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  "http://127.0.0.1:8765/scope?url=https://example.com"

# Send a raw request
curl -X POST -H "Authorization: Bearer $BURP_BRIDGE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"raw":"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n","host":"example.com","port":443,"tls":true}' \
  http://127.0.0.1:8765/send
```

### Python

```python
from client.burp_bridge import BurpBridge

bridge = BurpBridge()  # reads BURP_BRIDGE_TOKEN from env
items = bridge.history(host="example.com", limit=10)
for item in items["items"]:
    print(item["method"], item["url"], item.get("status"))
```

## Adding new endpoints

1. Add a handler method in `Handlers.java`
2. Register the route in `ApiServer.registerRoutes()` with `auth(h::yourMethod)`
3. Rebuild with `gradle shadowJar`

### Future endpoint ideas (not implemented)

- `/scanner` — active/passive scan via `api.scanner()`
- `/intruder` — send to Intruder
- `/collaborator` — Burp Collaborator interaction
- `/issues` — reported issues

See the [Montoya API docs](https://portswigger.github.io/burp-extensions-montoya-api/) for available interfaces.

## Security

- The server binds to `127.0.0.1` only — not reachable from the network.
- A random bearer token is generated on each extension load and printed to the Burp output tab.
- The token rotates every time the extension is reloaded.
- Anyone with the token and local access can control your Burp instance — treat the token like a password.
