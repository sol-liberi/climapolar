$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envPath = Join-Path $projectRoot ".env"
$sqlPath = Join-Path $projectRoot "docker/postgres-init/10-least-privilege.sql"

if (-not (Test-Path -LiteralPath $envPath)) {
    throw "No existe .env. Copiá .env.example y definí DB_PASSWORD antes de continuar."
}
if (-not (Test-Path -LiteralPath $sqlPath)) {
    throw "No se encontró la migración de roles."
}

Push-Location $projectRoot
try {
    # Recreate the database service first so POSTGRES_PASSWORD inside the
    # container is exactly the value currently declared in .env. PostgreSQL
    # itself does not update passwords when an existing volume is reused.
    docker compose up -d --force-recreate --wait --wait-timeout 90 postgres
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo recrear PostgreSQL con la configuración actual de .env."
    }

    docker compose exec -T postgres psql -X -v ON_ERROR_STOP=1 `
        -U frio_polar -d frio_polar `
        -f /docker-entrypoint-initdb.d/10-least-privilege.sql
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo restringir el usuario de base de datos."
    }

    # Force a TCP/SCRAM connection. A local socket could use trust and would
    # not prove that the desired password was actually assigned.
    # Send the shell program over stdin so PowerShell cannot split its quoted
    # SQL argument while invoking the native Docker CLI on Windows.
    $loginCheck = @'
PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5 exec psql -X -w -h 127.0.0.1 -U climapolar_app -d frio_polar -Atc "SELECT current_user"
'@
    $loginResult = $loginCheck | docker compose exec -T postgres sh -eu -s
    if ($LASTEXITCODE -ne 0 -or (($loginResult -join "`n").Trim() -ne "climapolar_app")) {
        throw "La contraseña actual de DB_PASSWORD no permite autenticar a climapolar_app."
    }

    $result = docker compose exec -T postgres psql -X -w -U postgres -d frio_polar -Atc `
        "SELECT rolname || '|' || rolsuper || '|' || (rolpassword IS NULL) FROM pg_authid WHERE rolname IN ('frio_polar','climapolar_app') ORDER BY rolname;"
    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo verificar la configuración de roles."
    }

    Write-Host "Roles PostgreSQL configurados y acceso de climapolar_app verificado."
    Write-Host $result
    Write-Host "climapolar_app debe figurar como no-superusuario; frio_polar debe figurar sin contraseña de red."
} finally {
    Pop-Location
}
