@echo off
setlocal
where py >nul 2>&1
if errorlevel 1 (
    python "%~dp0install.py"
) else (
    py -3 "%~dp0install.py"
)
if errorlevel 1 (
    echo Installation failed. Read the message above.
) else (
    echo Installation complete.
)
pause
