param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$backendDir = Join-Path $root 'backend'
$frontendDir = Join-Path $root 'frontend'
$composeFile = Join-Path $root 'compose.e2e.yaml'
$backendProcess = $null
$databaseStarted = $false
$exitCode = 1

if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Set JAVA_HOME to a Java 21 JDK before running E2E tests.'
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue) -or
    -not (Get-Command mvn.cmd -ErrorAction SilentlyContinue) -or
    -not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
    throw 'Docker, Maven, and Node/npm are required for E2E tests.'
}
$edge = @('C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
    'C:\Program Files\Microsoft\Edge\Application\msedge.exe') | Where-Object { Test-Path $_ }
if (-not $edge) { throw 'Microsoft Edge is required for the Playwright msedge channel.' }
$occupied = Get-NetTCPConnection -State Listen -LocalPort 55432,18080,15173 -ErrorAction SilentlyContinue
if ($occupied) { throw 'E2E ports 55432, 18080, and 15173 must be free; existing services will not be reused.' }

$env:JAVA_HOME = $JavaHome
$env:PATH = "$(Join-Path $JavaHome 'bin');$env:PATH"
$env:AML_E2E_DB_PASSWORD = 'E2e-' + [guid]::NewGuid().ToString('N')
$env:AML_E2E_USERNAME = 'e2e_admin'
$env:AML_E2E_PASSWORD = 'E2e-' + [guid]::NewGuid().ToString('N')
$env:AML_E2E_RUN = 'isolated'
$env:AML_BACKEND_PROXY_TARGET = 'http://127.0.0.1:18080'
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $frontendDir '.playwright-browsers'
$env:NO_COLOR = $null
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:AML_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/aml_frontend_e2e'
$env:AML_DB_USER = 'aml_e2e'
$env:AML_DB_PASSWORD = $env:AML_E2E_DB_PASSWORD
$env:AML_BACKEND_PORT = '18080'
$env:AML_DEMO_USERNAME = $env:AML_E2E_USERNAME
$env:AML_DEMO_PASSWORD = $env:AML_E2E_PASSWORD
$env:AML_JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$env:AML_SCREENING_PROVIDER = 'MOCK'
$env:AML_YENTE_ENABLED = 'false'
$env:AML_FINERACT_ENABLED = 'false'

try {
    $ErrorActionPreference = 'Continue'
    $databaseStarted = $true
    docker compose --progress quiet -p aml-frontend-e2e -f $composeFile up -d --wait
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'Disposable PostgreSQL failed to start.' }

    Push-Location $backendDir
    try {
        $ErrorActionPreference = 'Continue'
        & mvn.cmd -B -DskipTests package *> (Join-Path $backendDir 'target/frontend-e2e-build.log')
        $ErrorActionPreference = 'Stop'
        if ($LASTEXITCODE -ne 0) { throw 'Backend package failed; inspect backend/target/frontend-e2e-build.log.' }
    } finally { Pop-Location }

    $jar = Join-Path $backendDir 'target/aml-platform-0.1.0-SNAPSHOT.jar'
    $backendProcess = Start-Process -FilePath (Join-Path $JavaHome 'bin/java.exe') `
        -ArgumentList @('-jar', $jar) -WorkingDirectory $backendDir -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $backendDir 'target/frontend-e2e-backend.log') `
        -RedirectStandardError (Join-Path $backendDir 'target/frontend-e2e-backend-error.log')
    $healthy = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($backendProcess.HasExited) { break }
        try {
            $health = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/actuator/health' -TimeoutSec 2
            if ($health.status -eq 'UP') { $healthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $healthy) { throw 'Isolated backend did not become healthy; inspect backend/target/frontend-e2e-backend.log.' }

    Push-Location $frontendDir
    try {
        $ErrorActionPreference = 'Continue'
        & npm.cmd run build
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
        & npm.cmd run test:e2e
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
    } finally { Pop-Location }
} finally {
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force
    }
    if ($databaseStarted) {
        $ErrorActionPreference = 'Continue'
        docker compose --progress quiet -p aml-frontend-e2e -f $composeFile down
    }
}

exit $exitCode
