
$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Compose = Join-Path $Root "infrastructure/docker/docker-compose.yml"
$EnvFile = Join-Path $Root ".env"

docker compose --env-file $EnvFile -f $Compose ps

if ($LASTEXITCODE -ne 0) {
    throw "Unable to retrieve infrastructure status."
}

$Services = @(
    "stockpulse-mysql",
    "stockpulse-postgres",
    "stockpulse-kafka",
    "stockpulse-redis"
)

$Failed = $false

foreach ($Service in $Services) {
    $Status = docker inspect --format='{{.State.Health.Status}}' $Service 2>$null

    if ($Status -eq "healthy") {
        Write-Host "[PASS] $Service" -ForegroundColor Green
    }
    else {
        Write-Host "[FAIL] $Service : $Status" -ForegroundColor Red
        $Failed = $true
    }
}

if ($Failed) {
    throw "Infrastructure health verification failed."
}

Write-Host "All infrastructure components are healthy."
