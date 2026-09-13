#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_ROOT="${INSTALL_ROOT:-/opt/umoweb}"
SYSTEMD_ROOT="${SYSTEMD_ROOT:-/etc/systemd/system}"
ACCESS_CONFIG_PATH="${ACCESS_CONFIG_PATH:-/etc/umoweb/access.env}"
REPORT_USER="${REPORT_USER:-umoweb-report}"
REPORT_GROUP="${REPORT_GROUP:-umoweb-report}"
NGINX_GROUP_ID="${NGINX_GROUP_ID:-101}"
SKIP_SYSTEMD="${SKIP_SYSTEMD:-0}"
DIRECTORY_OWNER="root"

access_die() {
    printf '[access-install] ERROR: %s\n' "$*" >&2
    exit 1
}

access_require_command() {
    command -v "$1" >/dev/null 2>&1 || access_die "required command not found: $1"
}

access_require_file() {
    [[ -f "$1" ]] || access_die "required file not found: $1"
}

access_make_directory() {
    local mode="$1"
    shift
    if [[ "$(uname -s)" == "Linux" ]]; then
        install -d -m "$mode" "$@"
    else
        mkdir -p "$@"
        chmod "$mode" "$@"
    fi
}

access_install_script() {
    local source="$1"
    local destination="$2"
    if [[ "$source" != "$destination" ]]; then
        install -m 0755 "$source" "$destination"
    else
        chmod 0755 "$destination"
    fi
}

access_prepare_active_log() {
    local log_file="$INSTALL_ROOT/access/logs/access.log"
    if [[ -e "$log_file" ]]; then
        if [[ "$SKIP_SYSTEMD" != "1" ]]; then
            chown "$NGINX_GROUP_ID":0 "$log_file"
        fi
        chmod 0640 "$log_file"
    fi
}

if [[ "$SKIP_SYSTEMD" != "1" && "$(id -u)" != "0" ]]; then
    access_die "install-access-timer.sh must run as root"
fi
if [[ "$SKIP_SYSTEMD" == "1" ]]; then
    DIRECTORY_OWNER="$(id -un)"
fi

access_require_command install
access_require_command python3
access_require_command sed
access_require_file "$SCRIPT_DIR/access_maintenance.py"
access_require_file "$SCRIPT_DIR/report_server.py"
access_require_file "$SCRIPT_DIR/systemd/umoweb-access-maintenance.service"
access_require_file "$SCRIPT_DIR/systemd/umoweb-access-maintenance.timer"
access_require_file "$SCRIPT_DIR/systemd/umoweb-access-report.service"

if [[ "$SKIP_SYSTEMD" != "1" ]]; then
    access_require_command systemctl
    access_require_command useradd
    if ! getent group "$REPORT_GROUP" >/dev/null; then
        groupadd --system "$REPORT_GROUP"
    fi
    if ! id -u "$REPORT_USER" >/dev/null 2>&1; then
        useradd \
            --system \
            --gid "$REPORT_GROUP" \
            --home-dir /nonexistent \
            --shell /usr/sbin/nologin \
            "$REPORT_USER"
    fi
fi

access_make_directory 0755 "$INSTALL_ROOT/scripts/access"
access_make_directory 0700 "$(dirname "$ACCESS_CONFIG_PATH")"
if [[ "$SKIP_SYSTEMD" != "1" ]]; then
    chown root:"$REPORT_GROUP" "$INSTALL_ROOT"
    chmod 0750 "$INSTALL_ROOT"
fi
chmod 0755 "$INSTALL_ROOT/scripts" "$INSTALL_ROOT/scripts/access"
if [[ "$SKIP_SYSTEMD" == "1" ]]; then
    access_make_directory 0750 \
        "$INSTALL_ROOT/access" \
        "$INSTALL_ROOT/access/aggregates" \
        "$INSTALL_ROOT/access/reports"
    access_make_directory 0750 "$INSTALL_ROOT/access/logs"
else
    install -d -o "$DIRECTORY_OWNER" -g "$REPORT_GROUP" -m 0750 \
        "$INSTALL_ROOT/access" \
        "$INSTALL_ROOT/access/aggregates" \
        "$INSTALL_ROOT/access/reports"
    install -d -o "$DIRECTORY_OWNER" -g "$NGINX_GROUP_ID" -m 0750 \
        "$INSTALL_ROOT/access/logs"
fi
access_prepare_active_log

if [[ "$SKIP_SYSTEMD" != "1" ]] &&
    command -v docker >/dev/null 2>&1 &&
    [[ -f "$INSTALL_ROOT/compose.yaml" && -f "$INSTALL_ROOT/.env.docker" ]]; then
    docker compose \
        -p umoweb \
        --env-file "$INSTALL_ROOT/.env.docker" \
        -f "$INSTALL_ROOT/compose.yaml" \
        exec -T frontend nginx -s reopen >/dev/null 2>&1 || true
fi

if [[ ! -f "$ACCESS_CONFIG_PATH" ]]; then
    cat > "$ACCESS_CONFIG_PATH" <<EOF
ACCESS_LOG_DIR=$INSTALL_ROOT/access/logs
ACCESS_AGGREGATE_DIR=$INSTALL_ROOT/access/aggregates
ACCESS_REPORT_DIR=$INSTALL_ROOT/access/reports
ACCESS_RAW_RETENTION_DAYS=30
ACCESS_AGGREGATE_RETENTION_DAYS=180
ACCESS_REPORT_HOST=127.0.0.1
ACCESS_REPORT_PORT=7890
ACCESS_TRUSTED_PROXIES=
COMPOSE_FILE=$INSTALL_ROOT/compose.yaml
COMPOSE_ENV_FILE=$INSTALL_ROOT/.env.docker
COMPOSE_PROJECT=umoweb
EOF
    chmod 0600 "$ACCESS_CONFIG_PATH"
fi

python3 "$SCRIPT_DIR/access_maintenance.py" render-config \
    --config "$ACCESS_CONFIG_PATH" \
    --privacy-output "$INSTALL_ROOT/access/privacy-config.json" \
    --trusted-output "$INSTALL_ROOT/access/trusted-proxies.conf"
chmod 0644 "$INSTALL_ROOT/access/privacy-config.json" "$INSTALL_ROOT/access/trusted-proxies.conf"

access_install_script "$SCRIPT_DIR/access-lib.sh" "$INSTALL_ROOT/scripts/access/access-lib.sh"
access_install_script "$SCRIPT_DIR/access_maintenance.py" "$INSTALL_ROOT/scripts/access/access_maintenance.py"
access_install_script "$SCRIPT_DIR/report_server.py" "$INSTALL_ROOT/scripts/access/report_server.py"
access_install_script "$SCRIPT_DIR/run-maintenance.sh" "$INSTALL_ROOT/scripts/access/run-maintenance.sh"
access_install_script "$SCRIPT_DIR/serve-access-report.sh" "$INSTALL_ROOT/scripts/access/serve-access-report.sh"

access_make_directory 0755 "$SYSTEMD_ROOT"
sed \
    -e "s|@INSTALL_ROOT@|$INSTALL_ROOT|g" \
    -e "s|@REPORT_USER@|$REPORT_USER|g" \
    -e "s|@REPORT_GROUP@|$REPORT_GROUP|g" \
    "$SCRIPT_DIR/systemd/umoweb-access-maintenance.service" \
    > "$SYSTEMD_ROOT/umoweb-access-maintenance.service"
install -m 0644 \
    "$SCRIPT_DIR/systemd/umoweb-access-maintenance.timer" \
    "$SYSTEMD_ROOT/umoweb-access-maintenance.timer"
sed \
    -e "s|@INSTALL_ROOT@|$INSTALL_ROOT|g" \
    -e "s|@REPORT_USER@|$REPORT_USER|g" \
    -e "s|@REPORT_GROUP@|$REPORT_GROUP|g" \
    "$SCRIPT_DIR/systemd/umoweb-access-report.service" \
    > "$SYSTEMD_ROOT/umoweb-access-report.service"

if [[ "$SKIP_SYSTEMD" == "1" ]]; then
    printf 'Rendered UmoWeb access configuration under %s (systemd skipped).\n' "$INSTALL_ROOT"
    exit 0
fi

systemctl daemon-reload
systemctl enable --now umoweb-access-maintenance.timer
systemctl enable umoweb-access-report.service
systemctl restart umoweb-access-report.service
systemctl start umoweb-access-maintenance.service
systemctl list-timers umoweb-access-maintenance.timer --no-pager

printf 'Installed UmoWeb access maintenance and report services.\n'
printf 'Report: http://%s:%s/ via an administrator SSH tunnel only.\n' \
    "${ACCESS_REPORT_HOST:-127.0.0.1}" \
    "${ACCESS_REPORT_PORT:-7890}"
