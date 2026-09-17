#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"
backend_dir="$repo_root/Server Side/UmoWebBackend"
jar_path="${1:-$backend_dir/target/UmoWebBackend-0.0.1-SNAPSHOT.jar}"

if [[ ! -f "$jar_path" ]]; then
  echo "Backend jar not found: $jar_path" >&2
  echo "Run Maven package before rebuilding the content search index." >&2
  exit 1
fi

cd "$backend_dir"
exec java -jar "$jar_path" \
  --spring.main.web-application-type=none \
  --app.scheduling.enabled=false \
  --app.search.backfill-only=true
