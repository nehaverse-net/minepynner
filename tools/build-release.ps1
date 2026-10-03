$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    New-Item -ItemType Directory -Path 'dist' -Force | Out-Null
    & mvn -B package
    if ($LASTEXITCODE -ne 0) { throw 'Java build failed' }
    Copy-Item -LiteralPath 'pynner-paper\target\pynner-paper-0.1.0.jar' -Destination 'dist\pynner-paper-0.1.0.jar' -Force
    Copy-Item -LiteralPath 'dist\pynner-paper-0.1.0.jar' -Destination 'pynner-debug\src\pynner_debug\pynner-paper-0.1.0.jar' -Force
    $buildPython = Join-Path $projectRoot '.venv\Scripts\python.exe'
    foreach ($package in @('pynner-sdk', 'pynner-runtime', 'pynner-debug')) {
        & $buildPython -m build --wheel $package --outdir dist
        if ($LASTEXITCODE -ne 0) { throw "Wheel build failed: $package" }
    }
    Push-Location 'pynner-fabric'
    try {
        & .\gradlew.bat --console=plain build
        if ($LASTEXITCODE -ne 0) { throw 'Fabric build failed' }
    } finally { Pop-Location }
    Copy-Item -LiteralPath 'pynner-fabric\build\libs\pynner-debug-fabric-0.1.0.jar' -Destination 'dist\pynner-debug-fabric-0.1.0.jar' -Force
    & $buildPython tools/package_release.py
    if ($LASTEXITCODE -ne 0) { throw 'Release packaging failed' }
    Get-ChildItem -LiteralPath dist -File | Select-Object Name,Length
} finally {
    Pop-Location
}
