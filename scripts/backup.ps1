param(
    [string]$Destination = (Join-Path $PSScriptRoot "..\backups")
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$destinationRoot = (New-Item -ItemType Directory -Path $Destination -Force).FullName
$backupRoot = Join-Path $destinationRoot "climapolar-$stamp"
$mediaRoot = Join-Path $backupRoot "media"

New-Item -ItemType Directory -Path $mediaRoot -Force | Out-Null

docker compose exec -T postgres sh -c 'pg_dump -U postgres -d "$POSTGRES_DB" -Fc -f /tmp/climapolar-backup.dump'
if ($LASTEXITCODE -ne 0) { throw "No se pudo generar el respaldo de PostgreSQL." }

docker cp "frio-polar-db:/tmp/climapolar-backup.dump" (Join-Path $backupRoot "postgres.dump")
if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar el respaldo de PostgreSQL." }

docker cp "frio-polar-app:/app/data/media/." $mediaRoot
if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar el volumen de fotografías y videos." }

$dump = Join-Path $backupRoot "postgres.dump"
$mediaFiles = @(Get-ChildItem -LiteralPath $mediaRoot -Recurse -File)
$mediaBytes = ($mediaFiles | Measure-Object -Property Length -Sum).Sum

[pscustomobject]@{
    Backup = $backupRoot
    DatabaseSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $dump).Hash
    MediaFiles = $mediaFiles.Count
    MediaBytes = $mediaBytes
} | Format-List
