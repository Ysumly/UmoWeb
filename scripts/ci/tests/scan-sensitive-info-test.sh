#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
scanner="$repo_root/scripts/ci/scan-sensitive-info.sh"
fixture_root="$(mktemp -d)"

cleanup() {
    rm -rf "$fixture_root"
}
trap cleanup EXIT

assert_passes() {
    local case_name="$1"
    local root="$2"

    if ! bash "$scanner" --root "$root" >/dev/null 2>&1; then
        printf 'FAIL: expected scanner to pass: %s\n' "$case_name" >&2
        exit 1
    fi
}

assert_fails() {
    local case_name="$1"
    local root="$2"

    if bash "$scanner" --root "$root" >/dev/null 2>&1; then
        printf 'FAIL: expected scanner to reject: %s\n' "$case_name" >&2
        exit 1
    fi
}

safe_root="$fixture_root/safe"
mkdir -p "$safe_root"
cat >"$safe_root/safe.txt" <<'EOF'
127.0.0.1
10.20.30.40
172.30.0.10
192.168.1.20
192.0.2.10
198.51.100.20
203.0.113.30
admin123
change-me-in-production-this-is-a-default-only
sk-dummy-placeholder
replace-with-a-long-random-database-password
EOF
assert_passes "reserved addresses and documented placeholders" "$safe_root"

public_ip_root="$fixture_root/public-ip"
mkdir -p "$public_ip_root"
printf '%s\n' "8.$(printf '8.%s.%s' '8' '8')" >"$public_ip_root/value.txt"
assert_fails "public IPv4 address" "$public_ip_root"

ecs_root="$fixture_root/ecs"
mkdir -p "$ecs_root"
printf 'i-%s\n' "2vcb31oaqpubbia5lol1" >"$ecs_root/value.txt"
assert_fails "ECS instance ID" "$ecs_root"

access_key_root="$fixture_root/access-key"
mkdir -p "$access_key_root"
printf 'LTAI%s\n' "1234567890ABCDEF" >"$access_key_root/value.txt"
assert_fails "Alibaba Cloud access key" "$access_key_root"

aws_root="$fixture_root/aws"
mkdir -p "$aws_root"
printf 'AKIA%s\n' "1234567890ABCDEF" >"$aws_root/value.txt"
assert_fails "AWS access key" "$aws_root"

github_root="$fixture_root/github"
mkdir -p "$github_root"
printf 'ghp_%s\n' "1234567890abcdefghijklmnopqrstuvwxyz" >"$github_root/value.txt"
assert_fails "GitHub token" "$github_root"

private_key_root="$fixture_root/private-key"
mkdir -p "$private_key_root"
printf -- '-----BEGIN %s PRIVATE KEY-----\n' "RSA" >"$private_key_root/value.txt"
assert_fails "private key header" "$private_key_root"

jwt_root="$fixture_root/jwt"
mkdir -p "$jwt_root"
printf 'eyJ%s.%s.%s\n' \
    "abcdefghijklmnop" \
    "abcdefghijklmnop" \
    "abcdefghijklmnop" >"$jwt_root/value.txt"
assert_fails "JWT-like token" "$jwt_root"

env_root="$fixture_root/env"
mkdir -p "$env_root"
printf 'SAFE=value\n' >"$env_root/.env.docker"
assert_fails "tracked environment file" "$env_root"

example_root="$fixture_root/env-examples"
mkdir -p "$example_root"
printf 'SAFE=value\n' >"$example_root/.env.example"
printf 'SAFE=value\n' >"$example_root/.env.docker.example"
assert_passes "environment examples" "$example_root"

printf 'Sensitive information scanner tests passed\n'
