$repoRoot = Split-Path -Parent $PSScriptRoot
$toolBin = Join-Path $repoRoot 'tools\win'
$javaHome = 'C:\Program Files\Java\jdk-21'
$mavenHome = 'C:\Users\10050\apache-maven-3.9.10-bin\apache-maven-3.9.10'
$repoPythonExe = Join-Path $repoRoot 'tools\python\cpython-3.10.16-windows-x86_64-none\python.exe'
$pythonHome = Join-Path $env:LOCALAPPDATA 'Programs\Python\Python310'

$env:JAVA_HOME = $javaHome
$env:CODEX_PYTHON_EXE = $null
if (Test-Path -LiteralPath $repoPythonExe) {
    $env:CODEX_PYTHON_EXE = $repoPythonExe
} else {
    $pythonExe = Join-Path $pythonHome 'python.exe'
    try {
        if (Test-Path -LiteralPath $pythonExe -ErrorAction Stop) {
            $env:CODEX_PYTHON_EXE = $pythonExe
        }
    } catch {
        # The Codex sandbox may deny probing user-local app directories; the wrapper has fallbacks.
    }
}

$pathEntries = @(
    $toolBin,
    (Join-Path $javaHome 'bin'),
    (Join-Path $mavenHome 'bin')
)

$env:Path = (($pathEntries + ($env:Path -split ';' | Where-Object { $_ })) -join ';')

Write-Host "Local tools ready: npm wrapper, Python wrapper, Maven 3.9.10, Java 21"
