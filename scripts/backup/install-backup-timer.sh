#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

INSTALL_ROOT="${INSTALL_ROOT:-/opt/umoweb}"
TIMER_ENV_FILE="${TIMER_ENV_FILE:-/etc/umoweb/backup.env}"

[[ "$(id -u)" == "0" ]] || die "install-backup-timer.sh must run as root"
require_command systemctl
require_command sed
require_file "$SCRIPT_DIR/systemd/umoweb-backup.service"
require_file "$SCRIPT_DIR/systemd/umoweb-backup.timer"
require_file "$INSTALL_ROOT/scripts/backup/create-backup.sh"

install -d -m 0700 "$INSTALL_ROOT/backups"
install -d -m 0700 "/etc/umoweb"

if [[ ! -f "$TIMER_ENV_FILE" ]]; then
    cat > "$TIMER_ENV_FILE" <<EOF
COMPOSE_FILE=$INSTALL_ROOT/compose.yaml
COMPOSE_ENV_FILE=$INSTALL_ROOT/.env.docker
COMPOSE_PROJECT=umoweb
BACKUP_ROOT=$INSTALL_ROOT/backups
BACKUP_RETENTION_COUNT=6
BACKUP_MAX_BYTES=8589934592
BACKUP_HELPER_IMAGE=mysql:8.4
BACKEND_IMAGE=umoweb-backend:latest
FRONTEND_IMAGE=umoweb-frontend:latest
# Non-Git deployments may set GIT_COMMIT to the deployed source revision.
EOF
    chmod 0600 "$TIMER_ENV_FILE"
fi

sed "s|@INSTALL_ROOT@|$INSTALL_ROOT|g" \
    "$SCRIPT_DIR/systemd/umoweb-backup.service" \
    > /etc/systemd/system/umoweb-backup.service
install -m 0644 "$SCRIPT_DIR/systemd/umoweb-backup.timer" \
    /etc/systemd/system/umoweb-backup.timer

systemctl daemon-reload
systemctl enable --now umoweb-backup.timer
systemctl list-timers umoweb-backup.timer --no-pager

echo "Installed weekly UmoWeb backup timer. Run manually with:"
echo "  systemctl start umoweb-backup.service"
