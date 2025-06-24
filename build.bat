@echo off
REM Create bin directory if it does not exist
if not exist "bin" mkdir bin

REM Compile Java file, output class files to bin
javac -d bin XRPCSwingDemo.java

if %ERRORLEVEL% NEQ 0 (
    echo Build failed.
    pause
    exit /b 1
)
echo Build successful.
pause