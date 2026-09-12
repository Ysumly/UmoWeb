#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
    echo "Usage: $0 <umoweb-restore-project> [restore-env-file]" >&2
}

project="${1:-}"
env_file="${2:-}"
[[ -n "$project" ]] || {
    usage
    exit 2
}
assert_restore_project_name "$project" ||
    die "refusing to clean project outside umoweb-restore-*: $project"
assert_project_name "$project"
require_command docker
require_file "$COMPOSE_FILE"

RESTORE_ROOT="${RESTORE_ROOT:-$BACKUP_ROOT/restores}"
env_file="${env_file:-$RESTORE_ROOT/$project.env}"
require_file "$env_file"

docker compose -p "$project" --env-file "$env_file" -f "$COMPOSE_FILE" \
    down -v --remove-orphans

rm -f "$env_file"
rm -rf "$RESTORE_ROOT/$project"

echo "Removed isolated restore environment: $project"
