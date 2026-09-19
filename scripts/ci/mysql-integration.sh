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
fake_openai_port="${FAKE_OPENAI_PORT:-19090}"
fake_openai_log="${FAKE_OPENAI_LOG:-${RUNNER_TEMP:-/tmp}/umoweb-fake-openai.log}"

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
mysql_client < "$repo_root/docs/design/migrations/20260913_content_search.sql"
mysql_client < "$repo_root/docs/design/migrations/20260913_content_search.sql"
mysql_client < "$repo_root/docs/design/migrations/20260915_content_schedule.sql"
mysql_client < "$repo_root/docs/design/migrations/20260915_content_schedule.sql"

# Simulate an existing pre-AI database, then prove the migration creates and seeds
# both tables twice without duplicating modes or versions.
mysql_client -e "SET FOREIGN_KEY_CHECKS = 0; DROP TABLE IF EXISTS umo_blog.ai_transform_mode_versions; DROP TABLE IF EXISTS umo_blog.ai_transform_modes; SET FOREIGN_KEY_CHECKS = 1;"
mysql_client < "$repo_root/docs/design/migrations/20260918_admin_ai_modes.sql"
mysql_client < "$repo_root/docs/design/migrations/20260918_admin_ai_modes.sql"

assert_value "database character set" "utf8mb4" \
  "SELECT DEFAULT_CHARACTER_SET_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'umo_blog'"
assert_value "database collation" "utf8mb4_unicode_ci" \
  "SELECT DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'umo_blog'"
assert_value "token_version column" "1" \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'umo_blog' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'token_version'"
assert_value "migration indexes" "3" \
  "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'umo_blog' AND INDEX_NAME IN ('idx_published_at', 'idx_content_category_category_id', 'idx_content_tag_tag_id')"
assert_value "migration foreign keys" "7" \
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
assert_value "content search table" "1" \
  "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'umo_blog' AND TABLE_NAME = 'content_search'"
assert_value "content search fulltext index" "1" \
  "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'umo_blog' AND TABLE_NAME = 'content_search' AND INDEX_NAME = 'ft_content_search_body' AND INDEX_TYPE = 'FULLTEXT'"
assert_value "scheduled_at column" "1" \
  "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'umo_blog' AND TABLE_NAME = 'contents' AND COLUMN_NAME = 'scheduled_at'"
assert_value "schedule status index" "1" \
  "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = 'umo_blog' AND TABLE_NAME = 'contents' AND INDEX_NAME = 'idx_status_scheduled_at'"
assert_value "ai mode rows" "5" \
  "SELECT COUNT(*) FROM umo_blog.ai_transform_modes"
assert_value "ai mode version rows" "5" \
  "SELECT COUNT(*) FROM umo_blog.ai_transform_mode_versions WHERE version_no = 1"
assert_value "disabled ai modes" "5" \
  "SELECT COUNT(*) FROM umo_blog.ai_transform_modes WHERE enabled = 0"
assert_value "orphan relations after migration" "0" \
  "SELECT (SELECT COUNT(*) FROM umo_blog.content_category cc LEFT JOIN umo_blog.contents c ON c.id = cc.content_id LEFT JOIN umo_blog.categories category ON category.id = cc.category_id WHERE c.id IS NULL OR category.id IS NULL) + (SELECT COUNT(*) FROM umo_blog.content_tag ct LEFT JOIN umo_blog.contents c ON c.id = ct.content_id LEFT JOIN umo_blog.tags tag ON tag.id = ct.tag_id WHERE c.id IS NULL OR tag.id IS NULL) + (SELECT COUNT(*) FROM umo_blog.categories child LEFT JOIN umo_blog.categories parent ON parent.id = child.parent_id WHERE child.parent_id IS NOT NULL AND parent.id IS NULL)"

mkdir -p "$storage_dir"
cp -R "$repo_root/docker/demo-data/." "$storage_dir/"
cd "$backend_dir"
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:$mysql_port/umo_blog?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
export DB_USER=root
export DB_PASS="$mysql_password"
export APP_STORAGE_PATH="$storage_dir"
export MYSQL_INTEGRATION=true
"$maven_bin" -B \
  -Dtest=ContentCategoryFilterIntegrationTest,ImageManagementIntegrationTest,ContentSearchIntegrationTest,RelatedContentIntegrationTest,ScheduledContentIntegrationTest,AiModeCatalogIntegrationTest \
  test
"$maven_bin" -B -DskipTests package

export SPRING_PROFILES_ACTIVE=prod
export JWT_SECRET="$(openssl rand -hex 32)"
export INIT_ADMIN_USER="ci_admin_${GITHUB_RUN_ID:-local}_${GITHUB_RUN_ATTEMPT:-1}"
export INIT_ADMIN_PASS="Ci$(openssl rand -hex 24)"
export NO_PROXY="127.0.0.1,localhost,::1"
export no_proxy="$NO_PROXY"
export PYTHONUNBUFFERED=1
export APP_AI_ENABLED=true
export DEEPSEEK_BASE_URL="http://127.0.0.1:$fake_openai_port"
export DEEPSEEK_API_KEY="fake-ci-key"
export DEEPSEEK_MODEL="fake-model"

"$repo_root/scripts/search/rebuild-content-search.sh" \
  "$backend_dir/target/UmoWebBackend-0.0.1-SNAPSHOT.jar"
"$repo_root/scripts/search/rebuild-content-search.sh" \
  "$backend_dir/target/UmoWebBackend-0.0.1-SNAPSHOT.jar"

assert_value "search index rows after two rebuilds" "5" \
  "SELECT COUNT(*) FROM umo_blog.content_search"
assert_value "Chinese ngram body search" "1" \
  "SELECT COUNT(*) FROM umo_blog.content_search WHERE MATCH(body_text) AGAINST('清晨的雾' IN NATURAL LANGUAGE MODE)"

backend_pid=""
fake_openai_pid=""
cleanup_processes() {
  if [[ -n "$fake_openai_pid" ]] && kill -0 "$fake_openai_pid" 2>/dev/null; then
    kill "$fake_openai_pid" 2>/dev/null || true
    wait "$fake_openai_pid" 2>/dev/null || true
  fi
  if [[ -n "$backend_pid" ]] && kill -0 "$backend_pid" 2>/dev/null; then
    kill "$backend_pid" 2>/dev/null || true
    wait "$backend_pid" 2>/dev/null || true
  fi
}
trap cleanup_processes EXIT

"$python_bin" "$repo_root/scripts/ai/fake-openai-server.py" \
  --port "$fake_openai_port" >"$fake_openai_log" 2>&1 &
fake_openai_pid=$!

fake_ready=false
for attempt in $(seq 1 30); do
  if curl --noproxy '*' --silent --max-time 1 \
    --output /dev/null "http://127.0.0.1:$fake_openai_port/unknown" 2>/dev/null; then
    fake_ready=true
    break
  fi
  if ! kill -0 "$fake_openai_pid" 2>/dev/null; then
    echo "Fake OpenAI server exited before becoming ready." >&2
    cat "$fake_openai_log" >&2
    exit 1
  fi
  sleep 0.2
done

if [[ "$fake_ready" != "true" ]]; then
  echo "Fake OpenAI server did not become ready." >&2
  cat "$fake_openai_log" >&2
  exit 1
fi

java -jar target/UmoWebBackend-0.0.1-SNAPSHOT.jar >"$backend_log" 2>&1 &
backend_pid=$!

ready=false
for attempt in $(seq 1 60); do
  status="$(curl --noproxy '*' --max-time 5 --silent --output /dev/null \
    --write-out "%{http_code}" "http://127.0.0.1:8080/api/public/site-info" || true)"
  if [[ "$status" == "200" ]]; then
    ready=true
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    echo "Backend exited before becoming healthy." >&2
    cat "$backend_log" >&2
    exit 1
  fi
  if (( attempt == 1 || attempt % 5 == 0 )); then
    echo "Waiting for backend health: attempt ${attempt}/60, HTTP ${status:-unavailable}" >&2
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
  --password "$INIT_ADMIN_PASS" \
  --include-ai; then
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
assert_value "search index rows after smoke cleanup" "5" \
  "SELECT COUNT(*) FROM umo_blog.content_search"

image_files="$(find "$storage_dir/images" -type f -name '*.png' | wc -l)"
if [[ "$image_files" -ne 0 ]]; then
  echo "Expected no uploaded PNG after smoke cleanup in $storage_dir/images." >&2
  exit 1
fi

echo "MySQL integration passed with schema, seed, migrations, and 40/40 API smoke."
