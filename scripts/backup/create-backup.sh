#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

require_command docker
require_command tar
require_command awk
require_command df
require_command mkdir
require_command wc
require_file "$COMPOSE_FILE"
require_file "$COMPOSE_ENV_FILE"
assert_project_name "$COMPOSE_PROJECT"

compose_source() {
    docker compose -p "$COMPOSE_PROJECT" --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

require_image() {
    local image="$1"
    docker image inspect "$image" >/dev/null 2>&1 ||
        die "required local image not found: $image"
}

require_running_service() {
    local service="$1"
    compose_source ps --status running --services | grep -qx "$service" ||
        die "service is not running: $service"
}

volume_name() {
    printf '%s_%s\n' "$COMPOSE_PROJECT" "$1"
}

require_image "$BACKUP_HELPER_IMAGE"
require_image "$BACKEND_IMAGE"
require_image "$FRONTEND_IMAGE"
require_running_service mysql
require_running_service backend
require_running_service frontend

mkdir -p "$BACKUP_ROOT"
chmod 0700 "$BACKUP_ROOT"
acquire_lock "$BACKUP_ROOT/.lock"

stamp="$(utc_stamp)"
if [[ -n "$GIT_COMMIT" ]]; then
    git_commit="$GIT_COMMIT"
else
    git_commit="$(git -C "$REPO_ROOT" rev-parse --short=12 HEAD 2>/dev/null || echo unknown)"
fi
helper_image_id="$(docker image inspect --format '{{.Id}}' "$BACKUP_HELPER_IMAGE")"
backend_image_id="$(docker image inspect --format '{{.Id}}' "$BACKEND_IMAGE")"
frontend_image_id="$(docker image inspect --format '{{.Id}}' "$FRONTEND_IMAGE")"
backup_id="umoweb-backup-$stamp-$git_commit"
staging_dir="$BACKUP_ROOT/.$backup_id.partial"
archive="$BACKUP_ROOT/$backup_id.tar.gz"
services_stopped=0
backup_complete=0
restart_failed=0
start_epoch="$(date +%s)"

cleanup() {
    local status=$?
    if ((backup_complete == 0)); then
        rm -rf "$staging_dir"
    fi
    if ((services_stopped == 1)); then
        if ! compose_source up -d --no-build --wait backend frontend; then
            warn "failed to restart backend/frontend after backup"
            restart_failed=1
        fi
    fi
    release_lock "$BACKUP_ROOT/.lock"
    if ((status != 0 || restart_failed != 0)); then
        return "$status"
    fi
    return 0
}
trap cleanup EXIT

app_data_bytes="$(
    docker run --rm --entrypoint sh \
        -v "$(volume_name app_data):/source:ro" \
        "$BACKUP_HELPER_IMAGE" \
        -c 'du -sk /source' | awk '{print $1 * 1024}'
)"
[[ "$app_data_bytes" =~ ^[0-9]+$ ]] || die "failed to determine app_data size"

free_bytes="$(df -Pk "$BACKUP_ROOT" | awk 'NR == 2 {print $4 * 1024}')"
[[ "$free_bytes" =~ ^[0-9]+$ ]] || die "failed to determine free disk space"
required_bytes=$((app_data_bytes * 2 + 536870912))
if ((free_bytes < required_bytes)); then
    die "insufficient free disk space: need at least $required_bytes bytes, have $free_bytes"
fi

rm -rf "$staging_dir"
mkdir -p "$staging_dir"
chmod 0700 "$staging_dir"

log "stopping frontend and backend for a consistent backup"
compose_source stop frontend backend
services_stopped=1

log "dumping MySQL database"
compose_source exec -T mysql sh -c '
    exec mysqldump \
        -uroot \
        -p"$MYSQL_ROOT_PASSWORD" \
        --single-transaction \
        --quick \
        --routines \
        --triggers \
        --events \
        --hex-blob \
        --default-character-set=utf8mb4 \
        --databases "$MYSQL_DATABASE"
' > "$staging_dir/database.sql"

mysql_version="$(
    compose_source exec -T mysql sh -c '
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "SELECT VERSION()"
    ' | tr -d '\r'
)"

compose_source exec -T mysql sh -c '
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -N -B -e "
        SELECT '\''users'\'', COUNT(*) FROM users
        UNION ALL SELECT '\''categories'\'', COUNT(*) FROM categories
        UNION ALL SELECT '\''tags'\'', COUNT(*) FROM tags
        UNION ALL SELECT '\''contents'\'', COUNT(*) FROM contents
        UNION ALL SELECT '\''content_category'\'', COUNT(*) FROM content_category
        UNION ALL SELECT '\''content_tag'\'', COUNT(*) FROM content_tag
        UNION ALL SELECT '\''images'\'', COUNT(*) FROM images
        UNION ALL SELECT '\''site_options'\'', COUNT(*) FROM site_options
        UNION ALL SELECT '\''contents_published'\'', COUNT(*) FROM contents WHERE status = '\''PUBLISHED'\''
        UNION ALL SELECT '\''contents_draft'\'', COUNT(*) FROM contents WHERE status = '\''DRAFT'\'';
    "
' > "$staging_dir/table-counts.tsv"

queue_table_exists="$(
    compose_source exec -T mysql sh -c '
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -N -B -e "
            SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = '\''image_cleanup_queue'\'';
        "
    ' | tr -d '\r'
)"
if [[ "$queue_table_exists" == "1" ]]; then
    compose_source exec -T mysql sh -c '
        mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -N -B -e "
            SELECT '\''image_cleanup_queue'\'', COUNT(*) FROM image_cleanup_queue;
        "
    ' >> "$staging_dir/table-counts.tsv"
elif [[ "$queue_table_exists" != "0" ]]; then
    die "failed to determine image_cleanup_queue table state: $queue_table_exists"
fi

log "archiving app_data and creating the file manifest"
staging_mount="$(docker_host_path "$staging_dir")"
MSYS_NO_PATHCONV=1 docker run --rm --entrypoint bash \
    -v "$(volume_name app_data):/source:ro" \
    -v "$staging_mount:/backup" \
    "$BACKUP_HELPER_IMAGE" \
    -c '
        set -Eeuo pipefail
        tar -C /source -czf /backup/app-data.tar.gz .
        cd /source
        find . -type f -print0 |
            sort -z |
            while IFS= read -r -d "" file; do
                relative="${file#./}"
                size="$(wc -c < "$file" | tr -d "[:space:]")"
                hash="$(sha256sum "$file" | cut -d " " -f 1)"
                printf "%s\t%s\t%s\n" "$relative" "$size" "$hash"
            done > /backup/app-files.tsv
    '

database_sha="$(sha256_file "$staging_dir/database.sql")"
app_data_sha="$(sha256_file "$staging_dir/app-data.tar.gz")"
app_files_sha="$(sha256_file "$staging_dir/app-files.tsv")"
app_file_count="$(wc -l < "$staging_dir/app-files.tsv" | tr -d '[:space:]')"
markdown_file_count="$(
    awk -F '\t' '$1 ~ /\.md$/ {count++} END {print count + 0}' "$staging_dir/app-files.tsv"
)"
image_file_count="$(
    awk -F '\t' '$1 ~ /^images\// {count++} END {print count + 0}' "$staging_dir/app-files.tsv"
)"
backup_seconds=$(($(date +%s) - start_epoch))

{
    printf 'BACKUP_ID=%s\n' "$backup_id"
    printf 'CREATED_AT_UTC=%s\n' "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    printf 'GIT_COMMIT=%s\n' "$git_commit"
    printf 'COMPOSE_PROJECT=%s\n' "$COMPOSE_PROJECT"
    printf 'MYSQL_VERSION=%s\n' "$mysql_version"
    printf 'MYSQL_DATABASE=%s\n' "$(compose_source exec -T mysql sh -c 'printf %s "$MYSQL_DATABASE"' | tr -d '\r')"
    printf 'BACKUP_HELPER_IMAGE=%s\n' "$BACKUP_HELPER_IMAGE"
    printf 'BACKEND_IMAGE=%s\n' "$BACKEND_IMAGE"
    printf 'FRONTEND_IMAGE=%s\n' "$FRONTEND_IMAGE"
    printf 'BACKUP_HELPER_IMAGE_ID=%s\n' "$helper_image_id"
    printf 'BACKEND_IMAGE_ID=%s\n' "$backend_image_id"
    printf 'FRONTEND_IMAGE_ID=%s\n' "$frontend_image_id"
    printf 'DATABASE_SHA256=%s\n' "$database_sha"
    printf 'APP_DATA_SHA256=%s\n' "$app_data_sha"
    printf 'APP_FILES_SHA256=%s\n' "$app_files_sha"
    printf 'APP_FILE_COUNT=%s\n' "$app_file_count"
    printf 'MARKDOWN_FILE_COUNT=%s\n' "$markdown_file_count"
    printf 'IMAGE_FILE_COUNT=%s\n' "$image_file_count"
    printf 'BACKUP_DURATION_SECONDS=%s\n' "$backup_seconds"
    while IFS=$'\t' read -r table count; do
        [[ -n "$table" ]] || continue
        printf 'TABLE_COUNT_%s=%s\n' "$table" "$count"
    done < "$staging_dir/table-counts.tsv"
} > "$staging_dir/metadata.env"

(
    cd "$staging_dir"
    sha256sum database.sql app-data.tar.gz app-files.tsv metadata.env > SHA256SUMS
)

tar -C "$staging_dir" -czf "$archive.tmp" .
mv "$archive.tmp" "$archive"
printf '%s  %s\n' "$(sha256_file "$archive")" "$(basename "$archive")" > "$archive.sha256"
chmod 0600 "$archive" "$archive.sha256"
backup_complete=1
rm -rf "$staging_dir"

log "restarting frontend and backend"
if compose_source up -d --no-build --wait backend frontend; then
    services_stopped=0
else
    restart_failed=1
    warn "backup completed but services failed to restart"
fi

if ((restart_failed != 0)); then
    exit 1
fi

prune_backups

printf 'Backup complete: %s\n' "$archive"
printf 'Size: %s bytes\n' "$(file_size "$archive")"
printf 'Duration: %s seconds\n' "$backup_seconds"
