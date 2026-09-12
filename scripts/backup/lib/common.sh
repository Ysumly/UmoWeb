#!/usr/bin/env bash

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-$REPO_ROOT/compose.yaml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-$REPO_ROOT/.env.docker}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-umoweb}"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/umoweb/backups}"
BACKUP_RETENTION_COUNT="${BACKUP_RETENTION_COUNT:-6}"
BACKUP_MAX_BYTES="${BACKUP_MAX_BYTES:-8589934592}"
BACKUP_HELPER_IMAGE="${BACKUP_HELPER_IMAGE:-mysql:8.4}"
BACKEND_IMAGE="${BACKEND_IMAGE:-umoweb-backend:latest}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-umoweb-frontend:latest}"
GIT_COMMIT="${GIT_COMMIT:-}"
RESTORE_PORT="${RESTORE_PORT:-18080}"
RESTORE_SUBNET="${RESTORE_SUBNET:-172.31.0.0/24}"
RESTORE_FRONTEND_IP="${RESTORE_FRONTEND_IP:-172.31.0.10}"

log() {
    printf '[backup] %s\n' "$*"
}

warn() {
    printf '[backup] WARN: %s\n' "$*" >&2
}

die() {
    printf '[backup] ERROR: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_file() {
    [[ -f "$1" ]] || die "required file not found: $1"
}

require_directory() {
    [[ -d "$1" ]] || die "required directory not found: $1"
}

assert_project_name() {
    local value="$1"
    [[ "$value" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] ||
        die "invalid project name: $value"
}

assert_restore_project_name() {
    local value="$1"
    [[ "$value" =~ ^umoweb-restore-[a-z0-9][a-z0-9_.-]*$ ]] ||
        return 1
    [[ "$value" != "umoweb" ]] || return 1
    return 0
}

sha256_file() {
    local target="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$target" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$target" | awk '{print $1}'
    else
        die "sha256sum or shasum is required"
    fi
}

file_size() {
    wc -c < "$1" | tr -d '[:space:]'
}

verify_sha256_file() {
    local target="$1"
    local checksum_file="$2"
    if [[ ! -f "$target" || ! -f "$checksum_file" ]]; then
        warn "cannot verify missing file or checksum: $target"
        return 1
    fi

    local expected
    expected="$(awk 'NR == 1 {print $1}' "$checksum_file")"
    if [[ ! "$expected" =~ ^[A-Fa-f0-9]{64}$ ]]; then
        warn "invalid checksum file: $checksum_file"
        return 1
    fi

    local actual
    actual="$(sha256_file "$target")"
    if [[ "$actual" != "$expected" ]]; then
        warn "checksum mismatch for $target"
        return 1
    fi

    return 0
}

utc_stamp() {
    date -u +"%Y%m%dT%H%M%SZ"
}

random_hex() {
    local bytes="${1:-24}"
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex "$bytes"
    else
        od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
    fi
}

docker_host_path() {
    local path="$1"
    if command -v cygpath >/dev/null 2>&1; then
        cygpath -w "$path"
    else
        printf '%s\n' "$path"
    fi
}

acquire_lock() {
    local lock_dir="$1"
    if ! mkdir "$lock_dir" 2>/dev/null; then
        die "another backup or restore operation holds $lock_dir"
    fi
    printf '%s\n' "$$" > "$lock_dir/pid"
}

release_lock() {
    local lock_dir="$1"
    rm -f "$lock_dir/pid"
    rmdir "$lock_dir" 2>/dev/null || true
}

prune_backups() {
    require_directory "$BACKUP_ROOT"
    [[ "$BACKUP_RETENTION_COUNT" =~ ^[1-9][0-9]*$ ]] ||
        die "BACKUP_RETENTION_COUNT must be a positive integer"
    [[ "$BACKUP_MAX_BYTES" =~ ^[1-9][0-9]*$ ]] ||
        die "BACKUP_MAX_BYTES must be a positive integer"

    local archives=()
    local archive
    while IFS= read -r archive; do
        archives+=("$archive")
    done < <(find "$BACKUP_ROOT" -maxdepth 1 -type f -name 'umoweb-backup-*.tar.gz' | sort)

    local total_bytes=0
    for archive in "${archives[@]}"; do
        total_bytes=$((total_bytes + $(file_size "$archive")))
    done

    while ((${#archives[@]} > BACKUP_RETENTION_COUNT || total_bytes > BACKUP_MAX_BYTES)) &&
        ((${#archives[@]} > 1)); do
        archive="${archives[0]}"
        local removed_bytes
        removed_bytes="$(file_size "$archive")"
        rm -f "$archive" "$archive.sha256"
        archives=("${archives[@]:1}")
        total_bytes=$((total_bytes - removed_bytes))
        log "pruned old backup: $(basename "$archive")"
    done

    if ((${#archives[@]} > 0 && total_bytes > BACKUP_MAX_BYTES)); then
        warn "latest backup exceeds BACKUP_MAX_BYTES; it is retained for recovery safety"
    fi
}
