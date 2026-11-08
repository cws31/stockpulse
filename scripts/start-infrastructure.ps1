
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Compose = Join-Path $Root "infrastructure/docker/docker-compose.yml"
$EnvFile = Join-Path $Root ".env"

if (!(Test-Path $EnvFile)) {
    throw ".env file not found."
}

docker compose --env-file $EnvFile -f $Compose up -d

if ($LASTEXITCODE -ne 0) {
    throw "Infrastructure startup failed."
}

Write-Host "StockPulse infrastructure started."
