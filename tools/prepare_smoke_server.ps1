param([string]$SourceServer = 'D:\programs\minecraft\devminecraftserver')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$testServerRoot = Join-Path $projectRoot 'dev-server'
New-Item -ItemType Directory -Path $testServerRoot -Force | Out-Null
foreach ($entry in @('server.jar', 'eula.txt', 'libraries', 'cache', 'versions')) {
    Copy-Item -LiteralPath (Join-Path $SourceServer $entry) -Destination $testServerRoot -Recurse -Force
}
$pluginRoot = Join-Path $testServerRoot 'plugins\Pynner'
New-Item -ItemType Directory -Path (Join-Path $pluginRoot 'scripts') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'pynner-paper\target\pynner-paper-0.1.0.jar') -Destination (Join-Path $testServerRoot 'plugins\pynner-paper-0.1.0.jar') -Force
Get-ChildItem -LiteralPath (Join-Path $projectRoot 'examples') -Filter '*.py' | Copy-Item -Destination (Join-Path $pluginRoot 'scripts') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'paper_smoke_script.py') -Destination (Join-Path $pluginRoot 'scripts\smoke.py') -Force
$testPythonPath = (Join-Path $projectRoot '.venv\Scripts\python.exe').Replace('\', '/')
@"
python:
  executable: '$testPythonPath'
runtime:
  auto-reload: true
  startup-timeout-seconds: 30
  heartbeat-timeout-seconds: 15
  restart-limit: 3
  restart-delay-seconds: 5
"@ | Set-Content -LiteralPath (Join-Path $pluginRoot 'config.yml') -Encoding utf8
@'
server-ip=127.0.0.1
server-port=25591
online-mode=false
enable-rcon=false
level-name=world
level-type=minecraft:flat
generate-structures=false
view-distance=2
simulation-distance=2
spawn-protection=0
max-players=5
pause-when-empty-seconds=-1
sync-chunk-writes=false
'@ | Set-Content -LiteralPath (Join-Path $testServerRoot 'server.properties') -Encoding utf8
Write-Output $testServerRoot
