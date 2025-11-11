#!/bin/bash
# Uninstaller for lens-correct

echo "Uninstalling lens-correct..."

# Remove symlink
if [ -L "/usr/local/bin/lens-correct" ]; then
    echo "Removing symlink from /usr/local/bin..."
    sudo rm -f /usr/local/bin/lens-correct
fi

# Remove application bundle
if [ -d "/Applications/lens-correct.app" ]; then
    echo "Removing application from /Applications..."
    sudo rm -rf "/Applications/lens-correct.app"
fi

# Optional: Remove package receipt (helps with package database cleanup)
pkgutil --pkgs | grep -i lens-correct | while read pkg; do
    echo "Removing package receipt: $pkg"
    sudo pkgutil --forget "$pkg"
done

echo "Uninstallation complete."