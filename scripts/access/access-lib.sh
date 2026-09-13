#!/usr/bin/env bash

ACCESS_CONFIG_FILE="${ACCESS_CONFIG_FILE:-/etc/umoweb/access.env}"

access_die() {
    printf '[access] ERROR: %s\n' "$*" >&2
    exit 1
}

access_warn() {
    printf '[access] WARN: %s\n' "$*" >&2
}

access_require_command() {
    command -v "$1" >/dev/null 2>&1 || access_die "required command not found: $1"
}

access_require_file() {
    [[ -f "$1" ]] || access_die "required file not found: $1"
}

access_load_config() {
    # The file is root-owned and contains only access-policy settings.
    if [[ -r "$ACCESS_CONFIG_FILE" ]]; then
        # shellcheck disable=SC1090
        set -a
        source "$ACCESS_CONFIG_FILE"
        set +a
    elif [[ "${ACCESS_REQUIRE_CONFIG:-0}" == "1" ]]; then
        access_die "required readable config file not found: $ACCESS_CONFIG_FILE"
    fi

    : "${ACCESS_LOG_DIR:=/opt/umoweb/access/logs}"
    : "${ACCESS_AGGREGATE_DIR:=/opt/umoweb/access/aggregates}"
    : "${ACCESS_REPORT_DIR:=/opt/umoweb/access/reports}"
    : "${ACCESS_RAW_RETENTION_DAYS:=30}"
    : "${ACCESS_AGGREGATE_RETENTION_DAYS:=180}"
    : "${ACCESS_REPORT_HOST:=127.0.0.1}"
    : "${ACCESS_REPORT_PORT:=7890}"
    : "${ACCESS_TRUSTED_PROXIES:=}"
    : "${COMPOSE_FILE:=/opt/umoweb/compose.yaml}"
    : "${COMPOSE_ENV_FILE:=/opt/umoweb/.env.docker}"
    : "${COMPOSE_PROJECT:=umoweb}"
}

access_prepare_directories() {
    install -d -m 0750 "$ACCESS_LOG_DIR" "$ACCESS_AGGREGATE_DIR" "$ACCESS_REPORT_DIR"
    chmod 0750 "$ACCESS_LOG_DIR" "$ACCESS_AGGREGATE_DIR" "$ACCESS_REPORT_DIR"
}

access_secure_files() {
    local directory
    for directory in "$ACCESS_LOG_DIR" "$ACCESS_AGGREGATE_DIR" "$ACCESS_REPORT_DIR"; do
        [[ -d "$directory" ]] || continue
        find "$directory" -type f -exec chmod 0640 {} +
    done
}

access_compose() {
    docker compose \
        -p "$COMPOSE_PROJECT" \
        --env-file "$COMPOSE_ENV_FILE" \
        -f "$COMPOSE_FILE" \
        "$@"
}
