[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$envFile = Join-Path $root ".env.docker"
$generated = $false

function New-RandomHex {
    param([int]$ByteCount)

    $bytes = New-Object byte[] $ByteCount
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

function Read-EnvValue {
    param(
        [string]$Path,
        [string]$Name
    )

    $line = Get-Content -LiteralPath $Path |
        Where-Object { $_ -match "^$([Regex]::Escape($Name))=" } |
        Select-Object -First 1
    if (-not $line) {
        return $null
    }
    return $line.Substring($line.IndexOf("=") + 1)
}

if (-not (Test-Path -LiteralPath $envFile)) {
    $port = 8080
    $subnet = "172.30.0.0/24"
    $frontendIp = "172.30.0.10"
    $adminUser = "admin"
    $adminPassword = "Umo-" + (New-RandomHex 12)
    $lines = @(
        "APP_PORT=$port"
        "MYSQL_DATABASE=umo_blog"
        "MYSQL_USER=umo"
        "MYSQL_PASSWORD=$(New-RandomHex 24)"
        "MYSQL_ROOT_PASSWORD=$(New-RandomHex 24)"
        "JWT_SECRET=$(New-RandomHex 48)"
        "INIT_ADMIN_USER=$adminUser"
        "INIT_ADMIN_PASS=$adminPassword"
        "CORS_ALLOWED_ORIGINS=http://localhost:$port"
        "FRONTEND_IP=$frontendIp"
        "TRUSTED_PROXIES=$frontendIp/32"
        "DOCKER_SUBNET=$subnet"
        "VITE_ADMIN_PATH=/secret-admin"
    )
    [IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))
    $generated = $true
}

$resolvedEnvFile = (Resolve-Path -LiteralPath $envFile).Path

Push-Location $root
try {
    & docker compose --env-file $resolvedEnvFile up -d --build --wait
    $composeExitCode = $LASTEXITCODE
    if ($composeExitCode -ne 0) {
        & docker compose --env-file $resolvedEnvFile logs --tail 100
        throw "docker compose up failed with exit code $composeExitCode"
    }
}
finally {
    Pop-Location
}

$port = Read-EnvValue -Path $resolvedEnvFile -Name "APP_PORT"
$adminUser = Read-EnvValue -Path $resolvedEnvFile -Name "INIT_ADMIN_USER"
$adminPassword = Read-EnvValue -Path $resolvedEnvFile -Name "INIT_ADMIN_PASS"

Write-Host ""
Write-Host "UmoWeb is running:"
Write-Host "  Site:  http://localhost:$port"
Write-Host "  Admin: http://localhost:$port/secret-admin/login"
Write-Host "  User:  $adminUser"
if ($generated) {
    Write-Host "  Pass:  $adminPassword"
    Write-Host "  Credentials were written to .env.docker"
} else {
    Write-Host "  Pass:  stored in .env.docker"
}
