@echo off
cd /d "%~dp0"
if not exist "bin" (
    echo You must build the project first.
    pause
    exit /b 1
)
cd bin
java XRPCSwingDemo
cd ..
pause