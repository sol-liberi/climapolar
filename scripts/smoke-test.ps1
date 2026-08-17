param(
    [string]$BaseUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"
$curlCommand = Get-Command curl.exe -ErrorAction SilentlyContinue
if (-not $curlCommand) { $curlCommand = Get-Command curl -ErrorAction Stop }

function Assert-Status {
    param(
        [string]$Path,
        [int[]]$Expected
    )

    $nullDevice = if ($env:OS -eq "Windows_NT") { "NUL" } else { "/dev/null" }
    $statusText = & $curlCommand.Source --silent --show-error --output $nullDevice --write-out "%{http_code}" "$BaseUrl$Path"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar $Path" }
    $status = [int]$statusText
    if ($status -notin $Expected) {
        throw "$Path respondió $status; se esperaba $($Expected -join ' o ')."
    }
    Write-Host ("OK {0} -> {1}" -f $Path, $status)
}

Assert-Status -Path "/" -Expected 200
Assert-Status -Path "/trabajos" -Expected 200
Assert-Status -Path "/api/trabajos?page=0&size=9" -Expected 200
Assert-Status -Path "/api/trabajos?page=2147483647&size=24" -Expected 400
Assert-Status -Path "/no-existe" -Expected 404
Assert-Status -Path "/admin/trabajos" -Expected 302
Assert-Status -Path "/css/styles.css" -Expected 200

$headers = & $curlCommand.Source --silent --show-error --head "$BaseUrl/"
foreach ($requiredHeader in @("X-Content-Type-Options:", "X-Frame-Options:", "Content-Security-Policy:", "Referrer-Policy:")) {
    if (-not ($headers | Select-String -SimpleMatch $requiredHeader)) {
        throw "Falta el header de seguridad $requiredHeader"
    }
}

$portfolio = Invoke-RestMethod -Uri "$BaseUrl/api/trabajos?page=0&size=24" -Method Get
$mediaUrl = @($portfolio.content | ForEach-Object { $_.portada.url } | Where-Object { $_ -like "/media/*" } | Select-Object -First 1)
if ($mediaUrl.Count -gt 0) { Assert-Status -Path $mediaUrl[0] -Expected 200 }

Write-Host "Smoke test público completado correctamente."
