#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
server="$repo_root/scripts/ai/fake-openai-server.py"
python_bin="${PYTHON_BIN:-python3}"
curl_bin="${CURL_BIN:-curl}"
port="${FAKE_OPENAI_PORT:-19090}"
log_file="$(mktemp)"
request_file="$(mktemp)"

server_pid=""

cleanup() {
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
        kill "$server_pid" 2>/dev/null || true
        wait "$server_pid" 2>/dev/null || true
    fi
    rm -f "$request_file"
    rm -f "$log_file"
}
trap cleanup EXIT

cat >"$request_file" <<'JSON'
{"model":"fake-model","messages":[{"role":"system","content":"系统"},{"role":"user","content":"正文"}],"stream":false}
JSON

"$python_bin" "$server" --port "$port" >"$log_file" 2>&1 &
server_pid=$!

ready=false
for _ in $(seq 1 40); do
    if "$curl_bin" --noproxy '*' --silent --max-time 1 \
        --output /dev/null "http://127.0.0.1:$port/unknown" 2>/dev/null; then
        ready=true
        break
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        echo "Fake OpenAI server exited before becoming ready." >&2
        cat "$log_file" >&2
        exit 1
    fi
    sleep 0.1
done

if [[ "$ready" != "true" ]]; then
    echo "Fake OpenAI server did not become ready." >&2
    cat "$log_file" >&2
    exit 1
fi

response="$(
    "$curl_bin" --noproxy '*' --silent --show-error --max-time 5 \
        --header 'Content-Type: application/json' \
        --data-binary @"$request_file" \
        "http://127.0.0.1:$port/chat/completions"
)"

"$python_bin" - "$response" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
choice = payload["choices"][0]
assert choice["message"]["content"] == "正文", choice["message"]["content"]
assert choice["finish_reason"] == "stop", choice["finish_reason"]
assert payload["usage"]["input_tokens"] == 4, payload["usage"]
assert payload["usage"]["output_tokens"] == 2, payload["usage"]
PY

invalid_status="$(
    "$curl_bin" --noproxy '*' --silent --max-time 5 \
        --output /dev/null --write-out '%{http_code}' \
        --header 'Content-Type: application/json' \
        --data '{invalid-json' \
        "http://127.0.0.1:$port/chat/completions"
)"
if [[ "$invalid_status" != "400" ]]; then
    echo "Expected invalid JSON to return 400, got $invalid_status" >&2
    exit 1
fi

unknown_status="$(
    "$curl_bin" --noproxy '*' --silent --max-time 5 \
        --output /dev/null --write-out '%{http_code}' \
        "http://127.0.0.1:$port/unknown"
)"
if [[ "$unknown_status" != "404" ]]; then
    echo "Expected unknown path to return 404, got $unknown_status" >&2
    exit 1
fi

kill "$server_pid"
wait "$server_pid" 2>/dev/null || true
server_pid=""

if "$curl_bin" --noproxy '*' --silent --max-time 2 \
    --output /dev/null "http://127.0.0.1:$port/unknown" 2>/dev/null; then
    echo "Fake OpenAI server is still listening after shutdown." >&2
    exit 1
fi

echo "Fake OpenAI server tests passed"
