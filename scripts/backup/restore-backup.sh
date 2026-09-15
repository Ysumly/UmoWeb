#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
    echo "Usage: $0 <umoweb-backup-*.tar.gz> [restore-project]" >&2
}

archive="${1:-}"
requested_project="${2:-}"
[[ -n "$archive" ]] || {
    usage
    exit 2
}
archive="$(cd "$(dirname "$archive")" && pwd)/$(basename "$archive")"

require_command docker
require_command tar
require_command awk
require_file "$COMPOSE_FILE"
require_file "$archive"
verify_sha256_file "$archive" "$archive.sha256" ||
    die "backup checksum verification failed"

stamp="$(utc_stamp | tr '[:upper:]' '[:lower:]')"
project="${requested_project:-umoweb-restore-$stamp-$$}"
assert_restore_project_name "$project" ||
    die "restore project must match umoweb-restore-*: $project"

RESTORE_ROOT="${RESTORE_ROOT:-$BACKUP_ROOT/restores}"
work_dir="$RESTORE_ROOT/$project"
restore_env="$RESTORE_ROOT/$project.env"
restore_complete=0
stack_started=0

compose_restore() {
    docker compose -p "$project" --env-file "$restore_env" -f "$COMPOSE_FILE" "$@"
}

cleanup() {
    local status=$?
    if ((status != 0 && restore_complete == 0)); then
        if [[ "${RESTORE_KEEP_ON_FAILURE:-0}" == "1" ]]; then
            warn "restore failed; keeping $project for diagnosis"
        else
            if ((stack_started == 1)); then
                compose_restore down -v --remove-orphans >/dev/null 2>&1 || true
            fi
            rm -rf "$work_dir" "$restore_env"
        fi
    fi
    release_lock "$RESTORE_ROOT/.lock"
}
trap cleanup EXIT

mkdir -p "$RESTORE_ROOT"
chmod 0700 "$RESTORE_ROOT"
acquire_lock "$RESTORE_ROOT/.lock"

if docker volume inspect "${project}_mysql_data" >/dev/null 2>&1 ||
    docker volume inspect "${project}_app_data" >/dev/null 2>&1; then
    die "restore project already has volumes: $project"
fi

archive_bytes="$(file_size "$archive")"
free_bytes="$(df -Pk "$RESTORE_ROOT" | awk 'NR == 2 {print $4 * 1024}')"
required_bytes=$((archive_bytes * 3 + 536870912))
if ((free_bytes < required_bytes)); then
    die "insufficient free disk space for restore: need at least $required_bytes bytes, have $free_bytes"
fi

rm -rf "$work_dir"
mkdir -p "$work_dir"
chmod 0700 "$work_dir"
tar -xzf "$archive" -C "$work_dir"
(
    cd "$work_dir"
    sha256sum -c SHA256SUMS
)

metadata_value() {
    local key="$1"
    awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$work_dir/metadata.env"
}

database_name="$(metadata_value MYSQL_DATABASE)"
backup_id="$(metadata_value BACKUP_ID)"
[[ "$database_name" =~ ^[A-Za-z0-9_]+$ ]] ||
    die "invalid database name in backup metadata: $database_name"
[[ -n "$backup_id" ]] || die "backup metadata does not contain BACKUP_ID"

mysql_password="$(random_hex 24)"
mysql_root_password="$(random_hex 24)"
jwt_secret="$(random_hex 48)"
initial_admin_password="$(random_hex 18)"

{
    printf 'APP_PORT=127.0.0.1:%s\n' "$RESTORE_PORT"
    printf 'MYSQL_DATABASE=%s\n' "$database_name"
    printf 'MYSQL_USER=umo\n'
    printf 'MYSQL_PASSWORD=%s\n' "$mysql_password"
    printf 'MYSQL_ROOT_PASSWORD=%s\n' "$mysql_root_password"
    printf 'JWT_SECRET=%s\n' "$jwt_secret"
    printf 'INIT_ADMIN_USER=admin\n'
    printf 'INIT_ADMIN_PASS=%s\n' "$initial_admin_password"
    printf 'CORS_ALLOWED_ORIGINS=http://127.0.0.1:%s\n' "$RESTORE_PORT"
    printf 'FRONTEND_IP=%s\n' "$RESTORE_FRONTEND_IP"
    printf 'TRUSTED_PROXIES=%s/32\n' "$RESTORE_FRONTEND_IP"
    printf 'DOCKER_SUBNET=%s\n' "$RESTORE_SUBNET"
    printf 'VITE_ADMIN_PATH=/secret-admin\n'
    printf 'BACKEND_IMAGE=%s\n' "$BACKEND_IMAGE"
    printf 'FRONTEND_IMAGE=%s\n' "$FRONTEND_IMAGE"
} > "$restore_env"
chmod 0600 "$restore_env"

log "starting isolated MySQL project $project"
stack_started=1
compose_restore up -d --no-build --wait mysql

log "restoring MySQL database $database_name"
compose_restore exec -T mysql sh -c '
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
        DROP DATABASE IF EXISTS ${MYSQL_DATABASE};
        CREATE DATABASE ${MYSQL_DATABASE}
            DEFAULT CHARACTER SET utf8mb4
            DEFAULT COLLATE utf8mb4_unicode_ci;
    "
'
compose_restore exec -T mysql sh -c '
    exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"
' < "$work_dir/database.sql"

log "applying compatibility migrations"
migration_dir="$(cd "$SCRIPT_DIR/../../docs/design/migrations" && pwd)"
for migration in \
    "$migration_dir/20260911_integrity_security.sql" \
    "$migration_dir/20260913_image_cleanup_queue.sql"; do
    require_file "$migration"
    compose_restore exec -T mysql sh -c '
        exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"
    ' < "$migration"
done

log "restoring app_data into an isolated volume"
compose_restore create --no-build backend >/dev/null
work_mount="$(docker_host_path "$work_dir")"
MSYS_NO_PATHCONV=1 docker run --rm --entrypoint bash \
    -v "${project}_app_data:/target" \
    -v "$work_mount:/backup:ro" \
    "$BACKUP_HELPER_IMAGE" \
    -c '
        find /target -mindepth 1 -delete
        tar -xzf /backup/app-data.tar.gz -C /target
    '

log "starting isolated UmoWeb stack"
compose_restore up -d --no-build --wait

table_count() {
    local table="$1"
    compose_restore exec -T mysql sh -c "
        mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" \"\$MYSQL_DATABASE\" -N -B -e \"SELECT COUNT(*) FROM $table;\"
    " | tr -d '\r'
}

for table in users categories tags contents content_category content_tag images image_cleanup_queue site_options; do
    expected="$(metadata_value "TABLE_COUNT_$table")"
    actual="$(table_count "$table")"
    [[ "$expected" == "$actual" ]] ||
        die "table count mismatch for $table: expected $expected, got $actual"
done

log "rebuilding the app_data manifest for comparison"
MSYS_NO_PATHCONV=1 docker run --rm --entrypoint bash \
    -v "${project}_app_data:/source:ro" \
    -v "$work_mount:/restore" \
    "$BACKUP_HELPER_IMAGE" \
    -c '
        set -Eeuo pipefail
        cd /source
        find . -type f -print0 |
            sort -z |
            while IFS= read -r -d "" file; do
                relative="${file#./}"
                size="$(wc -c < "$file" | tr -d "[:space:]")"
                hash="$(sha256sum "$file" | cut -d " " -f 1)"
                printf "%s\t%s\t%s\n" "$relative" "$size" "$hash"
            done > /restore/restored-app-files.tsv
    '

diff -u "$work_dir/app-files.tsv" "$work_dir/restored-app-files.tsv" >/dev/null ||
    die "restored app_data manifest does not match the backup"

restore_complete=1
rm -rf "$work_dir"

cat <<EOF
Restore verified in isolated project: $project
Backup: $backup_id
URL: http://127.0.0.1:$RESTORE_PORT
Admin: /secret-admin/login
Restore env: $restore_env

From the development machine, create an SSH tunnel to this port and run:
  ssh -L $RESTORE_PORT:127.0.0.1:$RESTORE_PORT <ecs-host>
  .\\Server Side\\UmoWebBackend\\scripts\\api-smoke.ps1 ^
    -BaseUrl http://127.0.0.1:$RESTORE_PORT -Username <admin> -Password <current-password>

After 31/31 passes, remove only this restore environment:
  $SCRIPT_DIR/cleanup-restore.sh $project
EOF
