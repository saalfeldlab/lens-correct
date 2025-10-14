#!/bin/bash
# Uninstaller for confocal-lens

echo "Uninstalling confocal-lens..."

# Remove symlink
if [ -L "/usr/local/bin/confocal-lens" ]; then
    echo "Removing symlink from /usr/local/bin..."
    sudo rm -f /usr/local/bin/confocal-lens
fi

# Remove application bundle
if [ -d "/Applications/confocal-lens.app" ]; then
    echo "Removing application from /Applications..."
    sudo rm -rf "/Applications/confocal-lens.app"
fi

# Optional: Remove package receipt (helps with package database cleanup)
pkgutil --pkgs | grep -i confocal-lens | while read pkg; do
    echo "Removing package receipt: $pkg"
    sudo pkgutil --forget "$pkg"
done

echo "Uninstallation complete."