@echo off
REM Build native installer for confocal-lens CLI tool
REM Usage: build-installer.bat [platform]
REM   platform: linux, windows, osx (optional, defaults to current platform)

setlocal

set PLATFORM=%1
set MVN_OPTS=-Djava.awt.headless=true --add-opens=java.desktop/javax.swing=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED

echo =========================================
echo Building confocal-lens installer
echo =========================================

REM Clean previous builds
echo Cleaning previous builds...
call mvn clean

REM Build JAR with dependencies
echo Building JAR with dependencies...
call mvn package

REM Build installer for specified or current platform
if "%PLATFORM%"=="" (
    echo Building installer for current platform...
    call mvn install
) else if "%PLATFORM%"=="windows" (
    echo Building Windows MSI installer...
    call mvn install -Dos.detected.name=windows
) else if "%PLATFORM%"=="linux" (
    echo Building Linux DEB installer...
    call mvn install -Dos.detected.name=linux
) else if "%PLATFORM%"=="osx" (
    echo Building macOS PKG installer...
    call mvn install -Dos.detected.name=osx
) else if "%PLATFORM%"=="all" (
    echo Building installers for all platforms...
    echo Note: Cross-platform builds may require additional tools

    echo Building Windows installer...
    call mvn install -Dos.detected.name=windows

    echo Building Linux installer...
    call mvn install -Dos.detected.name=linux

    echo Building macOS installer...
    call mvn install -Dos.detected.name=osx
) else (
    echo Unknown platform: %PLATFORM%
    echo Valid options: linux, windows, osx, all
    exit /b 1
)

echo =========================================
echo Build complete!
echo Installers can be found in:
echo   target\installer-*\
echo =========================================

endlocal