$ErrorActionPreference = "Stop"
$sqlPath = Join-Path $PSScriptRoot "..\docker\postgres-init\10-least-privilege.sql"
if (-not (Test-Path -LiteralPath $sqlPath)) { throw "No se encontró la migración de roles." }

Get-Content -Raw -Encoding UTF8 -LiteralPath $sqlPath |
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U frio_polar -d frio_polar
if ($LASTEXITCODE -ne 0) { throw "No se pudo restringir el usuario de base de datos." }

$result = docker compose exec -T postgres psql -U postgres -d frio_polar -Atc `
    "SELECT rolname || '|' || rolsuper || '|' || (rolpassword IS NULL) FROM pg_authid WHERE rolname IN ('frio_polar','climapolar_app') ORDER BY rolname;"
if ($LASTEXITCODE -ne 0) { throw "No se pudo verificar la configuración de roles." }

Write-Host "Roles PostgreSQL configurados:"
Write-Host $result
Write-Host "climapolar_app debe figurar como no-superusuario; frio_polar debe figurar sin contraseña de red."
