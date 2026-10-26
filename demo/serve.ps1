param([int]$Port = 8765)

$pagePath = Join-Path $PSScriptRoot 'site/index.html'
$pageBytes = [System.IO.File]::ReadAllBytes($pagePath)
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

            if ($requestLine -match '^GET /(?:\?[^ ]*)? HTTP/') {
                $status = '200 OK'
                $body = $pageBytes
                $contentType = 'text/html; charset=utf-8'
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
