#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=access-lib.sh
source "$SCRIPT_DIR/access-lib.sh"

access_load_config
access_require_command python3
access_require_file "$SCRIPT_DIR/report_server.py"

exec python3 "$SCRIPT_DIR/report_server.py" \
    --host "$ACCESS_REPORT_HOST" \
    --port "$ACCESS_REPORT_PORT" \
    --report "$ACCESS_REPORT_DIR/index.html"
