#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
    echo "Usage: $0 <umoweb-backup-*.tar.gz> <destination-directory>" >&2
}

source_archive="${1:-}"
destination="${2:-}"
if [[ -z "$source_archive" || -z "$destination" ]]; then
    usage
    exit 2
fi

source_archive="$(cd "$(dirname "$source_archive")" && pwd)/$(basename "$source_archive")"
require_file "$source_archive"
require_command cp

bash "$SCRIPT_DIR/verify-backup.sh" "$source_archive"

mkdir -p "$destination"
chmod 0700 "$destination"

destination_archive="$destination/$(basename "$source_archive")"
destination_checksum="$destination_archive.sha256"

cp "$source_archive" "$destination_archive"
cp "$source_archive.sha256" "$destination_checksum"
chmod 0600 "$destination_archive" "$destination_checksum"

verify_sha256_file "$destination_archive" "$destination_checksum" ||
    die "exported backup checksum verification failed"

echo "Backup exported: $destination_archive"
echo "Export is not encrypted; protect the destination directory and copied archive."
