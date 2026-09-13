#!/usr/bin/env bash
set -Eeuo pipefail

export PATH="/usr/bin:/bin:$PATH"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ ! -f "$BACKUP_DIR/lib/common.sh" ]]; then
    echo "FAIL: scripts/backup/lib/common.sh is missing" >&2
    exit 1
fi

# shellcheck source=../lib/common.sh
source "$BACKUP_DIR/lib/common.sh"

TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT
FAILURES=0

fail() {
    echo "FAIL: $*" >&2
    FAILURES=$((FAILURES + 1))
}

assert_eq() {
    local expected="$1"
    local actual="$2"
    local message="$3"
    if [[ "$actual" != "$expected" ]]; then
        fail "$message: expected '$expected', got '$actual'"
    fi
}

assert_success() {
    local message="$1"
    shift
    if ! "$@"; then
        fail "$message"
    fi
}

assert_failure() {
    local message="$1"
    shift
    if "$@"; then
        fail "$message"
    fi
}

test_retention_removes_oldest_and_keeps_latest() {
    local root="$TEMP_ROOT/retention-count"
    mkdir -p "$root"
    BACKUP_ROOT="$root"
    BACKUP_RETENTION_COUNT=3
    BACKUP_MAX_BYTES=999999

    for stamp in 20260901T000000Z 20260902T000000Z 20260903T000000Z 20260904T000000Z; do
        printf 'backup-%s' "$stamp" > "$root/umoweb-backup-$stamp.tar.gz"
        printf 'checksum-%s' "$stamp" > "$root/umoweb-backup-$stamp.tar.gz.sha256"
    done

    prune_backups

    [[ ! -e "$root/umoweb-backup-20260901T000000Z.tar.gz" ]] ||
        fail "oldest backup should be pruned"
    [[ -e "$root/umoweb-backup-20260904T000000Z.tar.gz" ]] ||
        fail "latest backup must always be retained"
    assert_eq "3" "$(find "$root" -name 'umoweb-backup-*.tar.gz' -type f | wc -l | tr -d ' ')" \
        "retention count"
}

test_retention_removes_by_total_size() {
    local root="$TEMP_ROOT/retention-size"
    mkdir -p "$root"
    BACKUP_ROOT="$root"
    BACKUP_RETENTION_COUNT=6
    BACKUP_MAX_BYTES=250

    for stamp in 20260901T000000Z 20260902T000000Z 20260903T000000Z; do
        dd if=/dev/zero of="$root/umoweb-backup-$stamp.tar.gz" bs=100 count=1 status=none
        printf 'checksum-%s' "$stamp" > "$root/umoweb-backup-$stamp.tar.gz.sha256"
    done

    prune_backups

    [[ ! -e "$root/umoweb-backup-20260901T000000Z.tar.gz" ]] ||
        fail "oldest backup should be pruned when the size cap is exceeded"
    [[ -e "$root/umoweb-backup-20260903T000000Z.tar.gz" ]] ||
        fail "latest backup must survive size-based pruning"
}

test_restore_project_guard() {
    assert_failure "production project name must be rejected" assert_restore_project_name "umoweb"
    assert_failure "unprefixed project name must be rejected" assert_restore_project_name "other"
    assert_failure "uppercase restore project name must be rejected" \
        assert_restore_project_name "umoweb-restore-20260912T145000Z-123"
    assert_success "restore project name should be accepted" assert_restore_project_name "umoweb-restore-test"
    assert_success "lowercase timestamp restore project name should be accepted" \
        assert_restore_project_name "umoweb-restore-20260912t145000z-123"
}

test_checksum_detection() {
    local target="$TEMP_ROOT/checksum.txt"
    printf 'original' > "$target"
    local checksum
    checksum="$(sha256_file "$target")"
    printf '%s  %s\n' "$checksum" "$(basename "$target")" > "$TEMP_ROOT/checksum.sha256"

    assert_success "matching checksum should pass" verify_sha256_file "$target" "$TEMP_ROOT/checksum.sha256"
    printf 'modified' > "$target"
    assert_failure "modified file must fail verification" verify_sha256_file "$target" "$TEMP_ROOT/checksum.sha256"
}

test_verify_and_export_cli() {
    local root="$TEMP_ROOT/archive"
    local contents="$root/contents"
    local archive="$root/umoweb-backup-20260912T000000Z.tar.gz"
    local destination="$root/exported"
    mkdir -p "$contents"
    printf 'select 1;\n' > "$contents/database.sql"
    printf 'payload' > "$contents/app-data.tar.gz"
    printf 'app-files.tsv' > "$contents/app-files.tsv"
    printf 'BACKUP_ID=test\n' > "$contents/metadata.env"
    (
        cd "$contents"
        sha256sum database.sql app-data.tar.gz app-files.tsv metadata.env > SHA256SUMS
    )
    tar -C "$contents" -czf "$archive" .
    printf '%s  %s\n' "$(sha256_file "$archive")" "$(basename "$archive")" > "$archive.sha256"

    assert_success "valid backup archive should verify" \
        bash "$BACKUP_DIR/verify-backup.sh" "$archive"
    assert_success "valid backup archive should export" \
        bash "$BACKUP_DIR/export-backup.sh" "$archive" "$destination"
    assert_success "exported backup should retain a valid checksum" \
        bash "$BACKUP_DIR/verify-backup.sh" "$destination/$(basename "$archive")"

    printf 'corruption' >> "$archive"
    assert_failure "corrupted backup archive must fail verification" \
        bash "$BACKUP_DIR/verify-backup.sh" "$archive"
}

test_retention_removes_oldest_and_keeps_latest
test_retention_removes_by_total_size
test_restore_project_guard
test_checksum_detection
test_verify_and_export_cli

if ((FAILURES > 0)); then
    echo "Backup unit tests failed: $FAILURES" >&2
    exit 1
fi

echo "Backup unit tests passed"
