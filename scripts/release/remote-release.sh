#!/usr/bin/env bash
set -Eeuo pipefail

UMOWEB_ROOT="${UMOWEB_ROOT:-/opt/umoweb}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-$UMOWEB_ROOT/.env.docker}"
COMPOSE_FILE="${COMPOSE_FILE:-$UMOWEB_ROOT/compose.yaml}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-umoweb}"
RELEASE_ROOT="${RELEASE_ROOT:-$UMOWEB_ROOT/releases}"
RELEASE_TMP_ROOT="${RELEASE_TMP_ROOT:-/tmp}"
RELEASE_LOCK_MAX_AGE_SECONDS="${RELEASE_LOCK_MAX_AGE_SECONDS:-3600}"
RELEASE_PUBLIC_BASE_URL="${RELEASE_PUBLIC_BASE_URL:-}"
DOCKER_BIN="${DOCKER_BIN:-docker}"
CURL_BIN="${CURL_BIN:-curl}"
PYTHON_BIN="${PYTHON_BIN:-}"
RELEASE_TEMP_FILES=()

log() {
    printf '[release] %s\n' "$*"
}

warn() {
    printf '[release] WARN: %s\n' "$*" >&2
}

die() {
    printf '[release] ERROR: %s\n' "$*" >&2
    exit 1
}

register_release_temp_file() {
    RELEASE_TEMP_FILES+=("$1")
}

cleanup_release_temp_files() {
    local file
    if ((${#RELEASE_TEMP_FILES[@]} > 0)); then
        for file in "${RELEASE_TEMP_FILES[@]}"; do
            rm -f "$file"
        done
    fi
}

cleanup_release_resources() {
    cleanup_release_temp_files
    release_release_lock
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_file() {
    [[ -f "$1" ]] || die "required file not found: $1"
}

python_command() {
    if [[ -n "$PYTHON_BIN" ]]; then
        printf '%s\n' "$PYTHON_BIN"
        return
    fi
    if command -v python3 >/dev/null 2>&1; then
        printf 'python3\n'
        return
    fi
    if command -v python >/dev/null 2>&1; then
        printf 'python\n'
        return
    fi
    die "python3 or python is required"
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

manifest_value() {
    local manifest="$1"
    local path="$2"
    "$(python_command)" - "$manifest" "$path" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    value = json.load(handle)

for part in sys.argv[2].split("."):
    value = value[part]

if value is None:
    print("")
elif isinstance(value, bool):
    print("true" if value else "false")
else:
    print(value)
PY
}

validate_manifest() {
    local manifest="$1"
    local python
    python="$(python_command)"
    "$python" - "$manifest" <<'PY'
import json
import re
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    manifest = json.load(handle)

if manifest.get("schemaVersion") != 1:
    raise SystemExit("unsupported release manifest schema")
if manifest.get("kind") not in {"release", "baseline"}:
    raise SystemExit("invalid release manifest kind")
if not manifest.get("releaseId"):
    raise SystemExit("release manifest is missing releaseId")
for key in ("backendImage", "frontendImage", "archive"):
    if not isinstance(manifest.get(key), dict):
        raise SystemExit(f"release manifest is missing {key}")
for key in ("backendImage", "frontendImage"):
    image = manifest[key]
    if not image.get("tag"):
        raise SystemExit(f"{key} has no tag")
    if not re.fullmatch(r"sha256:[0-9a-fA-F]{64}", str(image.get("imageId", ""))):
        raise SystemExit(f"{key} has an invalid image ID")
archive = manifest["archive"]
if not re.fullmatch(r"[0-9a-fA-F]{64}", str(archive.get("sha256", ""))):
    raise SystemExit("archive has an invalid SHA-256")
if not isinstance(archive.get("size"), int) or archive["size"] <= 0:
    raise SystemExit("archive has an invalid size")
if not re.fullmatch(r"[0-9a-fA-F]{64}", str(manifest.get("adminPathSha256", ""))):
    raise SystemExit("manifest has an invalid admin path SHA-256")
PY
}

verify_archive() {
    local archive="$1"
    local manifest="$2"
    require_file "$archive"
    require_file "$manifest"
    validate_manifest "$manifest"

    local expected_hash
    local expected_size
    local actual_hash
    local actual_size
    expected_hash="$(manifest_value "$manifest" "archive.sha256")"
    expected_size="$(manifest_value "$manifest" "archive.size")"
    actual_hash="$(sha256_file "$archive")"
    actual_size="$(file_size "$archive")"

    [[ "$actual_hash" == "$expected_hash" ]] ||
        die "archive SHA-256 mismatch: expected $expected_hash, got $actual_hash"
    [[ "$actual_size" == "$expected_size" ]] ||
        die "archive size mismatch: expected $expected_size, got $actual_size"
}

verify_image_ids() {
    local manifest="$1"
    require_file "$manifest"
    validate_manifest "$manifest"

    local expected_backend
    local expected_frontend
    local backend_tag
    local frontend_tag
    local actual_backend
    local actual_frontend
    expected_backend="$(manifest_value "$manifest" "backendImage.imageId")"
    expected_frontend="$(manifest_value "$manifest" "frontendImage.imageId")"
    backend_tag="$(manifest_value "$manifest" "backendImage.tag")"
    frontend_tag="$(manifest_value "$manifest" "frontendImage.tag")"
    actual_backend="$("$DOCKER_BIN" image inspect --format '{{.Id}}' "$backend_tag" | tr -d '\r')"
    actual_frontend="$("$DOCKER_BIN" image inspect --format '{{.Id}}' "$frontend_tag" | tr -d '\r')"

    [[ "$actual_backend" == "$expected_backend" ]] ||
        die "backend image ID mismatch for $backend_tag: expected $expected_backend, got $actual_backend"
    [[ "$actual_frontend" == "$expected_frontend" ]] ||
        die "frontend image ID mismatch for $frontend_tag: expected $expected_frontend, got $actual_frontend"
}

verify_admin_path_hash() {
    local manifest="$1"
    require_file "$manifest"
    validate_manifest "$manifest"

    local expected
    local actual
    expected="$(manifest_value "$manifest" "adminPathSha256")"
    actual="$(
        read_env_value "$COMPOSE_ENV_FILE" "VITE_ADMIN_PATH" |
            tr -d '\r\n' |
            sha256_text
    )"
    [[ "$actual" == "$expected" ]] ||
        die "frontend admin path hash does not match the release manifest"
}

verify_compose_health() {
    local health_json="$1"
    "$(python_command)" - "$health_json" <<'PY'
import json
import sys

services = {}
for line in sys.argv[1].splitlines():
    line = line.strip()
    if not line:
        continue
    item = json.loads(line)
    services[item.get("Service")] = item

for service in ("mysql", "backend"):
    item = services.get(service)
    if not item or item.get("State") != "running" or item.get("Health") != "healthy":
        raise SystemExit(f"{service} is not running and healthy")

frontend = services.get("frontend")
if not frontend or frontend.get("State") != "running":
    raise SystemExit("frontend is not running")
PY
}

read_env_value() {
    local env_file="$1"
    local key="$2"
    require_file "$env_file"
    awk -v key="$key" '
        index($0, key "=") == 1 {
            sub("^[^=]*=", "")
            print
            found = 1
            exit
        }
        END {
            if (!found) {
                exit 1
            }
        }
    ' "$env_file"
}

read_env_value_or_default() {
    local env_file="$1"
    local key="$2"
    local fallback="$3"
    read_env_value "$env_file" "$key" 2>/dev/null || printf '%s\n' "$fallback"
}

http_status() {
    local url="$1"
    "$CURL_BIN" \
        --silent \
        --show-error \
        --connect-timeout 5 \
        --max-time 15 \
        --output /dev/null \
        --write-out '%{http_code}' \
        "$url"
}

verify_site_url() {
    local base_url="$1"
    local homepage_code
    local site_code
    local site_body
    homepage_code="$(http_status "$base_url/")"
    [[ "$homepage_code" == "200" ]] ||
        die "homepage returned HTTP $homepage_code"

    site_body="$(mktemp "$RELEASE_TMP_ROOT/umoweb-site.XXXXXX")"
    register_release_temp_file "$site_body"
    if ! site_code="$(
        "$CURL_BIN" \
            --silent \
            --show-error \
            --connect-timeout 5 \
            --max-time 15 \
            --output "$site_body" \
            --write-out '%{http_code}' \
            "$base_url/api/public/site-info"
    )"; then
        rm -f "$site_body"
        die "public site-info request failed"
    fi
    [[ "$site_code" == "200" ]] ||
        {
            rm -f "$site_body"
            die "public site-info returned HTTP $site_code"
        }
    if ! "$(python_command)" - "$site_body" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    payload = json.load(handle)

if not isinstance(payload, dict) or "siteTitle" not in payload:
    raise SystemExit("public site-info payload is invalid")
PY
    then
        rm -f "$site_body"
        die "public site-info payload is invalid"
    fi
    rm -f "$site_body"
}

verify_admin_login() {
    local base_url="$1"
    local admin_user
    local admin_password
    local login_payload
    local login_code
    mkdir -p "$RELEASE_TMP_ROOT"
    chmod 0700 "$RELEASE_TMP_ROOT"
    admin_user="$(read_env_value "$COMPOSE_ENV_FILE" "INIT_ADMIN_USER")"
    admin_password="$(read_env_value "$COMPOSE_ENV_FILE" "INIT_ADMIN_PASS")"
    login_payload="$(mktemp "$RELEASE_TMP_ROOT/umoweb-login.XXXXXX")"
    register_release_temp_file "$login_payload"
    chmod 0600 "$login_payload"
    if ! ADMIN_USER="$admin_user" ADMIN_PASSWORD="$admin_password" "$(python_command)" - > "$login_payload" <<'PY'
import json
import os
import sys

json.dump(
    {
        "username": os.environ["ADMIN_USER"],
        "password": os.environ["ADMIN_PASSWORD"],
    },
    sys.stdout,
)
PY
    then
        rm -f "$login_payload"
        die "failed to prepare administrator login payload"
    fi
    if ! login_code="$(
        "$CURL_BIN" \
            --silent \
            --show-error \
            --connect-timeout 5 \
            --max-time 15 \
            --output /dev/null \
            --write-out '%{http_code}' \
            --header 'Content-Type: application/json' \
            --data-binary "@$login_payload" \
            "$base_url/api/admin/login"
    )"; then
        rm -f "$login_payload"
        die "administrator login request failed"
    fi
    rm -f "$login_payload"
    [[ "$login_code" == "200" ]] ||
        die "administrator login returned HTTP $login_code; verify INIT_ADMIN_PASS matches the current password"
}

runtime_base_url() {
    local app_port
    app_port="$(read_env_value "$COMPOSE_ENV_FILE" "APP_PORT" 2>/dev/null || printf '8080')"
    printf 'http://127.0.0.1:%s\n' "$app_port"
}

verify_runtime() {
    require_file "$COMPOSE_ENV_FILE"
    require_file "$COMPOSE_FILE"

    local base_url
    local health_json
    base_url="$(runtime_base_url)"

    health_json="$(compose_source ps --format json)"
    verify_compose_health "$health_json"
    verify_site_url "$base_url"
    verify_admin_login "$base_url"

    if [[ -n "$RELEASE_PUBLIC_BASE_URL" ]]; then
        local public_url="${RELEASE_PUBLIC_BASE_URL%/}"
        log "verifying public endpoint"
        verify_site_url "$public_url"
    fi
}

preflight_admin_login() {
    require_file "$COMPOSE_ENV_FILE"
    verify_admin_login "$(runtime_base_url)"
}

prune_release_images() {
    local backend_tag="$1"
    local backend_commit_tag="$2"
    local frontend_tag="$3"
    local frontend_commit_tag="$4"
    local repository
    local refs
    local reference

    for repository in umoweb-backend umoweb-frontend; do
        refs="$(
            "$DOCKER_BIN" image ls --format '{{.Repository}}:{{.Tag}}' |
                grep "^$repository:" || true
        )"
        while IFS= read -r reference; do
            [[ -n "$reference" ]] || continue
            if [[ "$repository" == "umoweb-backend" ]]; then
                [[ "$reference" == "$backend_tag" || "$reference" == "$backend_commit_tag" ]] && continue
            else
                [[ "$reference" == "$frontend_tag" || "$reference" == "$frontend_commit_tag" ]] && continue
            fi
            "$DOCKER_BIN" image rm "$reference" >/dev/null 2>&1 ||
                warn "failed to prune old release image: $reference"
        done <<< "$refs"
    done
}

compose_source() {
    "$DOCKER_BIN" compose \
        -p "$COMPOSE_PROJECT" \
        --env-file "$COMPOSE_ENV_FILE" \
        -f "$COMPOSE_FILE" \
        "$@"
}

acquire_release_lock() {
    mkdir -p "$RELEASE_ROOT"
    chmod 0700 "$RELEASE_ROOT"
    if mkdir "$RELEASE_ROOT/.lock" 2>/dev/null; then
        printf '%s\n' "$$" > "$RELEASE_ROOT/.lock/pid"
        date +%s > "$RELEASE_ROOT/.lock/created"
        return
    fi

    local lock_pid
    local lock_created
    local now
    local age
    lock_pid="$(cat "$RELEASE_ROOT/.lock/pid" 2>/dev/null || true)"
    lock_created="$(cat "$RELEASE_ROOT/.lock/created" 2>/dev/null || true)"
    now="$(date +%s)"
    age=-1
    if [[ "$lock_created" =~ ^[0-9]+$ ]]; then
        age=$((now - lock_created))
    fi
    if [[ "$lock_pid" =~ ^[0-9]+$ ]] &&
        kill -0 "$lock_pid" 2>/dev/null &&
        ((age >= 0 && age <= RELEASE_LOCK_MAX_AGE_SECONDS)); then
        die "another release operation holds $RELEASE_ROOT/.lock"
    fi

    warn "removing stale release lock"
    rm -f "$RELEASE_ROOT/.lock/pid" "$RELEASE_ROOT/.lock/created"
    rmdir "$RELEASE_ROOT/.lock" ||
        die "failed to remove stale release lock"
    mkdir "$RELEASE_ROOT/.lock" ||
        die "failed to acquire release lock"
    printf '%s\n' "$$" > "$RELEASE_ROOT/.lock/pid"
    date +%s > "$RELEASE_ROOT/.lock/created"
}

release_release_lock() {
    rm -f "$RELEASE_ROOT/.lock/pid" "$RELEASE_ROOT/.lock/created"
    rmdir "$RELEASE_ROOT/.lock" 2>/dev/null || true
}

rollback_previous_config() {
    local previous_backend="$1"
    local previous_frontend="$2"
    warn "restoring previous release configuration"
    update_env_file "$COMPOSE_ENV_FILE" "$previous_backend" "$previous_frontend"
    if ! compose_source up -d --no-build --wait backend frontend; then
        warn "previous containers failed to start after rollback"
        return 1
    fi
    if ! verify_runtime; then
        warn "previous release failed runtime verification after rollback"
        return 1
    fi
    return 0
}

write_current_state() {
    local manifest="$1"
    local operation="$2"
    local state_file="$RELEASE_ROOT/current.json"
    local temp_file
    mkdir -p "$RELEASE_ROOT"
    temp_file="$(mktemp "$RELEASE_ROOT/.current.json.XXXXXX")"
    "$(python_command)" - "$manifest" "$temp_file" "$operation" <<'PY'
import datetime
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    manifest = json.load(handle)

manifest["operation"] = sys.argv[3]
manifest["deployedAtUtc"] = (
    datetime.datetime.now(datetime.timezone.utc)
    .replace(microsecond=0)
    .isoformat()
    .replace("+00:00", "Z")
)

with open(sys.argv[2], "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
PY
    chmod 0600 "$temp_file"
    mv "$temp_file" "$state_file"
}

remove_incoming_file() {
    local target="$1"
    local allowed_prefix="$RELEASE_ROOT/incoming/"
    if [[ "$target" == "$allowed_prefix"* && -f "$target" ]]; then
        rm -f "$target"
    fi
}

deploy_release() {
    local archive="$1"
    local manifest="$2"
    local operation="${3:-deploy}"
    [[ "$operation" == "deploy" || "$operation" == "rollback" ]] ||
        die "invalid release operation: $operation"

    acquire_release_lock
    trap 'cleanup_release_resources' EXIT
    trap 'cleanup_release_resources; exit 130' INT TERM HUP
    verify_archive "$archive" "$manifest"
    verify_admin_path_hash "$manifest"
    preflight_admin_login
    "$DOCKER_BIN" load -i "$archive"
    verify_image_ids "$manifest"

    local backend_tag
    local frontend_tag
    local previous_backend
    local previous_frontend
    backend_tag="$(manifest_value "$manifest" "backendImage.tag")"
    frontend_tag="$(manifest_value "$manifest" "frontendImage.tag")"
    previous_backend="$(read_env_value_or_default "$COMPOSE_ENV_FILE" "BACKEND_IMAGE" "umoweb-backend:latest")"
    previous_frontend="$(read_env_value_or_default "$COMPOSE_ENV_FILE" "FRONTEND_IMAGE" "umoweb-frontend:latest")"

    update_env_file "$COMPOSE_ENV_FILE" "$backend_tag" "$frontend_tag"
    if ! compose_source up -d --no-build --wait backend frontend; then
        rollback_previous_config "$previous_backend" "$previous_frontend" || true
        die "release containers failed to start"
    fi
    if ! verify_runtime; then
        rollback_previous_config "$previous_backend" "$previous_frontend" || true
        die "release runtime verification failed"
    fi

    write_current_state "$manifest" "$operation"
    prune_release_images \
        "$backend_tag" \
        "$(manifest_value "$manifest" "backendImage.commitTag")" \
        "$frontend_tag" \
        "$(manifest_value "$manifest" "frontendImage.commitTag")"
    remove_incoming_file "$archive"
    remove_incoming_file "$manifest"
    trap - EXIT INT TERM HUP
    cleanup_release_temp_files
    release_release_lock
    log "$operation completed for $(manifest_value "$manifest" "releaseId")"
}

update_env_file() {
    local env_file="$1"
    local backend_image="$2"
    local frontend_image="$3"
    require_file "$env_file"

    local directory
    local temp_file
    directory="$(dirname "$env_file")"
    temp_file="$(mktemp "$directory/.env.docker.XXXXXX")"

    awk -v backend="$backend_image" -v frontend="$frontend_image" '
        BEGIN { backend_seen = 0; frontend_seen = 0 }
        /^BACKEND_IMAGE=/ {
            print "BACKEND_IMAGE=" backend
            backend_seen = 1
            next
        }
        /^FRONTEND_IMAGE=/ {
            print "FRONTEND_IMAGE=" frontend
            frontend_seen = 1
            next
        }
        { print }
        END {
            if (!backend_seen) {
                print "BACKEND_IMAGE=" backend
            }
            if (!frontend_seen) {
                print "FRONTEND_IMAGE=" frontend
            }
        }
    ' "$env_file" > "$temp_file"

    if chmod --reference="$env_file" "$temp_file" >/dev/null 2>&1; then
        :
    else
        chmod 0600 "$temp_file"
    fi
    mv "$temp_file" "$env_file"
}

sha256_text() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 | awk '{print $1}'
    else
        die "sha256sum or shasum is required"
    fi
}

capture_current() {
    local release_id="${1:-}"
    [[ "$release_id" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] ||
        die "invalid baseline release ID: $release_id"
    require_file "$COMPOSE_ENV_FILE"

    local incoming_dir
    local archive
    local manifest
    local backend_container
    local frontend_container
    local backend_image_id
    local frontend_image_id
    local backend_tag
    local frontend_tag
    local archive_hash
    local archive_size
    local admin_path_hash
    local built_at
    incoming_dir="$RELEASE_ROOT/incoming/$release_id"
    archive="$incoming_dir/umoweb-images-$release_id.tar"
    manifest="$incoming_dir/manifest.json"
    backend_tag="umoweb-backend:$release_id"
    frontend_tag="umoweb-frontend:$release_id"

    mkdir -p "$incoming_dir"
    chmod 0700 "$incoming_dir"

    backend_container="$(compose_source ps -q backend)"
    frontend_container="$(compose_source ps -q frontend)"
    [[ -n "$backend_container" && "$backend_container" != *$'\n'* ]] ||
        die "expected exactly one running backend container"
    [[ -n "$frontend_container" && "$frontend_container" != *$'\n'* ]] ||
        die "expected exactly one running frontend container"

    backend_image_id="$("$DOCKER_BIN" inspect --format '{{.Image}}' "$backend_container" | tr -d '\r')"
    frontend_image_id="$("$DOCKER_BIN" inspect --format '{{.Image}}' "$frontend_container" | tr -d '\r')"
    "$DOCKER_BIN" tag "$backend_image_id" "$backend_tag"
    "$DOCKER_BIN" tag "$frontend_image_id" "$frontend_tag"
    "$DOCKER_BIN" save -o "$archive" "$backend_tag" "$frontend_tag"
    archive_hash="$(sha256_file "$archive")"
    archive_size="$(file_size "$archive")"
    admin_path_hash="$(
        read_env_value "$COMPOSE_ENV_FILE" "VITE_ADMIN_PATH" |
            tr -d '\r\n' |
            sha256_text
    )"
    built_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

    "$(python_command)" - \
        "$manifest" \
        "$release_id" \
        "$built_at" \
        "$backend_tag" \
        "$backend_image_id" \
        "$frontend_tag" \
        "$frontend_image_id" \
        "$(basename "$archive")" \
        "$archive_hash" \
        "$archive_size" \
        "$admin_path_hash" <<'PY'
import json
import sys

(
    manifest_path,
    release_id,
    built_at,
    backend_tag,
    backend_image_id,
    frontend_tag,
    frontend_image_id,
    archive_name,
    archive_hash,
    archive_size,
    admin_path_hash,
) = sys.argv[1:]

manifest = {
    "schemaVersion": 1,
    "releaseId": release_id,
    "kind": "baseline",
    "version": None,
    "gitCommit": None,
    "ciRunId": None,
    "builtAtUtc": built_at,
    "backendImage": {
        "tag": backend_tag,
        "commitTag": backend_tag,
        "imageId": backend_image_id,
    },
    "frontendImage": {
        "tag": frontend_tag,
        "commitTag": frontend_tag,
        "imageId": frontend_image_id,
    },
    "archive": {
        "fileName": archive_name,
        "sha256": archive_hash,
        "size": int(archive_size),
    },
    "adminPathSha256": admin_path_hash,
}

with open(manifest_path, "w", encoding="utf-8") as handle:
    json.dump(manifest, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
PY

    cat "$manifest"
}

cleanup_capture() {
    local release_id="${1:-}"
    [[ "$release_id" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] ||
        die "invalid baseline release ID: $release_id"
    local incoming_dir="$RELEASE_ROOT/incoming/$release_id"
    rm -f "$incoming_dir/umoweb-images-$release_id.tar" "$incoming_dir/manifest.json"
    rmdir "$incoming_dir" 2>/dev/null || true
}

verify_current() {
    local state_file="$RELEASE_ROOT/current.json"
    require_file "$state_file"
    validate_manifest "$state_file"
    verify_admin_path_hash "$state_file"
    verify_image_ids "$state_file"
    verify_runtime
    cat "$state_file"
}

main() {
    trap 'cleanup_release_temp_files' EXIT
    trap 'cleanup_release_temp_files; exit 130' INT TERM HUP
    case "${1:-}" in
        verify-archive)
            [[ $# -eq 3 ]] || die "usage: $0 verify-archive ARCHIVE MANIFEST"
            verify_archive "$2" "$3"
            ;;
        capture)
            [[ $# -eq 3 && "$2" == "--name" ]] || die "usage: $0 capture --name RELEASE_ID"
            capture_current "$3"
            ;;
        cleanup-capture)
            [[ $# -eq 3 && "$2" == "--name" ]] || die "usage: $0 cleanup-capture --name RELEASE_ID"
            cleanup_capture "$3"
            ;;
        deploy)
            [[ $# -eq 3 ]] || die "usage: $0 deploy ARCHIVE MANIFEST"
            deploy_release "$2" "$3" deploy
            ;;
        rollback)
            [[ $# -eq 3 ]] || die "usage: $0 rollback ARCHIVE MANIFEST"
            deploy_release "$2" "$3" rollback
            ;;
        verify-current)
            [[ $# -eq 1 ]] || die "usage: $0 verify-current"
            verify_current
            ;;
        status)
            [[ $# -eq 1 ]] || die "usage: $0 status"
            require_file "$RELEASE_ROOT/current.json"
            cat "$RELEASE_ROOT/current.json"
            ;;
        *)
            die "unsupported action: ${1:-<missing>}"
            ;;
    esac
}

if [[ "${REMOTE_RELEASE_SOURCE_ONLY:-0}" != "1" ]]; then
    main "$@"
fi
