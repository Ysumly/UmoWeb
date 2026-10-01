#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="${BASH_SOURCE[0]%/*}"
if [[ "$SCRIPT_DIR" == "${BASH_SOURCE[0]}" ]]; then
    SCRIPT_DIR="."
fi
SCRIPT_DIR="$(cd -- "$SCRIPT_DIR" && pwd -P)"
PREFLIGHT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
INSTALLER="$PREFLIGHT_DIR/install-memory-preflight-timer.sh"

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}

assert_file_contains() {
    local file="$1"
    local expected="$2"

    grep -Fq -- "$expected" "$file" ||
        fail "expected '$expected' in $file"
}

if [[ ! -f "$INSTALLER" ]]; then
    fail "installer not found at $INSTALLER"
fi

TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

FAKE_BIN="$TEMP_ROOT/bin"
INSTALL_ROOT="$TEMP_ROOT/opt/umoweb"
SYSTEMD_ROOT="$TEMP_ROOT/etc/systemd/system"
LOG_ROOT="$TEMP_ROOT/var/log/umoweb/openclaw-preflight"
COMMAND_LOG="$TEMP_ROOT/systemctl.log"
mkdir -p \
    "$FAKE_BIN" \
    "$INSTALL_ROOT/scripts/openclaw/preflight"

cp "$PREFLIGHT_DIR/memory-sample.sh" \
    "$INSTALL_ROOT/scripts/openclaw/preflight/memory-sample.sh"

cat > "$FAKE_BIN/id" <<'FAKE_ID'
#!/usr/bin/env bash

set -euo pipefail

if [[ "${1:-}" == "-u" ]]; then
    printf '0\n'
    exit 0
fi

exec /usr/bin/id "$@"
FAKE_ID

cat > "$FAKE_BIN/systemctl" <<'FAKE_SYSTEMCTL'
#!/usr/bin/env bash

set -euo pipefail

printf 'systemctl' >> "$FAKE_COMMAND_LOG"
printf ' <%s>' "$@" >> "$FAKE_COMMAND_LOG"
printf '\n' >> "$FAKE_COMMAND_LOG"

if [[ "${1:-}" == "cat" && "${2:-}" == "openclaw.service" ]]; then
    [[ "${FAKE_OPENCLAW_PRESENT:-0}" == "1" ]] && exit 0
    exit 1
fi

exit 0
FAKE_SYSTEMCTL

chmod +x "$FAKE_BIN/id" "$FAKE_BIN/systemctl"

if PATH="$FAKE_BIN:$PATH" \
    FAKE_COMMAND_LOG="$COMMAND_LOG" \
    INSTALL_ROOT="$INSTALL_ROOT" \
    SYSTEMD_ROOT="$SYSTEMD_ROOT" \
    LOG_ROOT="$LOG_ROOT" \
    "$INSTALLER" invalid >/dev/null 2>&1; then
    fail "installer accepted an invalid mode"
fi

if ! PATH="$FAKE_BIN:$PATH" \
    FAKE_COMMAND_LOG="$COMMAND_LOG" \
    INSTALL_ROOT="$INSTALL_ROOT" \
    SYSTEMD_ROOT="$SYSTEMD_ROOT" \
    LOG_ROOT="$LOG_ROOT" \
    "$INSTALLER" baseline >/dev/null; then
    fail "installer failed for baseline mode"
fi

SERVICE_FILE="$SYSTEMD_ROOT/umoweb-openclaw-preflight.service"
TIMER_FILE="$SYSTEMD_ROOT/umoweb-openclaw-preflight.timer"

assert_file_contains "$SERVICE_FILE" "Type=oneshot"
assert_file_contains "$SERVICE_FILE" "User=root"
assert_file_contains "$SERVICE_FILE" \
    "ExecStart=$INSTALL_ROOT/scripts/openclaw/preflight/memory-sample.sh $LOG_ROOT/baseline.csv"
assert_file_contains "$SERVICE_FILE" "Nice=10"
assert_file_contains "$SERVICE_FILE" "IOSchedulingClass=idle"

assert_file_contains "$TIMER_FILE" "OnBootSec=1min"
assert_file_contains "$TIMER_FILE" "OnUnitActiveSec=5min"
assert_file_contains "$TIMER_FILE" "AccuracySec=15s"
assert_file_contains "$TIMER_FILE" "Persistent=true"
assert_file_contains "$TIMER_FILE" "WantedBy=timers.target"

assert_file_contains "$COMMAND_LOG" "systemctl <daemon-reload>"
assert_file_contains "$COMMAND_LOG" \
    "systemctl <enable> <--now> <umoweb-openclaw-preflight.timer>"
assert_file_contains "$COMMAND_LOG" \
    "systemctl <start> <umoweb-openclaw-preflight.service>"

printf 'preserve-me\n' > "$LOG_ROOT/baseline.csv"

if ! PATH="$FAKE_BIN:$PATH" \
    FAKE_COMMAND_LOG="$COMMAND_LOG" \
    INSTALL_ROOT="$INSTALL_ROOT" \
    SYSTEMD_ROOT="$SYSTEMD_ROOT" \
    LOG_ROOT="$LOG_ROOT" \
    "$INSTALLER" baseline >/dev/null; then
    fail "installer failed when the baseline CSV already existed"
fi

[[ "$(cat "$LOG_ROOT/baseline.csv")" == "preserve-me" ]] ||
    fail "installer overwrote an existing baseline CSV"

if [[ -e "$SYSTEMD_ROOT/openclaw.service" ]]; then
    fail "installer created openclaw.service"
fi

if ! PATH="$FAKE_BIN:$PATH" \
    FAKE_COMMAND_LOG="$COMMAND_LOG" \
    INSTALL_ROOT="$INSTALL_ROOT" \
    SYSTEMD_ROOT="$SYSTEMD_ROOT" \
    LOG_ROOT="$LOG_ROOT" \
    "$INSTALLER" gate0 >/dev/null; then
    fail "installer failed for gate0 mode"
fi

assert_file_contains "$SERVICE_FILE" \
    "ExecStart=$INSTALL_ROOT/scripts/openclaw/preflight/memory-sample.sh $LOG_ROOT/gate0.csv"

if PATH="$FAKE_BIN:$PATH" \
    FAKE_COMMAND_LOG="$COMMAND_LOG" \
    FAKE_OPENCLAW_PRESENT=1 \
    INSTALL_ROOT="$INSTALL_ROOT" \
    SYSTEMD_ROOT="$SYSTEMD_ROOT" \
    LOG_ROOT="$LOG_ROOT" \
    "$INSTALLER" baseline >/dev/null 2>&1; then
    fail "installer ran while openclaw.service already existed"
fi

printf 'PASS: memory preflight timer installer contract is satisfied\n'
