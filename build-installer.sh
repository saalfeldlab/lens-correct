#!/bin/bash

# Build native installer for lens-correct CLI tool
# Usage: ./build-installer.sh [platform]
#   platform: linux, windows, osx (optional, defaults to current platform)

set -e

PLATFORM=$1
MVN_OPTS="-Djava.awt.headless=true --add-opens=java.desktop/javax.swing=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED"

echo "========================================="
echo "Building lens-correct installer"
echo "========================================="

# Clean previous builds
echo "Cleaning previous builds..."
mvn clean

# Build JAR with dependencies
echo "Building JAR with dependencies..."
mvn package

# Copy JAR to jpackage input directory
echo "Preparing jpackage input directory..."
mkdir -p target/jars
cp target/*-jar-with-dependencies.jar target/jars/ 2>/dev/null || true

# Build installer for specified or current platform
if [ -z "$PLATFORM" ]; then
    echo "Building installer for current platform..."
    mvn install
else
    echo "Building installer for platform: $PLATFORM"
    case $PLATFORM in
        linux)
            echo "Building Linux DEB installer..."
            mvn install -Dos.detected.name=linux
            ;;
        windows)
            echo "Building Windows MSI installer..."
            mvn install -Dos.detected.name=windows
            ;;
        osx)
            echo "Building macOS PKG installer..."
            mvn install -Dos.detected.name=osx
            ;;
        all)
            echo "Building installers for all platforms..."
            echo "Note: Cross-platform builds may require additional tools"

            # Linux
            echo "Building Linux installer..."
            mvn install -Dos.detected.name=linux

            # Windows (requires Windows or Wine)
            echo "Building Windows installer..."
            mvn install -Dos.detected.name=windows

            # macOS (requires macOS)
            echo "Building macOS installer..."
            mvn install -Dos.detected.name=osx
            ;;
        *)
            echo "Unknown platform: $PLATFORM"
            echo "Valid options: linux, windows, osx, all"
            exit 1
            ;;
    esac
fi

echo "========================================="
echo "Build complete!"
echo "Installers can be found in:"
echo "  target/installer-*/"
echo "========================================="