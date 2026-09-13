#!/usr/bin/env bash
set -euo pipefail

usage() {
    printf 'Usage: %s [--root <directory>]\n' "$0" >&2
}

root=""
if [[ $# -gt 0 ]]; then
    if [[ $# -ne 2 || "$1" != "--root" ]]; then
        usage
        exit 2
    fi
    root="$2"
fi

violations=0

record_violation() {
    local label="$1"
    local file="$2"
    local line="$3"

    printf 'ERROR: %s detected at %s:%s\n' "$label" "$file" "$line" >&2
    violations=$((violations + 1))
}

scan_results() {
    local label="$1"
    local results="$2"
    local result
    local file
    local remainder
    local line

    while IFS= read -r result; do
        if [[ -z "$result" ]]; then
            continue
        fi
        file="${result%%:*}"
        remainder="${result#*:}"
        line="${remainder%%:*}"
        record_violation "$label" "$file" "$line"
    done <<<"$results"
}

scan_pattern() {
    local label="$1"
    local pattern="$2"
    local results

    if [[ "$scan_mode" == "root" ]]; then
        if (( ${#files[@]} == 0 )); then
            return
        fi
        results="$(grep -HnEIo -- "$pattern" "${files[@]}" 2>/dev/null || true)"
    else
        results="$(git grep -nEIo -e "$pattern" -- . 2>/dev/null || true)"
    fi

    scan_results "$label" "$results"
}

is_public_ipv4() {
    local address="$1"
    local a
    local b
    local c
    local d
    local octet

    IFS=. read -r a b c d <<<"$address"
    for octet in "$a" "$b" "$c" "$d"; do
        if [[ ! "$octet" =~ ^[0-9]{1,3}$ ]] || (( 10#$octet > 255 )); then
            return 1
        fi
    done

    a=$((10#$a))
    b=$((10#$b))
    c=$((10#$c))

    if (( a == 0 || a == 10 || a == 127 || a >= 224 )); then
        return 1
    fi
    if (( a == 100 && b >= 64 && b <= 127 )); then
        return 1
    fi
    if (( a == 169 && b == 254 )); then
        return 1
    fi
    if (( a == 172 && b >= 16 && b <= 31 )); then
        return 1
    fi
    if (( a == 192 && b == 168 )); then
        return 1
    fi
    if (( a == 192 && b == 0 && c == 0 )); then
        return 1
    fi
    if (( a == 192 && b == 0 && c == 2 )); then
        return 1
    fi
    if (( a == 198 && (b == 18 || b == 19) )); then
        return 1
    fi
    if (( a == 198 && b == 51 && c == 100 )); then
        return 1
    fi
    if (( a == 203 && b == 0 && c == 113 )); then
        return 1
    fi

    return 0
}

scan_public_ipv4() {
    local results
    local result
    local file
    local remainder
    local line
    local address

    if [[ "$scan_mode" == "root" ]]; then
        if (( ${#files[@]} == 0 )); then
            return
        fi
        results="$(grep -HnEIo -E '([0-9]{1,3}\.){3}[0-9]{1,3}' "${files[@]}" 2>/dev/null || true)"
    else
        results="$(git grep -nEIo -E '([0-9]{1,3}\.){3}[0-9]{1,3}' -- . 2>/dev/null || true)"
    fi

    while IFS= read -r result; do
        if [[ -z "$result" ]]; then
            continue
        fi
        file="${result%%:*}"
        remainder="${result#*:}"
        line="${remainder%%:*}"
        address="${remainder#*:}"
        if [[ -n "$line" ]] && is_public_ipv4 "$address"; then
            record_violation "public IPv4 address" "$file" "$line"
        fi
    done <<<"$results"
}

scan_environment_files() {
    local file
    local file_name

    for file in "${files[@]}"; do
        file_name="${file##*/}"
        case "$file_name" in
            .env|.env.*)
                if [[ "$file_name" != ".env.example" && "$file_name" != ".env.docker.example" ]]; then
                    record_violation "tracked environment file" "$file" "1"
                fi
                ;;
        esac
    done
}

scan_mode="root"
files=()
if [[ -n "$root" ]]; then
    if [[ ! -d "$root" ]]; then
        printf 'ERROR: scan root does not exist: %s\n' "$root" >&2
        exit 2
    fi
    mapfile -d '' -t files < <(find "$root" -type f -not -path '*/.git/*' -print0)
else
    scan_mode="repository"
    repo_root="$(git rev-parse --show-toplevel)"
    cd "$repo_root"
    mapfile -d '' -t files < <(git ls-files -z)
fi

scan_environment_files
scan_public_ipv4

patterns=(
    "Alibaba Cloud ECS instance ID|i-[0-9a-z]{16,}"
    "Alibaba Cloud access key|LTAI[0-9A-Za-z]{12,}"
    "AWS access key|AKIA[0-9A-Z]{16}"
    "GitHub token|gh[pousr]_[A-Za-z0-9]{30,}"
    "JWT-like token|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"
    "private key header|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----"
)

for entry in "${patterns[@]}"; do
    label="${entry%%|*}"
    pattern="${entry#*|}"
    scan_pattern "$label" "$pattern"
done

if (( violations > 0 )); then
    printf 'Sensitive information scan failed with %d finding(s).\n' "$violations" >&2
    exit 1
fi

printf 'Sensitive information scan passed.\n'
