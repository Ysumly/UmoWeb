#!/usr/bin/env bash

set -Eeuo pipefail

SWAP_FILE="${SWAP_FILE:-/swapfile-openclaw}"
SWAP_MARKER="${SWAP_MARKER:-${SWAP_FILE}.umoweb-managed}"
FSTAB_PATH="${FSTAB_PATH:-/etc/fstab}"
MIN_FREE_KIB="${MIN_FREE_KIB:-3145728}"
SWAP_SIZE_GIB="${SWAP_SIZE_GIB:-2}"
SWAP_SIZE_BYTES="$((SWAP_SIZE_GIB * 1024 * 1024 * 1024))"
SWAP_MARKER_CONTENT="umoweb-openclaw-swap-v1"
SWAP_ENTRY="$SWAP_FILE none swap sw 0 0"

die() {
    printf '[openclaw-swap] ERROR: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

is_active_swap() {
    swapon --show=NAME --noheadings 2>/dev/null |
        awk -v path="$SWAP_FILE" '$1 == path { found = 1 } END { exit !found }'
}

fstab_has_entry() {
    awk -v path="$SWAP_FILE" '
        $1 == path && $2 == "none" && $3 == "swap" { found = 1 }
        END { exit !found }
    ' "$FSTAB_PATH"
}

remove_fstab_entry() {
    local temp_file="$FSTAB_PATH.tmp"

    awk -v path="$SWAP_FILE" '
        !($1 == path && $2 == "none" && $3 == "swap")
    ' "$FSTAB_PATH" > "$temp_file"
    mv "$temp_file" "$FSTAB_PATH"
}

verify_managed_swap() {
    [[ -f "$SWAP_FILE" ]] || die "managed swap file is missing"
    [[ -f "$SWAP_MARKER" ]] ||
        die "existing swap file is not managed by this installer: $SWAP_FILE"
    [[ "$(cat "$SWAP_MARKER")" == "$SWAP_MARKER_CONTENT" ]] ||
        die "existing swap marker is invalid: $SWAP_MARKER"
    [[ "$(stat -c '%s' "$SWAP_FILE")" == "$SWAP_SIZE_BYTES" ]] ||
        die "managed swap file size is not ${SWAP_SIZE_GIB} GiB"
}

[[ "$SWAP_FILE" == /* ]] || die "SWAP_FILE must be an absolute path"
[[ "$FSTAB_PATH" == /* ]] || die "FSTAB_PATH must be an absolute path"
[[ "$(id -u)" == "0" ]] || die "add-openclaw-swap.sh must run as root"

require_command awk
require_command df
require_command fallocate
require_command findmnt
require_command mkswap
require_command mv
require_command stat
require_command swapon
require_command swapoff
require_command systemctl

if [[ -e "$SWAP_FILE" || -e "$SWAP_MARKER" ]]; then
    verify_managed_swap

    if ! is_active_swap; then
        swapon "$SWAP_FILE"
    fi
    if ! fstab_has_entry; then
        printf '%s\n' "$SWAP_ENTRY" >> "$FSTAB_PATH"
    fi

    findmnt --verify
    systemctl daemon-reload
    printf 'Managed OpenClaw swap is already active: %s\n' "$SWAP_FILE"
    exit 0
fi

available_kib="$(
    df --output=avail -k "$(dirname "$SWAP_FILE")" |
        awk 'NF == 1 && $1 ~ /^[0-9]+$/ { value = $1 } END { print value }'
)"
[[ "$available_kib" =~ ^[0-9]+$ ]] ||
    die "could not determine free space for $(dirname "$SWAP_FILE")"
(( available_kib >= MIN_FREE_KIB )) ||
    die "at least 3 GiB free space is required to create the swap file"

if fstab_has_entry; then
    die "fstab already contains an entry for $SWAP_FILE"
fi

if is_active_swap; then
    die "swap is already active for $SWAP_FILE"
fi

created_file=0
activated_swap=0
added_fstab=0

rollback() {
    local exit_code="$?"

    trap - EXIT
    set +e

    if (( activated_swap == 1 )); then
        swapoff "$SWAP_FILE" >/dev/null 2>&1
    fi
    if (( added_fstab == 1 )) && [[ -f "$FSTAB_PATH" ]]; then
        remove_fstab_entry
    fi
    if (( created_file == 1 )); then
        rm -f -- "$SWAP_MARKER" "$SWAP_FILE"
    fi

    exit "$exit_code"
}
trap rollback EXIT

umask 077
fallocate -l "${SWAP_SIZE_GIB}G" "$SWAP_FILE"
created_file=1
chmod 0600 "$SWAP_FILE"
mkswap "$SWAP_FILE"
swapon "$SWAP_FILE"
activated_swap=1

printf '%s\n' "$SWAP_ENTRY" >> "$FSTAB_PATH"
added_fstab=1

printf '%s\n' "$SWAP_MARKER_CONTENT" > "$SWAP_MARKER"

findmnt --verify
systemctl daemon-reload

trap - EXIT
printf 'Created and activated OpenClaw swap: %s\n' "$SWAP_FILE"
printf 'Added fstab entry: %s\n' "$SWAP_ENTRY"
