$script:DemoDeviceRoot = $PSScriptRoot
$script:DemoInstalled = @{}
$script:DemoAdb = $null

function Get-DemoSdkRoots {
    $bundled = Join-Path $script:DemoDeviceRoot '../../../toolchain/sdk'
    $standard = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    $adbOnPath = Get-Command adb.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    $pathSdk = if ($adbOnPath) { Split-Path (Split-Path $adbOnPath.Source -Parent) -Parent }
    $locationFile = Join-Path $script:DemoDeviceRoot 'sdk-location.txt'
    $downloadSdk = if (Test-Path -LiteralPath $locationFile -PathType Leaf) {
        $relativeSdk = (Get-Content -LiteralPath $locationFile -Raw -Encoding UTF8).Trim()
        if ($relativeSdk) {
            Join-Path (Join-Path $env:USERPROFILE 'Documents') $relativeSdk
        }
    }
    @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, $bundled, $standard, $pathSdk, $downloadSdk) |
        Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) } |
        ForEach-Object { (Resolve-Path -LiteralPath $_).Path } |
        Select-Object -Unique
}

function Get-DemoAdb {
    if ($script:DemoAdb) { return $script:DemoAdb }
    foreach ($sdk in Get-DemoSdkRoots) {
        $candidate = Join-Path $sdk 'platform-tools/adb.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            $script:DemoAdb = $candidate
            return $candidate
        }
    }
    throw 'ADB를 찾지 못했어요. Android Studio에서 Android SDK Platform-Tools를 설치해 주세요.'
}

function Invoke-DemoTool {
    param([string]$Tool, [string[]]$Arguments)

    $ErrorActionPreference = 'Continue'
    $output = & $Tool @Arguments 2>&1
    [pscustomobject]@{ Code = $LASTEXITCODE; Output = ($output -join [Environment]::NewLine) }
}

function Confirm-DemoBuildStep {
    param([string]$Tool, [string[]]$Arguments, [string]$Step)

    $result = Invoke-DemoTool -Tool $Tool -Arguments $Arguments
    if ($result.Code -ne 0) {
        throw "시연 앱을 준비하는 중 '$Step' 단계에서 오류가 났어요. Android SDK와 JDK 설치를 확인해 주세요."
    }
}

function Get-DemoDevices {
    $result = Invoke-DemoTool -Tool (Get-DemoAdb) -Arguments @('devices', '-l')
    if ($result.Code -ne 0) {
        throw 'ADB에서 기기 목록을 읽지 못했어요. USB 연결을 확인한 다음 새로 고침해 주세요.'
    }
    foreach ($line in ($result.Output -split '\r?\n')) {
        if ($line -notmatch '^([A-Za-z0-9_.-]+)\s+(device|unauthorized|offline)(?:\s|$)') { continue }
        $serial, $state = $Matches[1], $Matches[2]
        if ($serial -like 'emulator-*' -or $serial -like '*_adb-tls-*' -or $serial -like 'localhost*') { continue }
        $model = if ($line -match '\bmodel:(\S+)') { $Matches[1] -replace '_', ' ' } else { 'Android 기기' }
        $tail = $serial.Substring([Math]::Max(0, $serial.Length - 4))
        $status = switch ($state) {
            'unauthorized' { 'USB 디버깅 허용 필요' }
            'offline' { '연결 확인 필요' }
            default { '연결됨' }
        }
        [pscustomobject]@{ Serial = $serial; Label = "$model · $tail · $status"; Ready = $state -eq 'device' }
    }
}

function Get-DemoJdk {
    $bundledRoot = Join-Path $script:DemoDeviceRoot '../../../toolchain/jdk'
    $bundled = if (Test-Path -LiteralPath $bundledRoot -PathType Container) {
        Get-ChildItem -LiteralPath $bundledRoot -Directory -Filter 'jdk-*' |
            Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
    }
    $javacOnPath = Get-Command javac.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    $pathJdk = if ($javacOnPath) { Split-Path (Split-Path $javacOnPath.Source -Parent) -Parent }
    $studioJdk = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
    foreach ($candidate in @($env:JAVA_HOME, $studioJdk, $pathJdk) + @($bundled)) {
        if (!$candidate) { continue }
        $bin = Join-Path $candidate 'bin'
        if ((Test-Path -LiteralPath (Join-Path $bin 'javac.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $bin 'jar.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $bin 'keytool.exe') -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw 'Java JDK를 찾지 못했어요. Android Studio 또는 JDK 17 이상을 설치하고 JAVA_HOME을 확인해 주세요.'
}

function Get-DemoAndroidBuildTools {
    foreach ($sdk in Get-DemoSdkRoots) {
        $platforms = Join-Path $sdk 'platforms'
        $buildTools = Join-Path $sdk 'build-tools'
        if (!(Test-Path -LiteralPath $platforms) -or !(Test-Path -LiteralPath $buildTools)) { continue }
        $platform = Get-ChildItem -LiteralPath $platforms -Directory |
            Where-Object { $_.Name -match '^android-(\d+)$' -and [int]$Matches[1] -ge 35 -and
                (Test-Path -LiteralPath (Join-Path $_.FullName 'android.jar') -PathType Leaf) } |
            Sort-Object { [int]($_.Name -replace '^android-', '') } -Descending | Select-Object -First 1
        $tools = Get-ChildItem -LiteralPath $buildTools -Directory |
            Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and
                (Test-Path -LiteralPath (Join-Path $_.FullName 'aapt.exe') -PathType Leaf) -and
                (Test-Path -LiteralPath (Join-Path $_.FullName 'd8.bat') -PathType Leaf) -and
                (Test-Path -LiteralPath (Join-Path $_.FullName 'zipalign.exe') -PathType Leaf) -and
                (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat') -PathType Leaf) } |
            Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
        if ($platform -and $tools) {
            return [pscustomobject]@{ Jar = Join-Path $platform.FullName 'android.jar'; Bin = $tools.FullName }
        }
    }
    throw 'Android SDK Platform 35 이상과 Android SDK Build-Tools를 설치해 주세요.'
}

function Get-DemoApk {
    $packagedApk = Join-Path $script:DemoDeviceRoot 'cookiepatrol-demo-sender.apk'
    if (Test-Path -LiteralPath $packagedApk -PathType Leaf) { return $packagedApk }

    $android = Join-Path $script:DemoDeviceRoot 'android'
    $manifest = Join-Path $android 'AndroidManifest.xml'
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $android 'src') -Filter '*.java' -Recurse -File |
        Sort-Object FullName | Select-Object -ExpandProperty FullName)
    $inputs = @($manifest, (Join-Path $script:DemoDeviceRoot 'DemoDevice.ps1')) + $sources
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $signature = ($inputs | ForEach-Object {
            [BitConverter]::ToString($sha256.ComputeHash([IO.File]::ReadAllBytes($_)))
        }) -join ':'
    } finally {
        $sha256.Dispose()
    }
    $cache = Join-Path $script:DemoDeviceRoot '.build'
    $null = New-Item -Path $cache -ItemType Directory -Force
    $apk = Join-Path $cache 'cookiepatrol-demo-sender.apk'
    $stamp = Join-Path $cache 'sources.txt'
    $keystore = Join-Path $cache 'demo-debug.keystore'
    try {
        $lock = [IO.File]::Open((Join-Path $cache 'build.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch [IO.IOException] {
        throw '다른 시연 창에서 앱을 준비 중이에요. 잠시 후 다시 보내주세요.'
    }
    try {
        if ((Test-Path -LiteralPath $apk -PathType Leaf) -and
            (Test-Path -LiteralPath $keystore -PathType Leaf) -and
            (Test-Path -LiteralPath $stamp -PathType Leaf) -and
            ((Get-Content -LiteralPath $stamp -Raw).Trim() -eq $signature)) {
            return $apk
        }
        $jdk = Get-DemoJdk
        $sdk = Get-DemoAndroidBuildTools
        $stage = Join-Path $cache ('stage-' + [guid]::NewGuid().ToString('N'))
        $classes = Join-Path $stage 'classes'
        $dex = Join-Path $stage 'dex'
        $null = New-Item -Path $classes, $dex -ItemType Directory -Force
        $classesJar = Join-Path $stage 'classes.jar'
        $unsigned = Join-Path $stage 'unsigned.apk'
        $aligned = Join-Path $stage 'aligned.apk'
        $signed = Join-Path $stage 'signed.apk'
        $previousJava = $env:JAVA_HOME
        try {
            $env:JAVA_HOME = $jdk
            $jdkBin = Join-Path $jdk 'bin'
            if (!(Test-Path -LiteralPath $keystore -PathType Leaf)) {
                Confirm-DemoBuildStep -Tool (Join-Path $jdkBin 'keytool.exe') -Arguments @(
                    '-genkeypair', '-keystore', $keystore, '-alias', 'cookiepatrol-demo',
                    '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-storetype', 'PKCS12',
                    '-storepass', 'android', '-keypass', 'android', '-dname', 'CN=CookiePatrol Demo,O=CookiePatrol,C=KR', '-noprompt'
                ) -Step '시연용 서명 키 준비'
            }
            Confirm-DemoBuildStep -Tool (Join-Path $jdkBin 'javac.exe') -Arguments (
                @('--release', '8', '-encoding', 'UTF-8', '-classpath', $sdk.Jar, '-d', $classes) + $sources
            ) -Step 'Java 컴파일'
            Confirm-DemoBuildStep -Tool (Join-Path $jdkBin 'jar.exe') -Arguments @(
                'cf', $classesJar, '-C', $classes, '.'
            ) -Step '클래스 묶기'
            Confirm-DemoBuildStep -Tool (Join-Path $sdk.Bin 'd8.bat') -Arguments @(
                '--lib', $sdk.Jar, '--min-api', '30', '--output', $dex, $classesJar
            ) -Step 'Android 실행 파일 만들기'
            Confirm-DemoBuildStep -Tool (Join-Path $sdk.Bin 'aapt.exe') -Arguments @(
                'package', '-f', '-M', $manifest, '-I', $sdk.Jar, '-F', $unsigned
            ) -Step 'APK 패키징'
            Confirm-DemoBuildStep -Tool (Join-Path $jdkBin 'jar.exe') -Arguments @(
                'uf', $unsigned, '-C', $dex, 'classes.dex'
            ) -Step 'APK 실행 파일 넣기'
            Confirm-DemoBuildStep -Tool (Join-Path $sdk.Bin 'zipalign.exe') -Arguments @(
                '-f', '4', $unsigned, $aligned
            ) -Step 'APK 정렬'
            Confirm-DemoBuildStep -Tool (Join-Path $sdk.Bin 'apksigner.bat') -Arguments @(
                'sign', '--ks', $keystore, '--ks-key-alias', 'cookiepatrol-demo', '--ks-pass', 'pass:android',
                '--key-pass', 'pass:android', '--out', $signed, $aligned
            ) -Step 'APK 서명'
            Move-Item -LiteralPath $signed -Destination $apk -Force
            Set-Content -LiteralPath $stamp -Value $signature -Encoding Ascii
        } finally {
            $env:JAVA_HOME = $previousJava
            $resolvedStage = Resolve-Path -LiteralPath $stage -ErrorAction SilentlyContinue
            $resolvedCache = (Resolve-Path -LiteralPath $cache).Path.TrimEnd('\') + '\'
            if ($resolvedStage -and $resolvedStage.Path.StartsWith($resolvedCache, [StringComparison]::OrdinalIgnoreCase)) {
                Remove-Item -LiteralPath $resolvedStage.Path -Recurse -Force
            }
        }
        return $apk
    } finally {
        $lock.Dispose()
    }
}

function Initialize-DemoBackendRoute {
    param([string]$Adb, [string]$Serial)

    $routes = Invoke-DemoTool -Tool $Adb -Arguments @('-s', $Serial, 'reverse', '--list')
    if ($routes.Code -ne 0 -or $routes.Output -match '(?m)(?:^|\s)tcp:18000(?=\s|$)') { return }
    try {
        $request = [Net.WebRequest]::Create('http://127.0.0.1:8000/health')
        $request.Timeout = 2000
        $request.Proxy = $null
        $request.AllowAutoRedirect = $false
        $health = $request.GetResponse()
        try {
            if ([int]$health.StatusCode -eq 200) {
                $null = Invoke-DemoTool -Tool $Adb -Arguments @('-s', $Serial, 'reverse', '--no-rebind', 'tcp:18000', 'tcp:8000')
            }
        } finally {
            $health.Dispose()
        }
    } catch {
        # Local-only examples also run when the PC backend is off.
    }
}

function Initialize-DemoDevice {
    param(
        [Parameter(Mandatory = $true)][string]$Serial
    )

    $selected = Get-DemoDevices | Where-Object { $_.Serial -ceq $Serial } | Select-Object -First 1
    if (!$selected -or !$selected.Ready) {
        throw '선택한 기기가 연결되어 있지 않아요. USB 연결과 USB 디버깅 허용 상태를 확인해 주세요.'
    }
    $adb = Get-DemoAdb
    $prefix = @('-s', $Serial)
    $api = Invoke-DemoTool -Tool $adb -Arguments ($prefix + @('shell', 'getprop', 'ro.build.version.sdk'))
    if ($api.Code -ne 0 -or $api.Output.Trim() -notmatch '^[0-9]+$') {
        throw '기기의 Android 버전을 읽지 못했어요. USB 연결을 확인해 주세요.'
    }
    $apiLevel = [int]$api.Output.Trim()
    if ($apiLevel -lt 30) { throw '시연 도구는 Android 11 이상에서 사용할 수 있어요.' }
    $main = Invoke-DemoTool -Tool $adb -Arguments ($prefix + @('shell', 'pm', 'path', 'com.cookiepatrol'))
    if ($main.Code -ne 0 -or $main.Output -notmatch '(?m)^package:') {
        throw '기기에 쿠키패트롤 앱을 설치하고 문자·카톡 알림 감시를 켜주세요.'
    }
    $listeners = Invoke-DemoTool -Tool $adb -Arguments (
        $prefix + @('shell', 'settings', 'get', 'secure', 'enabled_notification_listeners')
    )
    if ($listeners.Code -eq 0 -and $listeners.Output -notmatch '(?m)(?:^|:)com\.cookiepatrol/') {
        throw '기기에서 쿠키패트롤의 문자·카톡 알림 감시를 먼저 켜주세요.'
    }
    $apk = Get-DemoApk
    $generation = (Get-Item -LiteralPath $apk).LastWriteTimeUtc.Ticks
    if ($script:DemoInstalled[$Serial] -ne $generation) {
        $install = Invoke-DemoTool -Tool $adb -Arguments ($prefix + @('install', '-r', $apk))
        if ($install.Code -ne 0 -or $install.Output -notmatch '(?m)^Success\s*$') {
            if ($install.Output -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
                throw '기기에 서명이 다른 쿠키패트롤 시연 앱이 있어요. 기기에서 기존 시연 앱을 삭제한 뒤 다시 보내주세요.'
            }
            throw '기기에 시연 앱을 설치하지 못했어요. 기기 화면의 설치 확인 및 USB 연결을 확인해 주세요.'
        }
        if ($apiLevel -ge 33) {
            $permission = Invoke-DemoTool -Tool $adb -Arguments (
                $prefix + @('shell', 'pm', 'grant', 'com.cookiepatrol.demosender', 'android.permission.POST_NOTIFICATIONS')
            )
            if ($permission.Code -ne 0) { throw '기기 설정에서 쿠키패트롤 시연 앱의 알림을 허용해 주세요.' }
        }
        $script:DemoInstalled[$Serial] = $generation
    }
    Initialize-DemoBackendRoute -Adb $adb -Serial $Serial
    return $adb
}

function Send-DemoMessage {
    param(
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Body,
        [ValidatePattern('(?-i:\A[0-9A-F]{8}\z)')][string]$RunId
    )

    if ([string]::IsNullOrWhiteSpace($Body) -or $Body.Length -gt 2000) {
        throw '메시지는 1자 이상, 2,000자 이하로 입력해 주세요.'
    }
    $adb = Initialize-DemoDevice -Serial $Serial
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Body))
    $arguments = @(
        '-s', $Serial, 'shell', 'am', 'broadcast', '-a', 'com.cookiepatrol.demosender.SHOW_MESSAGE',
        '-n', 'com.cookiepatrol.demosender/.DemoMessageReceiver', '--es', 'body64', $encoded
    )
    if ($RunId) { $arguments += @('--es', 'run_id', $RunId) }
    $result = Invoke-DemoTool -Tool $adb -Arguments $arguments
    if ($result.Code -eq 0 -and $result.Output -match 'result=-1,\s*data="(POSTED|ALREADY_POSTED)"') {
        return $Matches[1]
    }
    if ($result.Output -match 'NOTIFICATIONS_DISABLED') {
        throw '기기 설정에서 쿠키패트롤 시연 앱의 알림을 허용해 주세요.'
    }
    if ($result.Output -match 'INVALID_MESSAGE') { throw '입력한 메시지를 기기에서 읽을 수 없어요.' }
    $script:DemoInstalled.Remove($Serial)
    throw '기기가 데모 메시지 수신을 확인하지 않았어요. USB 연결을 확인하고 다시 보내주세요.'
}

function Get-DemoVerification {
    param(
        [Parameter(Mandatory = $true)][ValidatePattern('\A[A-Za-z0-9_.-]+\z')][string]$Serial,
        [Parameter(Mandatory = $true)][ValidatePattern('(?-i:\A[0-9A-F]{8}\z)')][string]$RunId
    )

    $result = Invoke-DemoTool -Tool (Get-DemoAdb) -Arguments @(
        '-s', $Serial, 'logcat', '-d', '-v', 'raw', '-s', 'CookiePatrolDemo:I'
    )
    if ($result.Code -ne 0) {
        throw '휴대폰의 검사 결과를 읽지 못했어요. USB 연결과 USB 디버깅 상태를 확인해 주세요.'
    }

    $score = $null
    $stage = $null
    $completed = $false
    $warningTags = @{}
    foreach ($line in ($result.Output -split '\r?\n')) {
        if ($line -cmatch "^$RunId LOCAL ([0-9]+)$") {
            $parsed = 0
            if ([int]::TryParse($Matches[1], [Globalization.NumberStyles]::None,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$parsed)) {
                $score = $parsed
            }
        } elseif ($line -cmatch "^$RunId WARNING ([23]) ([0-9a-fA-F]{64})$") {
            $currentStage = [int]$Matches[1]
            if ($null -eq $stage -or $currentStage -gt $stage) { $stage = $currentStage }
            $warningTags[$Matches[2]] = $true
        } elseif ($line -ceq "$RunId DONE") {
            $completed = $true
        }
    }
    [pscustomobject]@{ Score = $score; Stage = $stage; WarningCount = $warningTags.Count; Completed = $completed }
}
