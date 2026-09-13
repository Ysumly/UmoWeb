#!/usr/bin/env bash
set -Eeuo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
script_path="$repo_root/scripts/release/remote-release.sh"

if [[ ! -f "$script_path" ]]; then
    printf 'Missing remote release script: %s\n' "$script_path" >&2
    exit 1
fi

export REMOTE_RELEASE_SOURCE_ONLY=1
# shellcheck source=../remote-release.sh
source "$script_path"

failures=0

assert_equal() {
    local actual="$1"
    local expected="$2"
    local message="$3"
    if [[ "$actual" != "$expected" ]]; then
        printf 'ASSERTION FAILED: %s. Expected %q, got %q\n' "$message" "$expected" "$actual" >&2
        failures=$((failures + 1))
    fi
}

assert_true() {
    local condition="$1"
    local message="$2"
    if [[ "$condition" != "0" ]]; then
        printf 'ASSERTION FAILED: %s\n' "$message" >&2
        failures=$((failures + 1))
    fi
}

temp_root="$(mktemp -d)"
trap 'rm -rf "$temp_root"' EXIT

env_file="$temp_root/.env.docker"
cat > "$env_file" <<'EOF'
APP_PORT=8080
MYSQL_PASSWORD=unchanged
VITE_ADMIN_PATH=/private-admin
BACKEND_IMAGE=umoweb-backend:latest
FRONTEND_IMAGE=umoweb-frontend:latest
EOF
COMPOSE_ENV_FILE="$env_file"

assert_equal \
    "$(read_env_value_or_default "$env_file" "MISSING_IMAGE" "umoweb-backend:latest")" \
    "umoweb-backend:latest" \
    "missing env value did not use its default"

update_env_file "$env_file" "umoweb-backend:v1.0.0-rc.1" "umoweb-frontend:v1.0.0-rc.1"
assert_equal "$(sed -n 's/^BACKEND_IMAGE=//p' "$env_file")" "umoweb-backend:v1.0.0-rc.1" "backend image was not updated"
assert_equal "$(sed -n 's/^FRONTEND_IMAGE=//p' "$env_file")" "umoweb-frontend:v1.0.0-rc.1" "frontend image was not updated"
assert_equal "$(sed -n 's/^MYSQL_PASSWORD=//p' "$env_file")" "unchanged" "unrelated env values changed"

archive="$temp_root/images.tar"
printf 'image archive\n' > "$archive"
archive_hash="$(sha256_file "$archive")"
archive_size="$(file_size "$archive")"
manifest="$temp_root/manifest.json"
cat > "$manifest" <<EOF
{
  "schemaVersion": 1,
  "releaseId": "v1.0.0-rc.1",
  "kind": "release",
  "version": "v1.0.0-rc.1",
  "gitCommit": "922849cbf7b4fd14764bb29ce765607a6e6681d9",
  "ciRunId": 34736666387,
  "builtAtUtc": "2026-09-13T04:00:00Z",
  "backendImage": {
    "tag": "umoweb-backend:v1.0.0-rc.1",
    "imageId": "sha256:1111111111111111111111111111111111111111111111111111111111111111"
  },
  "frontendImage": {
    "tag": "umoweb-frontend:v1.0.0-rc.1",
    "imageId": "sha256:2222222222222222222222222222222222222222222222222222222222222222"
  },
  "archive": {
    "fileName": "images.tar",
    "sha256": "$archive_hash",
    "size": $archive_size
  },
  "adminPathSha256": "55b15c306754cf0b831e9d4ea80403c98b6bac5266597e9afc0121a35f475fce"
}
EOF

verify_archive "$archive" "$manifest"
verify_admin_path_hash "$manifest"

wrong_admin_manifest="$temp_root/wrong-admin-manifest.json"
sed 's/55b15c306754cf0b831e9d4ea80403c98b6bac5266597e9afc0121a35f475fce/e1d0e445857ba8bf7987ac03559de6f398e2c49a9fbbd3e2e50f3255d8c686b7/' \
    "$manifest" > "$wrong_admin_manifest"
if (verify_admin_path_hash "$wrong_admin_manifest") >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: admin path hash mismatch was accepted\n' >&2
fi

if (verify_archive "$temp_root/missing.tar" "$manifest") >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: missing archive was accepted\n' >&2
fi

printf 'tampered\n' > "$archive"
if (verify_archive "$archive" "$manifest") >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: archive hash mismatch was accepted\n' >&2
fi

fake_bin="$temp_root/bin"
mkdir -p "$fake_bin"
cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" == "image" && "${2:-}" == "inspect" ]]; then
    tag="${!#}"
    case "$tag" in
        umoweb-backend:*) printf 'sha256:1111111111111111111111111111111111111111111111111111111111111111\n' ;;
        umoweb-frontend:*) printf 'sha256:2222222222222222222222222222222222222222222222222222222222222222\n' ;;
        *) exit 1 ;;
    esac
    exit 0
fi

if [[ "${1:-}" == "load" ]]; then
    exit 0
fi

exit 0
EOF
chmod +x "$fake_bin/docker"

printf 'image archive\n' > "$archive"
DOCKER_BIN="$fake_bin/docker"
verify_image_ids "$manifest"

wrong_manifest="$temp_root/wrong-manifest.json"
sed 's/sha256:2222222222222222222222222222222222222222222222222222222222222222/sha256:9999999999999999999999999999999999999999999999999999999999999999/' \
    "$manifest" > "$wrong_manifest"
if (DOCKER_BIN="$fake_bin/docker" verify_image_ids "$wrong_manifest") >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: image ID mismatch was accepted\n' >&2
fi

healthy_stack='{"Service":"mysql","State":"running","Health":"healthy"}
{"Service":"backend","State":"running","Health":"healthy"}
{"Service":"frontend","State":"running","Health":""}'
verify_compose_health "$healthy_stack"

unhealthy_stack='{"Service":"mysql","State":"running","Health":"healthy"}
{"Service":"backend","State":"running","Health":"unhealthy"}
{"Service":"frontend","State":"running","Health":""}'
if (verify_compose_health "$unhealthy_stack") >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: unhealthy backend was accepted\n' >&2
fi

lock_root="$temp_root/locks"
RELEASE_ROOT="$lock_root"
acquire_release_lock
if (RELEASE_ROOT="$lock_root" acquire_release_lock) >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: concurrent release lock was accepted\n' >&2
fi
release_release_lock
assert_true "$([[ ! -d "$lock_root/.lock" ]]; echo $?)" "release lock was not removed"

stale_lock_root="$temp_root/stale-locks"
mkdir -p "$stale_lock_root/.lock"
printf '99999999\n' > "$stale_lock_root/.lock/pid"
printf '0\n' > "$stale_lock_root/.lock/created"
if ! (
    RELEASE_ROOT="$stale_lock_root"
    acquire_release_lock
    release_release_lock
) >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: stale release lock was not recovered\n' >&2
fi

failing_curl="$fake_bin/failing-curl"
cat > "$failing_curl" <<'EOF'
#!/usr/bin/env bash
exit 7
EOF
chmod +x "$failing_curl"
login_env="$temp_root/login.env"
cat > "$login_env" <<'EOF'
APP_PORT=8080
INIT_ADMIN_USER=admin
INIT_ADMIN_PASS=current-password
EOF
login_tmp="$temp_root/login-tmp"
mkdir -p "$login_tmp"
if (
    COMPOSE_ENV_FILE="$login_env"
    CURL_BIN="$failing_curl"
    RELEASE_TMP_ROOT="$login_tmp"
    verify_admin_login "http://127.0.0.1:8080"
) >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: failed admin login was accepted\n' >&2
fi
assert_equal "$(find "$login_tmp" -mindepth 1 -maxdepth 1 | wc -l | tr -d ' ')" "0" "failed admin login left a credential file"

rollback_root="$temp_root/rollback/releases"
rollback_env="$temp_root/rollback/.env.docker"
mkdir -p "$(dirname "$rollback_env")"
cat > "$rollback_env" <<'EOF'
APP_PORT=8080
VITE_ADMIN_PATH=/private-admin
BACKEND_IMAGE=umoweb-backend:old
FRONTEND_IMAGE=umoweb-frontend:old
EOF

RELEASE_ROOT="$rollback_root"
COMPOSE_ENV_FILE="$rollback_env"
release_archive="$temp_root/release-images.tar"
printf 'new image archive\n' > "$release_archive"
release_hash="$(sha256_file "$release_archive")"
release_size="$(file_size "$release_archive")"
release_manifest="$temp_root/release-manifest.json"
cat > "$release_manifest" <<EOF
{
  "schemaVersion": 1,
  "releaseId": "v1.0.0-rc.1",
  "kind": "release",
  "version": "v1.0.0-rc.1",
  "gitCommit": "922849cbf7b4fd14764bb29ce765607a6e6681d9",
  "ciRunId": 34736666387,
  "builtAtUtc": "2026-09-13T04:00:00Z",
  "backendImage": {
    "tag": "umoweb-backend:v1.0.0-rc.1",
    "imageId": "sha256:1111111111111111111111111111111111111111111111111111111111111111"
  },
  "frontendImage": {
    "tag": "umoweb-frontend:v1.0.0-rc.1",
    "imageId": "sha256:2222222222222222222222222222222222222222222222222222222222222222"
  },
  "archive": {
    "fileName": "release-images.tar",
    "sha256": "$release_hash",
    "size": $release_size
  },
  "adminPathSha256": "55b15c306754cf0b831e9d4ea80403c98b6bac5266597e9afc0121a35f475fce"
}
EOF

compose_source() {
    return 0
}

preflight_admin_login() {
    return 0
}

verify_runtime() {
    if grep -q '^BACKEND_IMAGE=umoweb-backend:v1.0.0-rc.1$' "$COMPOSE_ENV_FILE"; then
        return 1
    fi
    return 0
}

if (
    RELEASE_ROOT="$rollback_root"
    COMPOSE_ENV_FILE="$rollback_env"
    DOCKER_BIN="$fake_bin/docker"
    deploy_release "$release_archive" "$release_manifest" deploy
) >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: failed verification did not abort deployment\n' >&2
fi

assert_equal "$(sed -n 's/^BACKEND_IMAGE=//p' "$rollback_env")" "umoweb-backend:old" "failed deployment did not restore backend image"
assert_equal "$(sed -n 's/^FRONTEND_IMAGE=//p' "$rollback_env")" "umoweb-frontend:old" "failed deployment did not restore frontend image"

success_incoming="$rollback_root/incoming/v1.0.0-rc.1"
mkdir -p "$success_incoming"
cp "$release_archive" "$success_incoming/release-images.tar"
cp "$release_manifest" "$success_incoming/manifest.json"
verify_runtime() {
    return 0
}

if ! (
    RELEASE_ROOT="$rollback_root"
    COMPOSE_ENV_FILE="$rollback_env"
    DOCKER_BIN="$fake_bin/docker"
    deploy_release "$success_incoming/release-images.tar" "$success_incoming/manifest.json" deploy
) >/dev/null 2>&1; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: successful deployment was rejected\n' >&2
fi

assert_equal \
    "$(python -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["releaseId"])' "$rollback_root/current.json")" \
    "v1.0.0-rc.1" \
    "successful deployment did not record current state"
assert_true "$([[ ! -f "$success_incoming/release-images.tar" ]]; echo $?)" "remote release archive was not removed after deployment"
assert_true "$([[ ! -f "$success_incoming/manifest.json" ]]; echo $?)" "remote manifest was not removed after deployment"

capture_root="$temp_root/capture/releases"
capture_env="$temp_root/capture/.env.docker"
mkdir -p "$(dirname "$capture_env")"
cat > "$capture_env" <<'EOF'
APP_PORT=8080
VITE_ADMIN_PATH=/private-admin
BACKEND_IMAGE=umoweb-backend:latest
FRONTEND_IMAGE=umoweb-frontend:latest
EOF

compose_source() {
    case "$*" in
        *"ps -q backend"*) printf 'backend-container\n' ;;
        *"ps -q frontend"*) printf 'frontend-container\n' ;;
        *) return 1 ;;
    esac
}

cat > "$fake_bin/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" == "inspect" && "${2:-}" == "backend-container" ]]; then
    printf 'sha256:1111111111111111111111111111111111111111111111111111111111111111\n'
    exit 0
fi
if [[ "${1:-}" == "inspect" && "${2:-}" == "frontend-container" ]]; then
    printf 'sha256:2222222222222222222222222222222222222222222222222222222222222222\n'
    exit 0
fi
if [[ "${1:-}" == "tag" || "${1:-}" == "save" ]]; then
    if [[ "${1:-}" == "save" ]]; then
        output="${2:-}"
        [[ "$output" == "-o" ]] && output="${3:-}"
        printf 'captured images\n' > "$output"
    fi
    exit 0
fi
exit 1
EOF
chmod +x "$fake_bin/docker"

capture_json="$(
    RELEASE_ROOT="$capture_root" \
    COMPOSE_ENV_FILE="$capture_env" \
    DOCKER_BIN="$fake_bin/docker" \
    capture_current "baseline-test"
)"
assert_equal \
    "$(python -c 'import json,sys; print(json.load(sys.stdin)["kind"])' <<<"$capture_json")" \
    "baseline" \
    "baseline capture produced the wrong manifest kind"
assert_equal \
    "$(python -c 'import json,sys; print(json.load(sys.stdin)["backendImage"]["tag"])' <<<"$capture_json")" \
    "umoweb-backend:baseline-test" \
    "baseline capture produced the wrong backend tag"
assert_equal \
    "$(python -c 'import json,sys; print(json.load(sys.stdin)["adminPathSha256"])' <<<"$capture_json")" \
    "55b15c306754cf0b831e9d4ea80403c98b6bac5266597e9afc0121a35f475fce" \
    "baseline capture produced the wrong admin path hash"
if [[ "$capture_json" == *"/private-admin"* ]]; then
    failures=$((failures + 1))
    printf 'ASSERTION FAILED: baseline capture leaked the admin path\n' >&2
fi

if ((failures > 0)); then
    exit 1
fi

printf 'Bash release tests passed.\n'
