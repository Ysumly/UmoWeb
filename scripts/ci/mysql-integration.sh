#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
backend_dir="$repo_root/Server Side/UmoWebBackend"
mysql_password="${MYSQL_PASSWORD:-ci-root-password}"
mysql_port="${MYSQL_PORT:-3306}"
python_bin="${PYTHON_BIN:-python3}"
maven_bin="${MAVEN_BIN:-mvn}"
storage_dir="${STORAGE_DIR:-${RUNNER_TEMP:-/tmp}/umoweb-storage}"
backend_log="${BACKEND_LOG:-${RUNNER_TEMP:-/tmp}/umoweb-backend.log}"

mysql_client() {
  docker run --rm -i \
    --add-host=host.docker.internal:host-gateway \
    -e MYSQL_PWD="$mysql_password" \
    mysql:8.4 \
    mysql --protocol=tcp -hhost.docker.internal -P"$mysql_port" -uroot \
    --default-character-set=utf8mb4 "$@"
}

assert_value() {
  local label="$1"
  local expected="$2"
  local query="$3"
  local actual

  actual="$(mysql_client --batch --skip-column-names -e "$query")"
  if [[ "$actual" != "$expected" ]]; then
    echo "$label: expected '$expected', got '$actual'" >&2
    exit 1
  fi
}

wait_for_mysql() {
  for _ in $(seq 1 60); do
    if mysql_client --batch --skip-column-names -e "SELECT 1" >/dev/null 2>&1; then
      return
    fi
    sleep 2
  done
  echo "MySQL did not become ready within 120 seconds." >&2
  exit 1
}

wait_for_mysql
mysql_client -e "DROP DATABASE IF EXISTS umo_blog;"
mysql_client < "$repo_root/docs/design/schema.sql"
mysql_client < "$repo_root/docs/design/seed-data.sql"
mysql_client < "$repo_root/docs/design/migrations/20260911_integrity_security.sql"
mysql_client < "$repo_root/docs/design/migrations/20260911_integrity_security.sql"
mysql_client < "$repo_root/docs/design/migrations/20260913_image_cleanup_queue.sql"
mysql_client < "$repo_root/docs/design/migrations/20260913_image_cleanup_queue.sql"

assert_value "database character set" "utf8mb4" \
  "SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'umo_blog'"
assert_value "database collation" "utf8mb4_unicode_ci" \
  "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'umo_blog'"
assert_value "token_version column" "1" \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'umo_blog' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'token_version'"
assert_value "migration indexes" "3" \
  "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'umo_blog' AND INDEX_NAME IN ('idx_published_at', 'idx_content_category_category_id', 'idx_content_tag_tag_id')"
assert_value "migration foreign keys" "5" \
  "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = 'umo_blog' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"
assert_value "seed categories" "10" \
  "SELECT COUNT(*) FROM umo_blog.categories"
assert_value "seed tags" "9" \
  "SELECT COUNT(*) FROM umo_blog.tags"
assert_value "seed contents" "6" \
  "SELECT COUNT(*) FROM umo_blog.contents"
assert_value "seed category links" "9" \
  "SELECT COUNT(*) FROM umo_blog.content_category"
assert_value "seed tag links" "12" \
  "SELECT COUNT(*) FROM umo_blog.content_tag"
assert_value "seed site options" "4" \
  "SELECT COUNT(*) FROM umo_blog.site_options"
assert_value "image cleanup queue table" "1" \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'umo_blog' AND TABLE_NAME = 'image_cleanup_queue'"
assert_value "orphan relations after migration" "0" \
  "SELECT (SELECT COUNT(*) FROM umo_blog.content_category cc LEFT JOIN umo_blog.contents c ON c.id = cc.content_id LEFT JOIN umo_blog.categories category ON category.id = cc.category_id WHERE c.id IS NULL OR category.id IS NULL) + (SELECT COUNT(*) FROM umo_blog.content_tag ct LEFT JOIN umo_blog.contents c ON c.id = ct.content_id LEFT JOIN umo_blog.tags tag ON tag.id = ct.tag_id WHERE c.id IS NULL OR tag.id IS NULL) + (SELECT COUNT(*) FROM umo_blog.categories child LEFT JOIN umo_blog.categories parent ON parent.id = child.parent_id WHERE child.parent_id IS NOT NULL AND parent.id IS NULL)"

mkdir -p "$storage_dir"
cd "$backend_dir"
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$mysql_port/umo_blog?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
export DB_USER=root
export DB_PASS="$mysql_password"
export APP_STORAGE_PATH="$storage_dir"
export MYSQL_INTEGRATION=true
"$maven_bin" -B -Dtest=ContentCategoryFilterIntegrationTest,ImageManagementIntegrationTest test
"$maven_bin" -B -DskipTests package

export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET="$(openssl rand -hex 32)"
export INIT_ADMIN_USER="ci_admin_${GITHUB_RUN_ID:-local}_${GITHUB_RUN_ATTEMPT:-1}"
export INIT_ADMIN_PASS="Ci$(openssl rand -hex 24)"

backend_pid=""
cleanup_backend() {
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
}
trap cleanup_backend EXIT

java -jar target/UmoWebBackend-0.0.1-SNAPSHOT.jar >"$backend_log" 2>&1 &
backend_pid=$!

ready=false
for _ in $(seq 1 60); do
  if curl --fail --silent "http://127.0.0.1:8080/api/public/site-info" >/dev/null; then
    ready=true
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "Backend exited before becoming healthy." >&2
    cat "$backend_log" >&2
    exit 1
  fi
  sleep 2
done

if [[ "$ready" != "true" ]]; then
  echo "Backend did not become healthy within 120 seconds." >&2
  cat "$backend_log" >&2
  exit 1
fi

if ! "$python_bin" scripts/api-smoke.py \
  --base-url "http://127.0.0.1:8080" \
  --username "$INIT_ADMIN_USER" \
  --password "$INIT_ADMIN_PASS"; then
  echo "API smoke failed. Backend log:" >&2
  cat "$backend_log" >&2
  exit 1
fi

assert_value "image rows after smoke" "0" \
  "SELECT COUNT(*) FROM umo_blog.images"
assert_value "image cleanup queue after smoke" "0" \
  "SELECT COUNT(*) FROM umo_blog.image_cleanup_queue"
assert_value "contents after smoke cleanup" "6" \
  "SELECT COUNT(*) FROM umo_blog.contents"

image_files="$(find "$storage_dir/images" -type f -name '*.png' | wc -l)"
if [[ "$image_files" -ne 0 ]]; then
  echo "Expected no uploaded PNG after smoke cleanup in $storage_dir/images." >&2
  exit 1
fi

echo "MySQL integration passed with schema, seed, migrations, and 29/29 API smoke."
