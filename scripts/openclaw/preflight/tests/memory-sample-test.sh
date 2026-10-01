#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="${BASH_SOURCE[0]%/*}"
if [[ "$SCRIPT_DIR" == "${BASH_SOURCE[0]}" ]]; then
    SCRIPT_DIR="."
fi
SCRIPT_DIR="$(cd -- "$SCRIPT_DIR" && pwd -P)"
SAMPLER="$SCRIPT_DIR/../memory-sample.sh"
EXPECTED_HEADER="timestamp,mem_total_kib,mem_available_kib,swap_total_kib,swap_free_kib,umoweb_frontend_bytes,umoweb_backend_bytes,umoweb_mysql_bytes,umoweb_health,openclaw_service_state"
FORBIDDEN_MUTATION_PATTERN='docker[[:space:]]+(stop|rm|kill)([^[:alnum:]_]|$)|docker[[:space:]]+compose[[:space:]].*([^[:alnum:]])(up|create|start|stop|restart|down|rm|exec|run)([^[:alnum:]_]|$)|systemctl[[:space:]]+(start|stop|restart|reload|disable|enable|mask|unmask|daemon-reload)([^[:alnum:]_]|$)|(^|[[:space:];])kill([[:space:]]|$)'

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}

assert_equal() {
    local expected="$1"
    local actual="$2"
    local message="$3"

    if [[ "$actual" != "$expected" ]]; then
        fail "$message (expected '$expected', got '$actual')"
    fi
}

if [[ ! -f "$SAMPLER" ]]; then
    fail "sampler not found at $SAMPLER"
fi

TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

FAKE_BIN="$TEMP_ROOT/bin"
MEMINFO_FIXTURE="$TEMP_ROOT/meminfo"
mkdir -p "$FAKE_BIN"

cat > "$MEMINFO_FIXTURE" <<'EOF'
MemTotal:       3887848 kB
MemFree:         123456 kB
MemAvailable:    987654 kB
SwapTotal:      2097152 kB
SwapFree:       1048576 kB
EOF

cat > "$FAKE_BIN/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash

set -euo pipefail

{
    printf 'docker'
    printf ' <%s>' "$@"
    printf '\n'
} >> "$FAKE_COMMAND_LOG"

if [[ "${FAKE_DOCKER_AVAILABLE:-1}" != "1" ]]; then
    printf 'docker unavailable\n' >&2
    exit 1
fi

if [[ "${1:-}" == "stats" ]]; then
    cat <<'EOF'
umoweb-frontend-1 32MiB / 1GiB
umoweb-backend-1 256.5MiB / 1GiB
umoweb-mysql-1 512KiB / 1GiB
EOF
    exit 0
fi

if [[ "${1:-}" == "compose" ]]; then
    backend_health="healthy"
    if [[ "${FAKE_COMPOSE_UNHEALTHY:-0}" == "1" ]]; then
        backend_health="unhealthy"
    fi
    printf '[{"Name":"umoweb-frontend-1","Service":"frontend","State":"running","Health":""},{"Name":"umoweb-backend-1","Service":"backend","State":"running","Health":"%s"},{"Name":"umoweb-mysql-1","Service":"mysql","State":"running","Health":"healthy"}]\n' "$backend_health"
    exit 0
fi

exit 64
FAKE_DOCKER

cat > "$FAKE_BIN/systemctl" <<'FAKE_SYSTEMCTL'
#!/usr/bin/env bash

set -euo pipefail

{
    printf 'systemctl'
    printf ' <%s>' "$@"
    printf '\n'
} >> "$FAKE_COMMAND_LOG"

if [[ "${1:-}" == "cat" && "${FAKE_OPENCLAW_PRESENT:-0}" == "1" ]]; then
    exit 0
fi

exit 1
FAKE_SYSTEMCTL

chmod +x "$FAKE_BIN/docker" "$FAKE_BIN/systemctl"

if grep -Eq "$FORBIDDEN_MUTATION_PATTERN" "$SAMPLER"; then
    fail "sampler source contains a forbidden mutating command"
fi

assert_output() {
    local output_file="$1"
    local expected_available="$2"
    local expected_frontend="$3"
    local expected_backend="$4"
    local expected_mysql="$5"
    local expected_health="$6"

    [[ -f "$output_file" ]] || fail "sampler did not create $output_file"

    local header
    local row
    header="$(sed -n '1p' "$output_file")"
    row="$(sed -n '2p' "$output_file")"

    assert_equal "$EXPECTED_HEADER" "$header" "CSV header is not stable"
    assert_equal "2" "$(wc -l < "$output_file" | tr -d ' ')" "expected one CSV sample row"
    assert_equal "10" "$(awk -F, 'NR == 2 { print NF }' "$output_file")" "CSV sample must contain 10 fields"

    local timestamp
    local mem_total_kib
    local mem_available_kib
    local swap_total_kib
    local swap_free_kib
    local frontend_bytes
    local backend_bytes
    local mysql_bytes
    local health
    local openclaw_state

    IFS=',' read -r timestamp mem_total_kib mem_available_kib swap_total_kib swap_free_kib \
        frontend_bytes backend_bytes mysql_bytes health openclaw_state <<< "$row"

    [[ "$timestamp" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] ||
        fail "timestamp is not ISO 8601 UTC: $timestamp"
    assert_equal "3887848" "$mem_total_kib" "MemTotal was not read correctly"
    assert_equal "$expected_available" "$mem_available_kib" "MemAvailable was not read correctly"
    assert_equal "2097152" "$swap_total_kib" "SwapTotal was not read correctly"
    assert_equal "1048576" "$swap_free_kib" "SwapFree was not read correctly"
    assert_equal "$expected_frontend" "$frontend_bytes" "frontend memory was not converted to bytes"
    assert_equal "$expected_backend" "$backend_bytes" "backend memory was not converted to bytes"
    assert_equal "$expected_mysql" "$mysql_bytes" "mysql memory was not converted to bytes"
    assert_equal "$expected_health" "$health" "container health was not classified correctly"
    assert_equal "not-installed" "$openclaw_state" "Gate 0 must report OpenClaw as not-installed"
}

healthy_output="$TEMP_ROOT/healthy.csv"
healthy_commands="$TEMP_ROOT/healthy-commands.log"
if ! MEMINFO_PATH="$MEMINFO_FIXTURE" \
    PATH="$FAKE_BIN:$PATH" \
    FAKE_COMMAND_LOG="$healthy_commands" \
    FAKE_DOCKER_AVAILABLE=1 \
    FAKE_OPENCLAW_PRESENT=0 \
    "$SAMPLER" "$healthy_output"; then
    fail "sampler failed with available Docker and a valid meminfo fixture"
fi

assert_output \
    "$healthy_output" \
    "987654" \
    "33554432" \
    "268959744" \
    "524288" \
    "healthy"

if grep -Eq "$FORBIDDEN_MUTATION_PATTERN" "$healthy_commands"; then
    fail "sampler invoked a forbidden mutating command"
fi

unavailable_output="$TEMP_ROOT/unavailable.csv"
unavailable_commands="$TEMP_ROOT/unavailable-commands.log"
if ! MEMINFO_PATH="$MEMINFO_FIXTURE" \
    PATH="$FAKE_BIN:$PATH" \
    FAKE_COMMAND_LOG="$unavailable_commands" \
    FAKE_DOCKER_AVAILABLE=0 \
    FAKE_OPENCLAW_PRESENT=0 \
    "$SAMPLER" "$unavailable_output"; then
    fail "sampler must continue when Docker is unavailable"
fi

assert_output \
    "$unavailable_output" \
    "987654" \
    "0" \
    "0" \
    "0" \
    "unknown"

if grep -Eq "$FORBIDDEN_MUTATION_PATTERN" "$unavailable_commands"; then
    fail "sampler invoked a forbidden mutating command while Docker was unavailable"
fi

unhealthy_output="$TEMP_ROOT/unhealthy.csv"
if ! MEMINFO_PATH="$MEMINFO_FIXTURE" \
    PATH="$FAKE_BIN:$PATH" \
    FAKE_COMMAND_LOG="$TEMP_ROOT/unhealthy-commands.log" \
    FAKE_DOCKER_AVAILABLE=1 \
    FAKE_COMPOSE_UNHEALTHY=1 \
    FAKE_OPENCLAW_PRESENT=0 \
    "$SAMPLER" "$unhealthy_output"; then
    fail "sampler failed while classifying an explicitly unhealthy container"
fi

assert_output \
    "$unhealthy_output" \
    "987654" \
    "33554432" \
    "268959744" \
    "524288" \
    "unhealthy"

printf 'PASS: memory sampler contract is satisfied\n'
