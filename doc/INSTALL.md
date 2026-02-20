# LensCorrect Installer Build

## Build Requirements

- Java 17+
- Maven 3.8+
- Linux: `fakeroot` (for DEB packages)

## Building

### Linux and MacOS (tested on Ubuntu 22.04 and macOS Sequoia M4)

```bash
./build-installer.sh
```

Creates `target/installer-linux/lens-correct_0.0.3-SNAPSHOT-1_amd64.deb` or `target/installer-osx/lens-correct-1.pkg`, respectively.

## Installation

### Ubuntu or Debian Linux

Install

```bash
sudo apt install target/installer-linux/lens-correct_0.0.3-SNAPSHOT-1_amd64.deb
```

The tool is installed to `/opt/lens-correct/` and symlinked to `/usr/local/bin/lens-correct`. Run it:

```bash
lens-correct --help
```

Uninstall

```bash
sudo apt remove lens-correct
```

### MacOS

Install by double clicking `target/installer-osx/lens-correct-1.pkg`.

The tool is installed to `/Applications/lens-correct.app/` and symlinked to `/usr/local/bin/lens-correct`. Run it:

```bash
lens-correct --help
```

Uninstall

```bash
sudo rm -rf /Applications/lens-correct.app /usr/local/bin/lens-correct
```

### Windows (untested)

Configuration files exist in `src/packaging/windows/` but have not been tested.

## Available Commands

```
lens-correct calibrate-split    # Calibrate lens distortion for split-images/lens arrays
lens-correct apply-split        # Apply correction to split-images/lens arrays
lens-correct calibrate-channels # Calibrate multi-channel image stacks
lens-correct apply-channels     # Apply multi-channel correction
```
