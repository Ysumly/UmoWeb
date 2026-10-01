#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="${BASH_SOURCE[0]%/*}"
if [[ "$SCRIPT_DIR" == "${BASH_SOURCE[0]}" ]]; then
    SCRIPT_DIR="."
fi
SCRIPT_DIR="$(cd -- "$SCRIPT_DIR" && pwd -P)"
INSTALLER="$SCRIPT_DIR/../add-openclaw-swap.sh"

fail() {
    printf 'FAIL: %s\n' "$*" >&2
    exit 1
}

assert_file_contains() {
    local file="$1"
    local expected="$2"

    grep -Fq -- "$expected" "$file" ||
        fail "expected '$expected' in $file"
}

if [[ ! -f "$INSTALLER" ]]; then
    fail "swap installer not found at $INSTALLER"
fi

TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

FAKE_BIN="$TEMP_ROOT/bin"
SWAP_FILE="$TEMP_ROOT/swapfile-openclaw"
SWAP_MARKER="$SWAP_FILE.umoweb-managed"
FSTAB_PATH="$TEMP_ROOT/fstab"
COMMAND_LOG="$TEMP_ROOT/commands.log"
SWAP_STATE="$TEMP_ROOT/active-swaps"
EXISTING_SWAP="$TEMP_ROOT/www-swap"

mkdir -p "$FAKE_BIN"
printf 'existing-swap\n' > "$EXISTING_SWAP"
: > "$FSTAB_PATH"
: > "$COMMAND_LOG"
: > "$SWAP_STATE"

cat > "$FAKE_BIN/id" <<'FAKE_ID'
#!/usr/bin/env bash

set -euo pipefail

if [[ "${1:-}" == "-u" ]]; then
    printf '%s\n' "${FAKE_UID:-0}"
    exit 0
fi

exec /usr/bin/id "$@"
FAKE_ID

cat > "$FAKE_BIN/df" <<'FAKE_DF'
#!/usr/bin/env bash

set -euo pipefail

printf '%s\n' "${FAKE_AVAIL_KIB:-3145728}"
FAKE_DF

cat > "$FAKE_BIN/fallocate" <<'FAKE_FALLOCATE'
#!/usr/bin/env bash

set -euo pipefail

size=""
path=""
while [[ "$#" -gt 0 ]]; do
    case "$1" in
        -l)
            size="$2"
            shift 2
            ;;
        *)
            path="$1"
            shift
            ;;
    esac
done

[[ -n "$size" && -n "$path" ]] || exit 2
case "$size" in
    2G) bytes=$((2 * 1024 * 1024 * 1024)) ;;
    *) exit 2 ;;
esac

truncate -s "$bytes" "$path"
printf 'fallocate <%s> <%s>\n' "$size" "$path" >> "$FAKE_COMMAND_LOG"
FAKE_FALLOCATE

cat > "$FAKE_BIN/mkswap" <<'FAKE_MKSWAP'
#!/usr/bin/env bash

set -euo pipefail

printf 'mkswap <%s>\n' "$1" >> "$FAKE_COMMAND_LOG"
FAKE_MKSWAP

cat > "$FAKE_BIN/chmod" <<'FAKE_CHMOD'
#!/usr/bin/env bash

set -euo pipefail

printf 'chmod' >> "$FAKE_COMMAND_LOG"
printf ' <%s>' "$@" >> "$FAKE_COMMAND_LOG"
printf '\n' >> "$FAKE_COMMAND_LOG"
FAKE_CHMOD

cat > "$FAKE_BIN/swapon" <<'FAKE_SWAPON'
#!/usr/bin/env bash

set -euo pipefail

if [[ "${1:-}" == "--show=NAME" ]]; then
    cat "$FAKE_SWAP_STATE"
    exit 0
fi

printf 'swapon <%s>\n' "$1" >> "$FAKE_COMMAND_LOG"
printf '%s\n' "$1" >> "$FAKE_SWAP_STATE"
FAKE_SWAPON

cat > "$FAKE_BIN/swapoff" <<'FAKE_SWAPOFF'
#!/usr/bin/env bash

set -euo pipefail

printf 'swapoff <%s>\n' "$1" >> "$FAKE_COMMAND_LOG"
grep -Fvx -- "$1" "$FAKE_SWAP_STATE" > "$FAKE_SWAP_STATE.tmp" || true
mv "$FAKE_SWAP_STATE.tmp" "$FAKE_SWAP_STATE"
FAKE_SWAPOFF

cat > "$FAKE_BIN/findmnt" <<'FAKE_FINDMNT'
#!/usr/bin/env bash

set -euo pipefail

printf 'findmnt' >> "$FAKE_COMMAND_LOG"
printf ' <%s>' "$@" >> "$FAKE_COMMAND_LOG"
printf '\n' >> "$FAKE_COMMAND_LOG"
FAKE_FINDMNT

cat > "$FAKE_BIN/systemctl" <<'FAKE_SYSTEMCTL'
#!/usr/bin/env bash

set -euo pipefail

printf 'systemctl' >> "$FAKE_COMMAND_LOG"
printf ' <%s>' "$@" >> "$FAKE_COMMAND_LOG"
printf '\n' >> "$FAKE_COMMAND_LOG"
FAKE_SYSTEMCTL

chmod +x \
    "$FAKE_BIN/id" \
    "$FAKE_BIN/df" \
    "$FAKE_BIN/fallocate" \
    "$FAKE_BIN/mkswap" \
    "$FAKE_BIN/chmod" \
    "$FAKE_BIN/swapon" \
    "$FAKE_BIN/swapoff" \
    "$FAKE_BIN/findmnt" \
    "$FAKE_BIN/systemctl"

run_installer() {
    PATH="$FAKE_BIN:$PATH" \
    FAKE_UID="${FAKE_UID:-0}" \
    FAKE_AVAIL_KIB="${FAKE_AVAIL_KIB:-3145728}" \
    FAKE_COMMAND_LOG="$COMMAND_LOG" \
    FAKE_SWAP_STATE="$SWAP_STATE" \
    SWAP_FILE="$SWAP_FILE" \
    SWAP_MARKER="$SWAP_MARKER" \
    FSTAB_PATH="$FSTAB_PATH" \
    MIN_FREE_KIB=3145728 \
    "$INSTALLER"
}

if FAKE_UID=1000 run_installer >/dev/null 2>&1; then
    fail "installer accepted a non-root user"
fi

[[ ! -e "$SWAP_FILE" ]] ||
    fail "non-root attempt created the swap file"

printf 'unmanaged\n' > "$SWAP_FILE"
if run_installer >/dev/null 2>&1; then
    fail "installer accepted an existing unmanaged swap file"
fi

[[ "$(cat "$SWAP_FILE")" == "unmanaged" ]] ||
    fail "installer modified an existing unmanaged swap file"

rm "$SWAP_FILE"

if FAKE_AVAIL_KIB=3000000 run_installer >/dev/null 2>&1; then
    fail "installer accepted less than 3 GiB free space"
fi

printf '%s none swap sw 0 0\n' "$SWAP_FILE" > "$FSTAB_PATH"
if run_installer >/dev/null 2>&1; then
    fail "installer accepted an existing fstab entry"
fi

: > "$FSTAB_PATH"

if ! run_installer >/dev/null; then
    fail "installer failed for a valid new swap file"
fi

[[ -f "$SWAP_FILE" ]] || fail "swap file was not created"
[[ "$(stat -c '%s' "$SWAP_FILE")" == "$((2 * 1024 * 1024 * 1024))" ]] ||
    fail "swap file is not 2 GiB"
[[ -f "$SWAP_MARKER" ]] || fail "management marker was not created"
assert_file_contains "$COMMAND_LOG" "chmod <0600> <$SWAP_FILE>"
assert_file_contains "$COMMAND_LOG" "mkswap <$SWAP_FILE>"
assert_file_contains "$COMMAND_LOG" "swapon <$SWAP_FILE>"
assert_file_contains "$COMMAND_LOG" "findmnt <--verify>"
assert_file_contains "$FSTAB_PATH" "$SWAP_FILE none swap sw 0 0"
[[ "$(cat "$EXISTING_SWAP")" == "existing-swap" ]] ||
    fail "installer modified the existing /www/swap"

if ! run_installer >/dev/null; then
    fail "installer was not idempotent for a managed active swap file"
fi

[[ "$(grep -Fc -- "$SWAP_FILE none swap sw 0 0" "$FSTAB_PATH")" == "1" ]] ||
    fail "installer duplicated the fstab entry"

printf 'PASS: OpenClaw swap installer contract is satisfied\n'
