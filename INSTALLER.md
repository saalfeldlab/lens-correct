# ConfocalLens Installer Build

## Build Requirements

- Java 17+
- Maven 3.8+
- Linux: `fakeroot` (for DEB packages)

## Building

### Linux (tested on Ubuntu 22.04)

```bash
./build-installer.sh
```

Creates: `target/installer-linux/confocal-lens_0.0.1-SNAPSHOT-1_amd64.deb`

### Windows/macOS (untested)

Configuration files exist in `src/packaging/windows/` and `src/packaging/osx/` but have not been tested.

## Installation (Linux)

Install

```bash
sudo apt install target/installer-linux/confocal-lens_0.0.1-SNAPSHOT-1_amd64.deb
```

The tool is installed to `/opt/confocal-lens/` and symlinked to `/usr/local/bin/confocal-lens`. Run it:

```bash
confocal-lens --help
```

Uninstall

```bash
sudo apt remove confocal-lens
```

## Available Commands

```
confocal-lens calibrate-split    # Calibrate lens distortion for split-images/lens arrays
confocal-lens apply-split        # Apply correction to split-images/lens arrays
confocal-lens calibrate-channels # Calibrate multi-channel image stacks
confocal-lens apply-channels     # Apply multi-channel correction
```
