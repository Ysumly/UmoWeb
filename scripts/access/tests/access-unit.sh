#!/usr/bin/env bash
set -Eeuo pipefail

export PATH="/usr/bin:/bin:$PATH"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ACCESS_DIR="$REPO_ROOT/scripts/access"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

python3 -m unittest discover -s "$ACCESS_DIR/tests" -p 'test_*.py' -v

INSTALL_ROOT="$TEMP_ROOT/install"
SYSTEMD_ROOT="$TEMP_ROOT/systemd"
ACCESS_CONFIG_PATH="$TEMP_ROOT/etc/access.env"
REPORT_USER="$(id -un)"
REPORT_GROUP="umoweb-report-test"
export INSTALL_ROOT SYSTEMD_ROOT ACCESS_CONFIG_PATH REPORT_USER REPORT_GROUP SKIP_SYSTEMD=1

mkdir -p "$INSTALL_ROOT/access/logs"
printf 'seed\n' > "$INSTALL_ROOT/access/logs/access.log"

bash "$ACCESS_DIR/install-access-timer.sh"

python3 - "$INSTALL_ROOT" "$ACCESS_CONFIG_PATH" "$SYSTEMD_ROOT" <<'PY'
import json
import os
import sys
from pathlib import Path

install_root = Path(sys.argv[1])
config_path = Path(sys.argv[2])
systemd_root = Path(sys.argv[3])

required = [
    config_path,
    install_root / "access/privacy-config.json",
    install_root / "access/trusted-proxies.conf",
    install_root / "access/logs/access.log",
    install_root / "scripts/access/access_maintenance.py",
    install_root / "scripts/access/report_server.py",
    systemd_root / "umoweb-access-maintenance.service",
    systemd_root / "umoweb-access-maintenance.timer",
    systemd_root / "umoweb-access-report.service",
]
missing = [str(path) for path in required if not path.is_file()]
if missing:
    raise SystemExit(f"installer did not render: {missing}")

policy = json.loads((install_root / "access/privacy-config.json").read_text(encoding="utf-8"))
if policy != {"rawRetentionDays": 30, "aggregateRetentionDays": 180}:
    raise SystemExit("installer rendered the wrong privacy policy")
if os.name != "nt" and (install_root / "access/logs/access.log").stat().st_mode & 0o777 != 0o640:
    raise SystemExit("installer did not create a 0640 active access log")

report_service = (systemd_root / "umoweb-access-report.service").read_text(encoding="utf-8")
if "User=" not in report_service:
    raise SystemExit("report service has no dedicated user")
PY

python3 - "$REPO_ROOT/access/privacy-config.json" \
    "$REPO_ROOT/Client Side/umo-web-frontend/public/privacy-config.json" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    runtime_default = json.load(handle)
with open(sys.argv[2], "r", encoding="utf-8") as handle:
    frontend_default = json.load(handle)
if runtime_default != frontend_default:
    raise SystemExit("default privacy configuration is inconsistent")
PY

if ! grep -q 'umask 0027' "$REPO_ROOT/docker/frontend/entrypoint.sh"; then
    echo "FAIL: frontend entrypoint does not set the access log umask" >&2
    exit 1
fi

if grep -R "@INSTALL_ROOT@\\|@REPORT_USER@\\|@REPORT_GROUP@" "$SYSTEMD_ROOT" >/dev/null; then
    echo "FAIL: rendered systemd units still contain placeholders" >&2
    exit 1
fi

printf 'Access unit tests passed.\n'
