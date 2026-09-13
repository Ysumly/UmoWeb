#!/usr/bin/env bash
set -Eeuo pipefail

export PATH="/usr/bin:/bin:$PATH"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
NGINX_IMAGE="${NGINX_IMAGE:-nginx:1.29-alpine}"
TEMP_ROOT="$(mktemp -d)"
CONTAINER_ID=""

cleanup() {
    if [[ -n "$CONTAINER_ID" ]]; then
        docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true
    fi
    rm -rf "$TEMP_ROOT"
}
trap cleanup EXIT

mkdir -p "$TEMP_ROOT/logs" "$TEMP_ROOT/html" "$TEMP_ROOT/nginx"
printf '<!doctype html><title>access-log-test</title>\n' > "$TEMP_ROOT/html/index.html"
printf '{"rawRetentionDays":30,"aggregateRetentionDays":180}\n' \
    > "$TEMP_ROOT/html/privacy-config.json"
printf '# no trusted proxy in this test\n' > "$TEMP_ROOT/nginx/trusted-proxies.conf"

CONTAINER_ID="$(
    docker run \
        --detach \
        --add-host backend:127.0.0.1 \
        --publish 127.0.0.1::80 \
        --mount "type=bind,src=$REPO_ROOT/docker/frontend/nginx.conf,dst=/etc/nginx/conf.d/default.conf,readonly" \
        --mount "type=bind,src=$TEMP_ROOT/nginx/trusted-proxies.conf,dst=/etc/nginx/umoweb/trusted-proxies.conf,readonly" \
        --mount "type=bind,src=$TEMP_ROOT/logs,dst=/var/log/umoweb" \
        --mount "type=bind,src=$TEMP_ROOT/html,dst=/usr/share/nginx/html,readonly" \
        "$NGINX_IMAGE"
)"

PUBLISHED_ADDRESS="$(docker port "$CONTAINER_ID" 80/tcp | head -n 1)"
PORT="${PUBLISHED_ADDRESS##*:}"
[[ "$PORT" =~ ^[0-9]+$ ]] || {
    echo "FAIL: could not resolve Nginx test port from $PUBLISHED_ADDRESS" >&2
    exit 1
}

for _ in $(seq 1 50); do
    if curl --fail --silent "http://127.0.0.1:$PORT/healthz" >/dev/null 2>&1; then
        break
    fi
    if [[ "$(docker inspect --format '{{.State.Running}}' "$CONTAINER_ID")" != "true" ]]; then
        docker logs "$CONTAINER_ID" >&2
        exit 1
    fi
    sleep 0.1
done

sentinel="umo-access-log-test-$$"
curl --silent --output /dev/null \
    --header "Authorization: Bearer $sentinel" \
    --header "Cookie: umo_secret=$sentinel" \
    --header "X-Forwarded-For: 203.0.113.250" \
    "http://127.0.0.1:$PORT/privacy?token=$sentinel"

curl --silent --output /dev/null \
    --request POST \
    --header 'Content-Type: application/json' \
    --header "Authorization: Bearer $sentinel" \
    --header "Cookie: umo_secret=$sentinel" \
    --header "X-Forwarded-For: 203.0.113.250" \
    --data "{\"password\":\"$sentinel\"}" \
    "http://127.0.0.1:$PORT/api/admin/contents?token=$sentinel" || true

for _ in $(seq 1 30); do
    if [[ -s "$TEMP_ROOT/logs/access.log" ]] &&
        grep -q '/api/admin/contents' "$TEMP_ROOT/logs/access.log"; then
        break
    fi
    sleep 0.1
done

if [[ ! -s "$TEMP_ROOT/logs/access.log" ]] ||
    ! grep -q '/api/admin/contents' "$TEMP_ROOT/logs/access.log"; then
    echo "FAIL: expected access log entries were not written" >&2
    docker logs "$CONTAINER_ID" >&2 || true
    if [[ -f "$TEMP_ROOT/logs/access.log" ]]; then
        cat "$TEMP_ROOT/logs/access.log" >&2
    fi
    exit 1
fi

python3 - "$TEMP_ROOT/logs/access.log" "$sentinel" <<'PY'
import ipaddress
import json
import sys

path, sentinel = sys.argv[1:]
records = []
with open(path, "r", encoding="utf-8") as handle:
    for raw_line in handle:
        payload = json.loads(raw_line)
        if set(payload) != {"time", "ip", "method", "path", "status", "bytes"}:
            raise SystemExit("Nginx log did not contain exactly six fields")
        if sentinel in raw_line:
            raise SystemExit("Nginx log leaked a sensitive sentinel")
        ipaddress.ip_address(payload["ip"])
        if "?" in payload["path"]:
            raise SystemExit("Nginx log retained a query string")
        records.append(payload)

if not any(record["path"] == "/privacy" for record in records):
    raise SystemExit(f"GET /privacy was not logged: {records!r}")
if not any(
    record["method"] == "POST" and record["path"] == "/api/admin/contents"
    for record in records
):
    raise SystemExit("POST /api/admin/contents was not logged")
if any(record["ip"] == "203.0.113.250" for record in records):
    raise SystemExit("untrusted X-Forwarded-For value was accepted")
PY

printf 'Nginx access log integration test passed.\n'
