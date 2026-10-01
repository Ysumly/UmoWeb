#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_ROOT="${INSTALL_ROOT:-/opt/umoweb}"
SYSTEMD_ROOT="${SYSTEMD_ROOT:-/etc/systemd/system}"
LOG_ROOT="${LOG_ROOT:-/var/log/umoweb/openclaw-preflight}"
MODE="${1:-}"

die() {
    printf '[openclaw-preflight] ERROR: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_file() {
    [[ -f "$1" ]] || die "required file not found: $1"
}

make_directory() {
    local mode="$1"
    local path="$2"

    if [[ "$(uname -s)" == "Linux" ]]; then
        install -d -m "$mode" "$path"
    else
        mkdir -p "$path"
        chmod "$mode" "$path" 2>/dev/null || true
    fi
}

case "$MODE" in
    baseline|gate0)
        ;;
    *)
        die "usage: $0 baseline|gate0"
        ;;
esac

OUTPUT_FILE="$LOG_ROOT/$MODE.csv"
SAMPLER="$INSTALL_ROOT/scripts/openclaw/preflight/memory-sample.sh"
SERVICE_TEMPLATE="$SCRIPT_DIR/systemd/umoweb-openclaw-preflight.service"
TIMER_TEMPLATE="$SCRIPT_DIR/systemd/umoweb-openclaw-preflight.timer"

[[ "$(id -u)" == "0" ]] || die "install-memory-preflight-timer.sh must run as root"

require_command install
require_command sed
require_command systemctl
require_file "$SAMPLER"
require_file "$SERVICE_TEMPLATE"
require_file "$TIMER_TEMPLATE"

if systemctl cat openclaw.service >/dev/null 2>&1; then
    die "openclaw.service already exists; Gate 0 requires it to remain uninstalled"
fi

make_directory 0700 "$LOG_ROOT"
make_directory 0755 "$SYSTEMD_ROOT"
chmod 0755 "$SAMPLER"

sed \
    -e "s|@INSTALL_ROOT@|$INSTALL_ROOT|g" \
    -e "s|@OUTPUT_FILE@|$OUTPUT_FILE|g" \
    "$SERVICE_TEMPLATE" \
    > "$SYSTEMD_ROOT/umoweb-openclaw-preflight.service"
install -m 0644 \
    "$TIMER_TEMPLATE" \
    "$SYSTEMD_ROOT/umoweb-openclaw-preflight.timer"

systemctl daemon-reload
systemctl enable --now umoweb-openclaw-preflight.timer
systemctl start umoweb-openclaw-preflight.service
systemctl list-timers umoweb-openclaw-preflight.timer --no-pager || true

printf 'Installed OpenClaw memory preflight timer (%s).\n' "$MODE"
printf 'Sample file: %s\n' "$OUTPUT_FILE"
