#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_SCRIPT_DIR="$SCRIPT_DIR/../backup"
# shellcheck source=../backup/lib/common.sh
source "$BACKUP_SCRIPT_DIR/lib/common.sh"

usage() {
    echo "Usage: $0 --confirm <umoweb-content-*.tar.gz>" >&2
}

confirmed=0
archive=""
for argument in "$@"; do
    case "$argument" in
        --confirm)
            confirmed=1
            ;;
        -*)
            usage
            exit 2
            ;;
        *)
            [[ -z "$archive" ]] || {
                usage
                exit 2
            }
            archive="$argument"
            ;;
    esac
done

[[ "$confirmed" == "1" && -n "$archive" ]] || {
    usage
    exit 2
}
[[ "$(id -u)" == "0" ]] || die "promote-content-backup.sh must run as root"
archive="$(cd "$(dirname "$archive")" && pwd)/$(basename "$archive")"
[[ "$(basename "$archive")" =~ ^umoweb-content-[A-Za-z0-9._-]+\.tar\.gz$ ]] ||
    die "refusing to promote an archive not named umoweb-content-*"

require_command docker
require_command tar
require_command awk
require_command python3
require_command curl
require_command jq
require_file "$COMPOSE_FILE"
require_file "$COMPOSE_ENV_FILE"
require_file "$archive"
[[ -f "$archive.sha256" ]] || die "archive checksum file is missing"
verify_sha256_file "$archive" "$archive.sha256" ||
    die "content backup checksum verification failed"

compose_source() {
    docker compose -p "$COMPOSE_PROJECT" --env-file "$COMPOSE_ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

env_value() {
    local key="$1"
    awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$COMPOSE_ENV_FILE"
}

set_env_value() {
    local file="$1"
    local key="$2"
    local value="$3"
    if grep -q "^${key}=" "$file"; then
        sed -i "s|^${key}=.*$|${key}=${value}|" "$file"
    else
        printf '%s=%s\n' "$key" "$value" >> "$file"
    fi
}

require_running_service() {
    local service="$1"
    compose_source ps --status running --services | grep -qx "$service" ||
        die "production service is not running: $service"
}

require_image() {
    local image="$1"
    docker image inspect "$image" >/dev/null 2>&1 ||
        die "required local image not found: $image"
}

volume_name() {
    printf '%s_%s\n' "$COMPOSE_PROJECT" "$1"
}

stamp="$(utc_stamp | tr '[:upper:]' '[:lower:]')"
restore_root="${BACKUP_ROOT:-/opt/umoweb/backups}/restores"
work_dir="$restore_root/promote-$stamp-$$"
new_env="$work_dir/.env.docker.new"
env_backup="$restore_root/.env.docker.pre-content-$stamp-$$"
promoted=0
services_stopped=0

cleanup() {
    local status=$?
    if ((status != 0 && promoted == 0 && services_stopped == 1)); then
        warn "promotion failed while services were stopped; production requires review"
        warn "pre-promotion environment backup: $env_backup"
    fi
    rm -rf "$work_dir"
    release_lock "$restore_root/.promote.lock"
    return "$status"
}
trap cleanup EXIT

mkdir -p "$restore_root"
chmod 0700 "$restore_root"
acquire_lock "$restore_root/.promote.lock"

require_running_service mysql
require_running_service backend
require_running_service frontend

for service in mysql backend frontend; do
    require_image "$(case "$service" in
        mysql) printf '%s' "$BACKUP_HELPER_IMAGE" ;;
        backend) printf '%s' "$BACKEND_IMAGE" ;;
        frontend) printf '%s' "$FRONTEND_IMAGE" ;;
    esac)"
done

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

backup_id="$(metadata_value BACKUP_ID)"
[[ "$backup_id" == umoweb-content-* ]] ||
    die "backup metadata is not a content import"
[[ "$(metadata_value TABLE_COUNT_contents)" =~ ^[1-9][0-9]*$ ]] ||
    die "content backup does not contain any contents"

log "creating a pre-promotion production backup"
"$BACKUP_SCRIPT_DIR/create-backup.sh"

log "staging new production credentials"
old_env_copy="$env_backup"
install -m 0600 "$COMPOSE_ENV_FILE" "$old_env_copy"
cp "$COMPOSE_ENV_FILE" "$new_env"
chmod 0600 "$new_env"
new_mysql_password="$(random_hex 24)"
new_mysql_root_password="$(random_hex 24)"
new_jwt_secret="$(random_hex 48)"
initial_admin_password="$(random_hex 18)"
set_env_value "$new_env" MYSQL_PASSWORD "$new_mysql_password"
set_env_value "$new_env" MYSQL_ROOT_PASSWORD "$new_mysql_root_password"
set_env_value "$new_env" JWT_SECRET "$new_jwt_secret"
set_env_value "$new_env" INIT_ADMIN_PASS "$initial_admin_password"

log "stopping production frontend and backend"
compose_source stop frontend backend
services_stopped=1

log "replacing production database content"
compose_source exec -T mysql sh -c '
    mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "
        DROP DATABASE IF EXISTS ${MYSQL_DATABASE};
        CREATE DATABASE ${MYSQL_DATABASE}
            DEFAULT CHARACTER SET utf8mb4
            DEFAULT COLLATE utf8mb4_unicode_ci;
    "
'
compose_source exec -T mysql sh -c '
    exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"
' < "$work_dir/database.sql"

log "replacing production app_data"
work_mount="$(docker_host_path "$work_dir")"
MSYS_NO_PATHCONV=1 docker run --rm --entrypoint bash \
    -v "$(volume_name app_data):/target" \
    -v "$work_mount:/backup:ro" \
    "$BACKUP_HELPER_IMAGE" \
    -c '
        find /target -mindepth 1 -delete
        tar -xzf /backup/app-data.tar.gz -C /target
    '

log "rotating MySQL credentials"
old_root_password="$(env_value MYSQL_ROOT_PASSWORD)"
compose_source exec -T mysql sh -c "
    mysql -uroot -p\"$old_root_password\" -e \"
        ALTER USER 'umo'@'%' IDENTIFIED BY '$new_mysql_password';
        ALTER USER 'root'@'localhost' IDENTIFIED BY '$new_mysql_root_password';
        ALTER USER 'root'@'%' IDENTIFIED BY '$new_mysql_root_password';
        FLUSH PRIVILEGES;
    \"
"

install -m 0600 "$new_env" "$COMPOSE_ENV_FILE"

log "starting promoted production stack"
compose_source up -d --no-build --wait
services_stopped=0

admin_username="$(env_value INIT_ADMIN_USER)"
admin_username="${admin_username:-admin}"
app_port="$(env_value APP_PORT)"
app_port="${app_port:-8080}"
base_url="http://127.0.0.1:${app_port}"
final_admin_password="$(random_hex 18)"

log "waiting for administrator initialization"
admin_count=""
for ((attempt = 0; attempt < 30; attempt++)); do
    admin_count="$(
        compose_source exec -T mysql sh -c '
            mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" \
                -N -B -e "SELECT COUNT(*) FROM users;"
        ' | tr -d '\r'
    )"
    if [[ "$admin_count" =~ ^[1-9][0-9]*$ ]]; then
        break
    fi
    sleep 2
done
[[ "$admin_count" =~ ^[1-9][0-9]*$ ]] ||
    die "administrator initialization did not complete"

log "rotating administrator password"
login_json="$(
    jq -nc \
        --arg username "$admin_username" \
        --arg password "$initial_admin_password" \
        '{username: $username, password: $password}'
)"
login_response="$(curl -fsS -H "Content-Type: application/json" -d "$login_json" "$base_url/api/admin/login")"
token="$(printf '%s' "$login_response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')"
change_json="$(
    jq -nc \
        --arg oldPassword "$initial_admin_password" \
        --arg newPassword "$final_admin_password" \
        '{oldPassword: $oldPassword, newPassword: $newPassword}'
)"
curl -fsS -X PUT \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$change_json" \
    "$base_url/api/admin/change-password" >/dev/null
set_env_value "$COMPOSE_ENV_FILE" INIT_ADMIN_PASS "$final_admin_password"
chmod 0600 "$COMPOSE_ENV_FILE"

old_login_json="$(
    jq -nc \
        --arg username "$admin_username" \
        --arg password "$initial_admin_password" \
        '{username: $username, password: $password}'
)"
old_status="$(curl -sS -o /dev/null -w '%{http_code}' -H "Content-Type: application/json" -d "$old_login_json" "$base_url/api/admin/login")"
[[ "$old_status" == "401" ]] ||
    die "old initial administrator password still works"
new_login_json="$(
    jq -nc \
        --arg username "$admin_username" \
        --arg password "$final_admin_password" \
        '{username: $username, password: $password}'
)"
curl -fsS -H "Content-Type: application/json" -d "$new_login_json" "$base_url/api/admin/login" >/dev/null

smoke_script="$REPO_ROOT/Server Side/UmoWebBackend/scripts/api-smoke.py"
require_file "$smoke_script"
python3 "$smoke_script" --base-url "$base_url" --env-file "$COMPOSE_ENV_FILE"

promoted=1
cat <<EOF
Content promotion completed.
Backup: $backup_id
Pre-promotion environment: $env_backup
Production environment: $COMPOSE_ENV_FILE
EOF
