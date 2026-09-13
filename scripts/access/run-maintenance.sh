#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=access-lib.sh
source "$SCRIPT_DIR/access-lib.sh"

access_load_config
access_require_command docker
access_require_command gzip
access_require_command python3
access_require_file "$SCRIPT_DIR/access_maintenance.py"
access_prepare_directories

umask 0027
active_log="$ACCESS_LOG_DIR/access.log"

if [[ -s "$active_log" && ! -L "$active_log" ]]; then
    rotated_log="$ACCESS_LOG_DIR/access-$(date -u +%Y%m%dT%H%M%SZ).log"
    chmod 0640 "$active_log"
    mv "$active_log" "$rotated_log"
    if ! access_compose exec -T frontend nginx -s reopen >/dev/null; then
        mv "$rotated_log" "$active_log"
        access_die "failed to reopen the frontend access log"
    fi
    chmod 0640 "$active_log" 2>/dev/null || true
fi

for rotated_log in "$ACCESS_LOG_DIR"/access-*.log; do
    [[ -f "$rotated_log" ]] || continue
    chmod 0640 "$rotated_log"
    gzip -9 "$rotated_log"
done

python3 "$SCRIPT_DIR/access_maintenance.py" run --config "$ACCESS_CONFIG_FILE"
access_secure_files
