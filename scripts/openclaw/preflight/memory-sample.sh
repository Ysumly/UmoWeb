#!/usr/bin/env bash

set -euo pipefail

MEMINFO_PATH="${MEMINFO_PATH:-/proc/meminfo}"
OUTPUT_PATH="${1:-/var/log/umoweb/openclaw-preflight/baseline.csv}"
CSV_HEADER="timestamp,mem_total_kib,mem_available_kib,swap_total_kib,swap_free_kib,umoweb_frontend_bytes,umoweb_backend_bytes,umoweb_mysql_bytes,umoweb_health,openclaw_service_state"

read_meminfo_kib() {
    local key="$1"

    awk -v key="$key" '$1 == key ":" && $2 ~ /^[0-9]+$/ { print $2; exit }' "$MEMINFO_PATH"
}

parse_docker_snapshot() {
    local docker_stats="$1"
    local compose_ps="$2"

    if ! command -v python3 >/dev/null 2>&1; then
        printf '0,0,0,unknown\n'
        return 0
    fi

    DOCKER_STATS="$docker_stats" COMPOSE_PS="$compose_ps" python3 - <<'PY'
import json
import os
import re


UNIT_MULTIPLIERS = {
    "B": 1,
    "KB": 1000,
    "kB": 1000,
    "KiB": 1024,
    "MB": 1000**2,
    "MiB": 1024**2,
    "GB": 1000**3,
    "GiB": 1024**3,
    "TB": 1000**4,
    "TiB": 1024**4,
    "PB": 1000**5,
    "PiB": 1024**5,
}


def parse_bytes(value):
    match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)([A-Za-z]+)", value.strip())
    if not match:
        return 0
    number = float(match.group(1))
    multiplier = UNIT_MULTIPLIERS.get(match.group(2), 0)
    return int(number * multiplier)


frontend_bytes = 0
backend_bytes = 0
mysql_bytes = 0

for line in os.environ.get("DOCKER_STATS", "").splitlines():
    fields = line.split(None, 1)
    if len(fields) != 2:
        continue
    name, usage = fields
    memory_value = usage.split("/", 1)[0].strip()
    amount = parse_bytes(memory_value)
    if "frontend" in name:
        frontend_bytes += amount
    elif "backend" in name:
        backend_bytes += amount
    elif "mysql" in name:
        mysql_bytes += amount


compose_text = os.environ.get("COMPOSE_PS", "").strip()
health = "unknown"

if compose_text:
    try:
        parsed = json.loads(compose_text)
    except json.JSONDecodeError:
        parsed = []
        for line in compose_text.splitlines():
            try:
                parsed.append(json.loads(line))
            except json.JSONDecodeError:
                pass

    if isinstance(parsed, dict):
        parsed = [parsed]

    if isinstance(parsed, list):
        services = {}
        for container in parsed:
            if not isinstance(container, dict):
                continue
            service = str(container.get("Service") or container.get("Name") or "").lower()
            state = str(container.get("State") or "").lower()
            container_health = str(container.get("Health") or "").lower()
            for expected in ("frontend", "backend", "mysql"):
                if expected in service:
                    services[expected] = (state, container_health)

        if set(services) == {"frontend", "backend", "mysql"}:
            if all(
                state == "running" and container_health in ("", "healthy")
                for state, container_health in services.values()
            ):
                health = "healthy"
            else:
                health = "unhealthy"

print(f"{frontend_bytes},{backend_bytes},{mysql_bytes},{health}")
PY
}

mem_total_kib="$(read_meminfo_kib "MemTotal")"
mem_available_kib="$(read_meminfo_kib "MemAvailable")"
swap_total_kib="$(read_meminfo_kib "SwapTotal")"
swap_free_kib="$(read_meminfo_kib "SwapFree")"

for value in "$mem_total_kib" "$mem_available_kib" "$swap_total_kib" "$swap_free_kib"; do
    if [[ -z "$value" ]]; then
        printf 'FAIL: required memory field is missing from %s\n' "$MEMINFO_PATH" >&2
        exit 1
    fi
done

docker_stats=""
compose_ps=""
if command -v docker >/dev/null 2>&1; then
    docker_stats="$(docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' 2>/dev/null || true)"
    compose_ps="$(docker compose -p umoweb --env-file /opt/umoweb/.env.docker -f /opt/umoweb/compose.yaml ps --format json 2>/dev/null || true)"
fi

docker_snapshot="$(parse_docker_snapshot "$docker_stats" "$compose_ps")"
IFS=',' read -r umoweb_frontend_bytes umoweb_backend_bytes umoweb_mysql_bytes umoweb_health <<< "$docker_snapshot"

openclaw_service_state="not-installed"
if command -v systemctl >/dev/null 2>&1 && systemctl cat openclaw.service >/dev/null 2>&1; then
    openclaw_service_state="present"
fi

if [[ ! -f "$OUTPUT_PATH" || ! -s "$OUTPUT_PATH" ]]; then
    printf '%s\n' "$CSV_HEADER" > "$OUTPUT_PATH"
fi

timestamp="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$timestamp" \
    "$mem_total_kib" \
    "$mem_available_kib" \
    "$swap_total_kib" \
    "$swap_free_kib" \
    "$umoweb_frontend_bytes" \
    "$umoweb_backend_bytes" \
    "$umoweb_mysql_bytes" \
    "$umoweb_health" \
    "$openclaw_service_state" >> "$OUTPUT_PATH"
