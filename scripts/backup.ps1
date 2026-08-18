param(
    [string]$Destination = (Join-Path $PSScriptRoot "..\backups")
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$destinationRoot = (New-Item -ItemType Directory -Path $Destination -Force).FullName
$backupRoot = Join-Path $destinationRoot "climapolar-$stamp"
$mediaRoot = Join-Path $backupRoot "media"

New-Item -ItemType Directory -Path $mediaRoot -Force | Out-Null

$appContainer = docker compose ps --all --quiet app
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($appContainer)) {
    throw "No se encontró el contenedor de la aplicación. Inicia el entorno antes de crear el respaldo."
}

$appState = docker inspect --format '{{.State.Running}}|{{.State.Paused}}' $appContainer
if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar el estado de la aplicación." }

$pausedByScript = $false
if ($appState -eq "true|false") {
    docker compose pause app | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "No se pudo pausar la aplicación para crear un respaldo consistente." }
    $pausedByScript = $true
}

try {
    docker compose exec -T postgres sh -c 'pg_dump -U postgres -d "$POSTGRES_DB" -Fc -f /tmp/climapolar-backup.dump'
    if ($LASTEXITCODE -ne 0) { throw "No se pudo generar el respaldo de PostgreSQL." }

    docker cp "frio-polar-db:/tmp/climapolar-backup.dump" (Join-Path $backupRoot "postgres.dump")
    if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar el respaldo de PostgreSQL." }

    docker cp "${appContainer}:/app/data/media/." $mediaRoot
    if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar el volumen de fotografías y videos." }
} finally {
    if ($pausedByScript) {
        docker compose unpause app | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "El respaldo terminó, pero no se pudo reanudar la aplicación. Ejecuta 'docker compose unpause app'."
        }

        $healthDeadline = (Get-Date).AddSeconds(60)
        do {
            $appHealth = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $appContainer
            if ($LASTEXITCODE -ne 0) { throw "No se pudo verificar la recuperación de la aplicación." }
            if ($appHealth -in @("healthy", "none")) { break }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $healthDeadline)

        if ($appHealth -notin @("healthy", "none")) {
            throw "La aplicación fue reanudada, pero no recuperó un estado saludable dentro de 60 segundos."
        }
    }
}

$dump = Join-Path $backupRoot "postgres.dump"
$mediaFiles = @(Get-ChildItem -LiteralPath $mediaRoot -Recurse -File)
$mediaBytes = ($mediaFiles | Measure-Object -Property Length -Sum).Sum

[pscustomobject]@{
    Backup = $backupRoot
    DatabaseSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $dump).Hash
    MediaFiles = $mediaFiles.Count
    MediaBytes = $mediaBytes
} | Format-List
