param(
    [switch]$Restart
)

$ErrorActionPreference = "Stop"
$envPath = Join-Path $PSScriptRoot "..\.env"
if (-not (Test-Path -LiteralPath $envPath)) {
    throw "No existe .env. Copiá .env.example antes de rotar la contraseña."
}

$randomBytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $generator.GetBytes($randomBytes)
} finally {
    $generator.Dispose()
}

$newPassword = [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$lines = @(Get-Content -LiteralPath $envPath)
$replaced = $false
$updated = foreach ($line in $lines) {
    if ($line -match '^ADMIN_PASSWORD=') {
        $replaced = $true
        "ADMIN_PASSWORD=$newPassword"
    } else {
        $line
    }
}
if (-not $replaced) { $updated += "ADMIN_PASSWORD=$newPassword" }

Set-Content -LiteralPath $envPath -Value $updated -Encoding UTF8
$newPassword = $null
[Array]::Clear($randomBytes, 0, $randomBytes.Length)

if ($Restart) {
    docker compose up -d --force-recreate app
    if ($LASTEXITCODE -ne 0) { throw "La clave cambió en .env, pero no se pudo recrear la aplicación." }
}

Write-Host "Contraseña administrativa rotada dentro de .env. No se imprimió en pantalla."
Write-Host "Guardala desde ese archivo en el gestor de contraseñas del cliente y eliminá cualquier copia temporal."
