#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"

usage() {
    echo "Usage: $0 <umoweb-backup-*.tar.gz>" >&2
}

archive="${1:-}"
[[ -n "$archive" ]] || {
    usage
    exit 2
}
archive="$(cd "$(dirname "$archive")" && pwd)/$(basename "$archive")"
require_file "$archive"
require_command tar
require_command sha256sum

verify_sha256_file "$archive" "$archive.sha256" ||
    die "backup checksum verification failed"

work_dir="$(mktemp -d)"
cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

tar -xzf "$archive" -C "$work_dir"

for required in database.sql app-data.tar.gz app-files.tsv metadata.env SHA256SUMS; do
    require_file "$work_dir/$required"
done

(
    cd "$work_dir"
    sha256sum -c SHA256SUMS
)

echo "Backup verified: $archive"
