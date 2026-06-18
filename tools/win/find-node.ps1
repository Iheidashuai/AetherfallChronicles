$codexNodeRoot = Join-Path $env:LOCALAPPDATA 'OpenAI\Codex\bin'
$node = Get-ChildItem -Path $codexNodeRoot -Filter node.exe -Recurse -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $node) {
    $node = 'D:\CSO2server\Server\node.exe'
}

Write-Output $node
