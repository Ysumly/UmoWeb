#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=access-lib.sh
source "$SCRIPT_DIR/access-lib.sh"

BASE_URL="${1:-}"
[[ -n "$BASE_URL" ]] || access_die "usage: $0 BASE_URL"
BASE_URL="${BASE_URL%/}"

ACCESS_REQUIRE_CONFIG=1 access_load_config
access_require_command curl
access_require_command python3
access_require_command ss

python3 "$SCRIPT_DIR/access_maintenance.py" render-config \
    --config "$ACCESS_CONFIG_FILE" \
    --privacy-output "$ACCESS_REPORT_DIR/.privacy-verify.json" \
    --trusted-output "$ACCESS_REPORT_DIR/.trusted-verify.conf"
trap 'rm -f "$ACCESS_REPORT_DIR/.privacy-verify.json" "$ACCESS_REPORT_DIR/.trusted-verify.conf"' EXIT

privacy_response="$(
    curl --fail --silent --show-error --max-time 15 \
        "$BASE_URL/privacy-config.json"
)"
expected_privacy="$(cat "$ACCESS_REPORT_DIR/.privacy-verify.json")"
python3 - "$expected_privacy" "$privacy_response" <<'PY'
import json
import sys

expected = json.loads(sys.argv[1])
actual = json.loads(sys.argv[2])
if actual != expected:
    raise SystemExit("public privacy configuration does not match access policy")
PY

for directory in "$ACCESS_LOG_DIR" "$ACCESS_AGGREGATE_DIR" "$ACCESS_REPORT_DIR"; do
    [[ -d "$directory" ]] || access_die "missing access directory: $directory"
    [[ "$(stat -c '%a' "$directory")" == "750" ]] ||
        access_die "access directory permissions must be 0750: $directory"
done
for file in "$ACCESS_CONFIG_FILE" "$ACCESS_LOG_DIR/access.log"; do
    [[ -e "$file" ]] || continue
    [[ "$(stat -c '%a' "$file")" == "600" || "$(stat -c '%a' "$file")" == "640" ]] ||
        access_die "access file permissions are too broad: $file"
done

health="$(curl --fail --silent --show-error --max-time 5 "http://${ACCESS_REPORT_HOST}:${ACCESS_REPORT_PORT}/healthz")"
[[ "$health" == "ok" ]] || access_die "access report health check failed"

if ! ss -ltnH "sport = :$ACCESS_REPORT_PORT" | grep -q "127\\.0\\.0\\.1:$ACCESS_REPORT_PORT"; then
    access_die "access report is not listening on 127.0.0.1:$ACCESS_REPORT_PORT"
fi
if ss -ltnH "sport = :$ACCESS_REPORT_PORT" | grep -Eq '(0\.0\.0\.0|\\[::\\]):'"$ACCESS_REPORT_PORT"; then
    access_die "access report is exposed beyond loopback"
fi

sentinel="umo-access-$(date -u +%Y%m%dT%H%M%SZ)-$$"
before_lines="$(wc -l < "$ACCESS_LOG_DIR/access.log" 2>/dev/null || printf '0')"
curl --silent --show-error --max-time 15 \
    --output /dev/null \
    --request POST \
    --header 'Content-Type: application/json' \
    --header "Authorization: Bearer $sentinel" \
    --header "Cookie: umo_secret=$sentinel" \
    --header 'X-Forwarded-For: 203.0.113.250' \
    --data "{\"password\":\"$sentinel\"}" \
    "$BASE_URL/api/admin/contents?token=$sentinel" || true

for _ in $(seq 1 20); do
    current_lines="$(wc -l < "$ACCESS_LOG_DIR/access.log" 2>/dev/null || printf '0')"
    if ((current_lines > before_lines)); then
        break
    fi
    sleep 0.1
done

python3 - "$ACCESS_LOG_DIR/access.log" "$sentinel" <<'PY'
import ipaddress
import json
import sys

path, sentinel = sys.argv[1:]
matches = []
with open(path, "r", encoding="utf-8") as handle:
    for raw_line in handle:
        if "/api/admin/contents" not in raw_line:
            continue
        payload = json.loads(raw_line)
        if set(payload) != {"time", "ip", "method", "path", "status", "bytes"}:
            raise SystemExit("access log contains fields outside the six-field contract")
        if payload["method"] == "POST" and payload["path"] == "/api/admin/contents":
            matches.append((raw_line, payload))

if not matches:
    raise SystemExit("synthetic access request was not written to the access log")
line, payload = matches[-1]
if sentinel in line:
    raise SystemExit("access log contains a sensitive sentinel value")
if payload["path"] != "/api/admin/contents":
    raise SystemExit("access log retained a query string")
if payload["ip"] == "203.0.113.250":
    raise SystemExit("untrusted X-Forwarded-For value was accepted")
ipaddress.ip_address(payload["ip"])
PY

printf 'Access deployment verified: six-field logs, privacy config, permissions, and loopback report.\n'
