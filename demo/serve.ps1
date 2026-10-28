param([int]$Port = 8765)

$pages = @{
    '/' = @{ Bytes = [System.IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'site/index.html')); Type = 'text/html; charset=utf-8' }
    '/account-security' = @{ Bytes = [System.IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'site/account-security.html')); Type = 'text/html; charset=utf-8' }
    '/reward-ad' = @{ Bytes = [System.IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'site/reward-ad.html')); Type = 'text/html; charset=utf-8' }
    '/image-ad' = @{ Bytes = [System.IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'site/image-ad.html')); Type = 'text/html; charset=utf-8' }
    '/reward-banner.svg' = @{ Bytes = [System.IO.File]::ReadAllBytes((Join-Path $PSScriptRoot 'site/reward-banner.svg')); Type = 'image/svg+xml; charset=utf-8' }
}
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
$listener.Start()

Write-Host "Demo site is serving on port $Port. Emulator URL: http://10.0.2.2:$Port/"
Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*' } |
    Select-Object -ExpandProperty IPAddress -Unique |
    ForEach-Object { Write-Host "Phone URL: http://${_}:$Port/" }
Write-Host 'Press Ctrl+C to stop.'

try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::ASCII)
            $requestLine = $reader.ReadLine()
            do { $headerLine = $reader.ReadLine() } while ($null -ne $headerLine -and $headerLine.Length -gt 0)

            $path = $null
            if ($requestLine -match '^GET (/[^ ]*) HTTP/') {
                try {
                    $path = ([uri]::new("http://localhost$($Matches[1])")).AbsolutePath.TrimEnd('/')
                    if ([string]::IsNullOrEmpty($path)) { $path = '/' }
                } catch {
                    $path = $null
                }
            }

            if ($null -ne $path -and $pages.ContainsKey($path)) {
                $status = '200 OK'
                $body = $pages[$path].Bytes
                $contentType = $pages[$path].Type
            } else {
                $status = '404 Not Found'
                $body = [System.Text.Encoding]::UTF8.GetBytes('Not found')
                $contentType = 'text/plain; charset=utf-8'
            }

            $responseHeader = [System.Text.Encoding]::ASCII.GetBytes(
                "HTTP/1.1 $status`r`nContent-Type: $contentType`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n"
            )
            $stream.Write($responseHeader, 0, $responseHeader.Length)
            $stream.Write($body, 0, $body.Length)
            $stream.Flush()
        } finally {
            if ($reader) { $reader.Dispose() }
            $client.Dispose()
        }
    }
} finally {
    $listener.Stop()
}
