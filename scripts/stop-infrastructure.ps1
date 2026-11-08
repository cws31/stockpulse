
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Compose = Join-Path $Root "infrastructure/docker/docker-compose.yml"
$EnvFile = Join-Path $Root ".env"

docker compose --env-file $EnvFile -f $Compose down

if ($LASTEXITCODE -ne 0) {
    throw "Infrastructure shutdown failed."
}

Write-Host "Infrastructure stopped. Volumes retained."
