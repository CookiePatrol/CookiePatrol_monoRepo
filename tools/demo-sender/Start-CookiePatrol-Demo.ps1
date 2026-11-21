param(
    [switch]$ListDevices,
    [string]$Preset,
    [string]$DeviceSerial
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'DemoDevice.ps1')
[object[]]$presets = ConvertFrom-Json -InputObject (
    Get-Content -LiteralPath (Join-Path $PSScriptRoot 'presets.json') -Raw -Encoding UTF8
)

if ($ListDevices) {
    Get-DemoDevices
    return
}

if ($Preset) {
    $example = $presets | Where-Object { $_.id -eq $Preset } | Select-Object -First 1
    if (-not $example) { throw "없는 시연 예제입니다. $Preset" }
    if (-not $DeviceSerial) {
        $ready = @(Get-DemoDevices | Where-Object Ready)
        if ($ready.Count -ne 1) { throw 'USB 기기 한 대를 연결하거나 -DeviceSerial을 지정해주세요.' }
        $DeviceSerial = $ready[0].Serial
    }
    $result = Send-DemoMessage -Serial $DeviceSerial -Body $example.text
    if ($result -eq 'ALREADY_POSTED') {
        Write-Output '방금 보낸 같은 메시지는 한 번만 전송했습니다.'
    } else {
        Write-Output '시연 메시지 알림을 보냈습니다.'
    }
    return
}

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[Windows.Forms.Application]::EnableVisualStyles()

$ink = [Drawing.Color]::FromArgb(21, 39, 53)
$muted = [Drawing.Color]::FromArgb(64, 84, 99)
$accent = [Drawing.Color]::FromArgb(29, 85, 106)
$font = [Drawing.Font]::new('Malgun Gothic', 11)

$form = [Windows.Forms.Form]::new()
$form.Text = '쿠키패트롤 시연 도구'
$form.StartPosition = 'CenterScreen'
$form.ClientSize = [Drawing.Size]::new(744, 655)
$form.MinimumSize = [Drawing.Size]::new(620, 590)
$form.AutoScaleMode = 'Dpi'
$form.BackColor = [Drawing.Color]::White
$form.ForeColor = $ink
$form.Font = $font

$page = [Windows.Forms.TableLayoutPanel]::new()
$page.Dock = 'Fill'
$page.Padding = [Windows.Forms.Padding]::new(26, 20, 26, 16)
$page.ColumnCount = 1
$page.RowCount = 8
foreach ($height in 70, 51, 50, 47) {
    [void]$page.RowStyles.Add([Windows.Forms.RowStyle]::new('Absolute', $height))
}
[void]$page.RowStyles.Add([Windows.Forms.RowStyle]::new('Percent', 100))
[void]$page.RowStyles.Add([Windows.Forms.RowStyle]::new('Absolute', 65))
[void]$page.RowStyles.Add([Windows.Forms.RowStyle]::new('Absolute', 56))
[void]$page.RowStyles.Add([Windows.Forms.RowStyle]::new('Absolute', 43))
$form.Controls.Add($page)

$header = [Windows.Forms.TableLayoutPanel]::new()
$header.Dock = 'Fill'
$header.RowCount = 2
$header.ColumnCount = 1
$header.Margin = [Windows.Forms.Padding]::new(0)
[void]$header.RowStyles.Add([Windows.Forms.RowStyle]::new('Absolute', 40))
[void]$header.RowStyles.Add([Windows.Forms.RowStyle]::new('Percent', 100))
$heading = [Windows.Forms.Label]::new()
$heading.Text = '쿠키패트롤 시연 메시지'
$heading.Font = [Drawing.Font]::new('Malgun Gothic', 20, [Drawing.FontStyle]::Bold)
$heading.Dock = 'Fill'
$subtitle = [Windows.Forms.Label]::new()
$subtitle.Text = 'USB로 연결한 안드로이드 기기에 시연용 메시지 알림을 보내요.'
$subtitle.Dock = 'Fill'
$subtitle.ForeColor = $muted
$header.Controls.Add($heading, 0, 0)
$header.Controls.Add($subtitle, 0, 1)
$page.Controls.Add($header, 0, 0)

function New-FieldRow($Caption, $Field, $ExtraButton) {
    $row = [Windows.Forms.TableLayoutPanel]::new()
    $row.Dock = 'Fill'
    $row.Margin = [Windows.Forms.Padding]::new(0)
    $row.RowCount = 1
    $row.ColumnCount = $(if ($ExtraButton) { 3 } else { 2 })
    [void]$row.ColumnStyles.Add([Windows.Forms.ColumnStyle]::new('Absolute', 98))
    [void]$row.ColumnStyles.Add([Windows.Forms.ColumnStyle]::new('Percent', 100))
    $label = [Windows.Forms.Label]::new()
    $label.Text = $Caption
    $label.Dock = 'Fill'
    $label.TextAlign = 'MiddleLeft'
    $label.Margin = [Windows.Forms.Padding]::new(0)
    $Field.Dock = 'Fill'
    $Field.Margin = [Windows.Forms.Padding]::new(0, 9, 0, 5)
    $Field.AccessibleName = $Caption
    $row.Controls.Add($label, 0, 0)
    $row.Controls.Add($Field, 1, 0)
    if ($ExtraButton) {
        [void]$row.ColumnStyles.Add([Windows.Forms.ColumnStyle]::new('Absolute', 112))
        $ExtraButton.Dock = 'Fill'
        $ExtraButton.Margin = [Windows.Forms.Padding]::new(10, 5, 0, 7)
        $row.Controls.Add($ExtraButton, 2, 0)
    }
    return $row
}

$deviceList = [Windows.Forms.ComboBox]::new()
$deviceList.DropDownStyle = 'DropDownList'
$refreshButton = [Windows.Forms.Button]::new()
$refreshButton.Text = '새로고침'
$page.Controls.Add((New-FieldRow '연결 기기' $deviceList $refreshButton), 0, 1)

$presetList = [Windows.Forms.ComboBox]::new()
$presetList.DropDownStyle = 'DropDownList'
foreach ($example in $presets) { [void]$presetList.Items.Add($example.label) }
$randomButton = [Windows.Forms.Button]::new()
$randomButton.Text = '랜덤 예제'
$randomButton.Enabled = $presets.Count -gt 1
$page.Controls.Add((New-FieldRow '시연 예제' $presetList $randomButton), 0, 2)

$expectation = [Windows.Forms.Label]::new()
$expectation.Dock = 'Fill'
$expectation.TextAlign = 'MiddleLeft'
$expectation.ForeColor = $muted
$page.Controls.Add($expectation, 0, 3)

$message = [Windows.Forms.TextBox]::new()
$message.AccessibleName = '메시지 내용'
$message.Multiline = $true
$message.AcceptsReturn = $true
$message.MaxLength = 2000
$message.ScrollBars = 'Vertical'
$message.Dock = 'Fill'
$message.Margin = [Windows.Forms.Padding]::new(0, 0, 0, 5)
$page.Controls.Add($message, 0, 4)

$verification = [Windows.Forms.Label]::new()
$verification.Dock = 'Fill'
$verification.TextAlign = 'MiddleLeft'
$verification.AccessibleName = '쿠키패트롤 검사 결과'
$verification.Margin = [Windows.Forms.Padding]::new(0, 5, 0, 4)
$verification.Padding = [Windows.Forms.Padding]::new(12, 0, 12, 0)
$verification.BackColor = [Drawing.Color]::FromArgb(239, 247, 248)
$verification.ForeColor = $muted
$verification.Text = '전송하면 휴대폰에서 실제로 확인한 점수와 경고가 여기에 표시돼요.'
$page.Controls.Add($verification, 0, 5)

$actionRow = [Windows.Forms.TableLayoutPanel]::new()
$actionRow.Dock = 'Fill'
$actionRow.Margin = [Windows.Forms.Padding]::new(0)
$actionRow.RowCount = 1
$actionRow.ColumnCount = 3
[void]$actionRow.ColumnStyles.Add([Windows.Forms.ColumnStyle]::new('Percent', 100))
[void]$actionRow.ColumnStyles.Add([Windows.Forms.ColumnStyle]::new('Absolute', 111))
[void]$actionRow.ColumnStyles.Add([Windows.Forms.ColumnStyle]::new('Absolute', 154))
$currentStatus = [Windows.Forms.Label]::new()
$currentStatus.Dock = 'Fill'
$currentStatus.TextAlign = 'MiddleLeft'
$currentStatus.AccessibleName = '전송 상태'
$currentStatus.Padding = [Windows.Forms.Padding]::new(0, 0, 10, 0)
$prepareButton = [Windows.Forms.Button]::new()
$prepareButton.Text = '기기 준비'
$prepareButton.Dock = 'Fill'
$prepareButton.Margin = [Windows.Forms.Padding]::new(0, 7, 10, 7)
$sendButton = [Windows.Forms.Button]::new()
$sendButton.Text = '기기에 보내기'
$sendButton.Dock = 'Fill'
$sendButton.Margin = [Windows.Forms.Padding]::new(0, 7, 0, 7)
$sendButton.FlatStyle = 'Flat'
$sendButton.BackColor = $accent
$sendButton.ForeColor = [Drawing.Color]::White
$actionRow.Controls.Add($currentStatus, 0, 0)
$actionRow.Controls.Add($prepareButton, 1, 0)
$actionRow.Controls.Add($sendButton, 2, 0)
$page.Controls.Add($actionRow, 0, 6)

$hint = [Windows.Forms.Label]::new()
$hint.Dock = 'Fill'
$hint.ForeColor = $muted
$hint.Font = [Drawing.Font]::new('Malgun Gothic', 9)
$hint.Text = '처음 쓰는 기기는 시연 전에 기기 준비를 누르세요. 기기에서 쿠키패트롤 알림 감시를 켜두세요.'
$hint.TextAlign = 'MiddleLeft'
$page.Controls.Add($hint, 0, 7)

$script:demoBusy = $false
$script:connectedDevices = @()
$script:verificationRun = $null
$random = [Random]::new()
$verificationTimer = [Windows.Forms.Timer]::new()
$verificationTimer.Interval = 800

function Get-SelectedDemoDevice {
    if ($deviceList.SelectedIndex -ge 0) { $script:connectedDevices[$deviceList.SelectedIndex] }
}

function Set-DemoStatus($Text, [switch]$IsError) {
    $currentStatus.Text = $Text
    $currentStatus.ForeColor = if ($IsError) { [Drawing.Color]::Firebrick } else { $muted }
}

function Get-MatchingDemoPreset([string]$Body) {
    if ($presetList.SelectedIndex -lt 0) { return }
    $example = $presets[$presetList.SelectedIndex]
    if (($example.text -replace '\r\n', "`n") -ceq $Body) { $example }
}

function Set-VerificationResult([string]$Text, [Drawing.Color]$Color) {
    $verification.Text = $Text
    $verification.ForeColor = $Color
}

function Complete-DemoVerification([string]$Text, [switch]$IsError) {
    $verificationTimer.Stop()
    $script:verificationRun = $null
    $color = if ($IsError) { [Drawing.Color]::Firebrick } else { $accent }
    Set-VerificationResult $Text $color
}

function Start-DemoVerification([string]$Serial, [string]$RunId, $Example) {
    $verificationTimer.Stop()
    $script:verificationRun = [pscustomobject]@{
        Serial = $Serial
        RunId = $RunId
        Example = $Example
        Clock = [Diagnostics.Stopwatch]::StartNew()
        StableSince = $null
    }
    Set-VerificationResult '휴대폰이 이 메시지를 검사했는지 확인 중…' $muted
    $verificationTimer.Start()
}

$verificationTimer.add_Tick({
    $run = $script:verificationRun
    if ($null -eq $run) { return }
    try {
        $receipt = Get-DemoVerification -Serial $run.Serial -RunId $run.RunId
    } catch {
        Complete-DemoVerification "검사 결과를 읽지 못했어요. $($_.Exception.Message)" -IsError
        return
    }
    $elapsed = $run.Clock.ElapsedMilliseconds
    if ($null -eq $receipt.Score) {
        if ($elapsed -ge 9000) {
            Complete-DemoVerification '시연 알림은 전달됐지만 검사 응답이 없어요. 최신 시연용 앱과 문자·카톡 감시를 확인하세요.' -IsError
        }
        return
    }

    $stage = [int]$receipt.Stage
    $count = $receipt.WarningCount
    $warning = if ($count -eq 0) { '경고 단계 없음 · 경고 0건' } else { "${stage}단계 · 경고 ${count}건" }
    $summary = "실제 기기 검사 $($receipt.Score)점 · $warning"
    if ($count -gt 1) {
        Complete-DemoVerification "$summary`r`n같은 시연 메시지에 경고가 여러 건 만들어졌어요." -IsError
        return
    }

    $example = $run.Example
    if ($null -ne $example) {
        if ($receipt.Score -ne $example.expectedScore -or $stage -gt $example.expectedStage) {
            Complete-DemoVerification "$summary`r`n예제에서 예상한 점수 또는 경고 단계와 달라요." -IsError
            return
        }
        if ($stage -lt $example.expectedStage) {
            $limit = if ($example.expectedStage -eq 3) { 33000 } else { 12000 }
            if ($elapsed -ge $limit) {
                $reason = if ($example.expectedStage -eq 3) {
                    'Google 3단계가 확인되지 않았어요. 자동 링크 추가 검사와 백엔드를 확인하세요.'
                } else {
                    '예상한 경고가 확인되지 않았어요. 휴대폰의 위험 경고 알림을 확인하세요.'
                }
                Complete-DemoVerification "$summary`r`n$reason" -IsError
            } else {
                Set-VerificationResult "실제 기기 검사 $($receipt.Score)점 · $($example.expectedStage)단계 경고 확인 중…" $muted
            }
            return
        }
    }

    if ($null -eq $run.StableSince) { $run.StableSince = $elapsed }
    $settleTime = if ($null -eq $example -and $stage -eq 0) { 2500 } else { 1200 }
    if ($elapsed - $run.StableSince -lt $settleTime) { return }

    if ($null -eq $example -and $stage -eq 0) {
        Complete-DemoVerification "실제 기기 검사 $($receipt.Score)점 · 현재 경고 0건"
    } elseif ($null -eq $example) {
        Complete-DemoVerification $summary
    } else {
        Complete-DemoVerification "$summary`r`n예상 결과와 일치해요."
    }
})

function Update-SendButton {
    $selected = Get-SelectedDemoDevice
    $canUseDevice = -not $script:demoBusy -and $null -ne $selected -and $selected.Ready
    $prepareButton.Enabled = $canUseDevice
    $sendButton.Enabled = $canUseDevice -and -not [string]::IsNullOrWhiteSpace($message.Text)
}

function Refresh-DemoDevices {
    $selected = Get-SelectedDemoDevice
    try {
        $devices = @(Get-DemoDevices)
        $deviceList.BeginUpdate()
        try {
            $deviceList.Items.Clear()
            $script:connectedDevices = $devices
            $remembered = -1
            for ($index = 0; $index -lt $devices.Count; $index++) {
                [void]$deviceList.Items.Add($devices[$index].Label)
                if ($selected -and $selected.Serial -eq $devices[$index].Serial) { $remembered = $index }
            }
            if ($remembered -ge 0) { $deviceList.SelectedIndex = $remembered }
            elseif ($devices.Count -eq 1) { $deviceList.SelectedIndex = 0 }
        } finally {
            $deviceList.EndUpdate()
        }
        if (-not $devices.Count) {
            Set-DemoStatus 'USB 기기를 연결하고 USB 디버깅을 허용한 뒤 새로고침하세요.'
        } elseif (-not ($devices | Where-Object Ready)) {
            Set-DemoStatus '휴대폰에서 USB 디버깅을 허용한 뒤 새로고침하세요.'
        } else {
            Set-DemoStatus '예제를 고르거나 메시지를 입력한 뒤 기기에 보내세요.'
        }
    } catch {
        $deviceList.Items.Clear()
        $script:connectedDevices = @()
        Set-DemoStatus $_.Exception.Message -IsError
    }
    Update-SendButton
}

$presetList.add_SelectedIndexChanged({
    if ($presetList.SelectedIndex -ge 0) {
        $message.Text = $presets[$presetList.SelectedIndex].text -replace '\r?\n', "`r`n"
    }
})
$randomButton.add_Click({
    $current = $presetList.SelectedIndex
    $next = if ($current -lt 0) { $random.Next($presets.Count) } else { $random.Next($presets.Count - 1) }
    if ($current -ge 0 -and $next -ge $current) { $next++ }
    $presetList.SelectedIndex = $next
})
$message.add_TextChanged({
    $example = Get-MatchingDemoPreset ($message.Text -replace '\r\n', "`n")
    if ($example) {
        $expectation.Text = $example.expected
    } else {
        $expectation.Text = '수정한 메시지는 입력 내용에 따라 검사 결과가 달라져요.'
    }
    Update-SendButton
})
$deviceList.add_SelectedIndexChanged({ Update-SendButton })
$refreshButton.add_Click({ Refresh-DemoDevices })

function Invoke-DemoDeviceAction($Working, $Done, [scriptblock]$Operation) {
    $selected = Get-SelectedDemoDevice
    if (-not $selected -or -not $selected.Ready -or $script:demoBusy) { return }
    $script:demoBusy = $true
    $refreshButton.Enabled = $false
    Update-SendButton
    Set-DemoStatus $Working
    $form.Refresh()
    try {
        $result = & $Operation $selected.Serial
        if ($result -eq 'ALREADY_POSTED') {
            Set-DemoStatus '방금 보낸 같은 메시지는 한 번만 전송했습니다.'
        } else {
            Set-DemoStatus $Done
        }
    } catch {
        Set-DemoStatus $_.Exception.Message -IsError
    } finally {
        $script:demoBusy = $false
        $refreshButton.Enabled = $true
        Update-SendButton
    }
}

$prepareButton.add_Click({
    Invoke-DemoDeviceAction '기기에 시연용 알림 앱을 준비하는 중…' '기기 준비가 끝났습니다. 시연 예제를 보낼 수 있어요.' {
        param($serial)
        $null = Initialize-DemoDevice -Serial $serial
    }
})
$sendButton.add_Click({
    Invoke-DemoDeviceAction '기기에 시연 메시지를 보내는 중…' '시연 메시지를 전송했습니다.' {
        param($serial)
        $body = $message.Text -replace '\r\n', "`n"
        $runId = [guid]::NewGuid().ToString('N').Substring(0, 8).ToUpperInvariant()
        $result = Send-DemoMessage -Serial $serial -Body $body -RunId $runId
        if ($result -eq 'POSTED') {
            Start-DemoVerification -Serial $serial -RunId $runId -Example (Get-MatchingDemoPreset $body)
        }
        $result
    }
})

$presetList.SelectedIndex = 0
$form.add_Shown({
    # Raise the form even though the accompanying PowerShell console is hidden.
    $form.TopMost = $true
    try {
        $form.BringToFront()
        $form.Activate()
    } finally {
        $form.TopMost = $false
    }
    Refresh-DemoDevices
})
try {
    [void]$form.ShowDialog()
} finally {
    $verificationTimer.Dispose()
    $form.Dispose()
}
