param(
    [string]$PythonExecutable = '',
    [switch]$Live,
    [ValidateRange(1, 100)][int]$MaxProviderAttempts = 80
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$records = Join-Path $repoRoot 'docs/verification'
New-Item -ItemType Directory -Force $records | Out-Null
$startedAt = (Get-Date).ToUniversalTime().ToString('o')
$runId = [guid]::NewGuid().ToString()
$script:verificationStep = 'setup'
trap {
    [ordered]@{
        status = 'failed'; run_id = $runId; started_at = $startedAt
        recorded_at = (Get-Date).ToUniversalTime().ToString('o')
        failed_step = $script:verificationStep
        failure_type = $_.Exception.GetType().Name
        external_detection = 'not_established_by_failed_run'
    } | ConvertTo-Json | Set-Content (Join-Path $records 'summary.json') -Encoding UTF8
    throw
}
[ordered]@{status='running'; run_id=$runId; started_at=$startedAt} | ConvertTo-Json |
    Set-Content (Join-Path $records 'summary.json') -Encoding UTF8
if (-not $PythonExecutable) { $PythonExecutable = Join-Path $repoRoot 'backend/.venv/Scripts/python.exe' }
if (-not (Test-Path -LiteralPath $PythonExecutable)) { throw '먼저 backend에서 uv sync를 실행하거나 -PythonExecutable로 인터프리터를 지정하세요.' }

function Invoke-Recorded([string]$Directory, [string]$Executable, [string[]]$Arguments, [string]$LogName) {
    $script:verificationStep = $LogName
    Push-Location $Directory
    $savedPreference = $ErrorActionPreference
    try {
        # PowerShell 5 wraps harmless native stderr warnings as ErrorRecords.
        $ErrorActionPreference = 'Continue'
        & $Executable @Arguments 2>&1 | Tee-Object -FilePath (Join-Path $records $LogName)
        $commandExitCode = $LASTEXITCODE
        if ($commandExitCode -ne 0) { throw "$LogName failed with exit code $commandExitCode" }
    } finally { $ErrorActionPreference = $savedPreference; Pop-Location }
}

if (-not $env:GRADLE_USER_HOME -and $env:USERPROFILE) { $env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle' }
Invoke-Recorded (Join-Path $repoRoot 'backend') $PythonExecutable @('-m', 'pytest', '-q', '--junitxml=../docs/verification/backend.xml') 'backend.log'
Invoke-Recorded (Join-Path $repoRoot 'frontend') (Join-Path $repoRoot 'frontend/gradlew.bat') @('testDebugUnitTest', 'assembleDebug', 'assembleRelease', 'lintDebug', '--console=plain') 'android-current.log'
Invoke-Recorded (Join-Path $repoRoot 'backend') $PythonExecutable @('evaluation.py', '--mode', 'mock') 'evaluation.log'
if ($Live) {
    Invoke-Recorded (Join-Path $repoRoot 'backend') $PythonExecutable @('provider_smoke.py', '--live') 'provider-smoke.log'
    Invoke-Recorded (Join-Path $repoRoot 'backend') $PythonExecutable @('evaluation.py', '--mode', 'live', '--max-provider-attempts', "$MaxProviderAttempts") 'evaluation-live.log'
}

[xml]$androidResults = '<testsuites />'
$script:verificationStep = 'result_metadata'
$androidTotal = 0
$androidFailures = 0
Get-ChildItem -LiteralPath (Join-Path $repoRoot 'frontend/app/build/test-results/testDebugUnitTest') -Filter 'TEST-*.xml' | ForEach-Object {
    [xml]$suite = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
    $androidTotal += [int]$suite.testsuite.tests
    $androidFailures += [int]$suite.testsuite.failures + [int]$suite.testsuite.errors
    [void]$androidResults.DocumentElement.AppendChild($androidResults.ImportNode($suite.testsuite, $true))
}
$androidResults.Save((Join-Path $records 'android-tests.xml'))
[xml]$backendResults = Get-Content -LiteralPath (Join-Path $records 'backend.xml') -Raw -Encoding UTF8
[xml]$lint = Get-Content -LiteralPath (Join-Path $repoRoot 'frontend/app/build/reports/lint-results-debug.xml') -Raw -Encoding UTF8
Push-Location $repoRoot
try {
    $inventory = [ordered]@{ status = 'not_checked'; physical = 0; emulators = 0; unauthorized = 0 }
    $adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' } else { '' }
    if ($adb -and (Test-Path -LiteralPath $adb)) {
        $deviceLines = & $adb devices
        $inventory.status = 'read_only_inventory'
        foreach ($line in $deviceLines) {
            if ($line -match '^emulator-\S+\s+device$') { $inventory.emulators++ }
            elseif ($line -match '^\S+\s+device$') { $inventory.physical++ }
            elseif ($line -match '^\S+\s+unauthorized$') { $inventory.unauthorized++ }
        }
    }
    $summary = [ordered]@{
        status = 'passed'
        run_id = $runId
        started_at = $startedAt
        recorded_at = (Get-Date).ToUniversalTime().ToString('o')
        branch = (git branch --show-current)
        base_commit = (git rev-parse HEAD)
        uncommitted_changes = [bool](git status --porcelain)
        android_tests = $androidTotal
        android_failures = $androidFailures
        backend_tests = [int]$backendResults.testsuites.testsuite.tests
        backend_failures = [int]$backendResults.testsuites.testsuite.failures + [int]$backendResults.testsuites.testsuite.errors
        lint_warnings = @($lint.issues.issue | Where-Object { $_.severity -eq 'Warning' }).Count
        lint_errors = @($lint.issues.issue | Where-Object { $_.severity -eq 'Error' }).Count
        debug_build = 'passed'
        release_build = 'passed_unsigned'
        physical_device = 'not_verified_by_script'
        emulator = 'not_verified_by_script'
        connected_device_inventory = $inventory
        external_detection = $(if ($Live) { 'see live-results.json; inspect coverage and statuses' } else { 'not_measured; mock only' })
        env_tracked = [bool](git ls-files -- backend/.env)
    }
    $summary | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $records 'summary.json') -Encoding UTF8
    Copy-Item (Join-Path $repoRoot 'frontend/app/build/reports/evaluation/local-results.json') (Join-Path $records 'local-results.json') -Force
    @('frontend/app/build/outputs/apk/debug/app-debug.apk', 'frontend/app/build/outputs/apk/release/app-release-unsigned.apk') | ForEach-Object {
        [ordered]@{path=$_; sha256=(Get-FileHash -LiteralPath (Join-Path $repoRoot $_) -Algorithm SHA256).Hash}
    } | ConvertTo-Json -Depth 3 | Set-Content (Join-Path $records 'artifacts.json') -Encoding UTF8
    $summary | ConvertTo-Json -Depth 4
} finally { Pop-Location }
