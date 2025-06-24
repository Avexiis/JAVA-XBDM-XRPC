@echo off
if not exist "bin" mkdir bin

javac -d bin XRPCSwingDemo.java

if %ERRORLEVEL% NEQ 0 (
    echo Build failed.
    pause
    exit /b 1
)
echo Build successful.
pause