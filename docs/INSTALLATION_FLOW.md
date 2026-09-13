# Installation Flow Guide

Complete guide to installing polyomino.dotfiles using the two recommended methods.

## Overview

```
Option A: One-shot web installer (recommended)
    ↓
    curl … | bash          (install.sh — clones repo, runs bootstrap, installs binary)
    ↓
    COMPLETE!

Option B: Manual / staged
    ↓
    bash bootstrap.sh      (Stage 1: system packages + Java)
    ↓
    polyomino binary       (Stage 2: install pre-built native binary)
    ↓
    polyomino install      (Stage 3: deploy dotfiles & symlinks)
    ↓
    COMPLETE!
```

---

## Option A — One-Shot Web Installer

```bash
# Standard
curl -fsSL https://raw.githubusercontent.com/petrolal/polyomino.dotfiles/master/install.sh | bash

# With gaming stack (GameMode, Gamescope, MangoHud, Steam)
curl -fsSL https://raw.githubusercontent.com/petrolal/polyomino.dotfiles/master/install.sh | bash -s -- --gaming
```

**What it does end-to-end:**
1. Clones / updates the repository to `~/polyomino.dotfiles`.
2. Runs `bootstrap.sh` (system packages + Java 21 GraalVM via SDKMan).
3. Installs the standalone native binary into `~/.local/bin/polyomino`.
4. Runs `polyomino install` to deploy all symlinks and configurations.

---

## Option B — Staged Manual Installation

### Stage 1: Bootstrap (~5-10 min)

```bash
git clone https://github.com/petrolal/polyomino.dotfiles.git ~/polyomino.dotfiles
cd ~/polyomino.dotfiles
./bootstrap.sh
```

**What `bootstrap.sh` does:**
1. Detects your package manager (pacman / apt-get / dnf).
2. Installs system dependencies (Sway, Waybar, Kitty, Wofi, etc.).
3. Installs Java 21 GraalVM via SDKMan.
4. Installs the `polyomino` native binary (see Stage 2 logic).
5. Adds `~/.local/bin` to PATH.

### Stage 2: Install polyomino Native Binary

The `bootstrap.sh` script handles this automatically in order of preference:

| Priority | Method | When used |
|----------|--------|-----------|
| 1 | Local `target/native-image/polyomino` | After `sbt nativeImage` locally |
| 2 | `sbt nativeImage` compile | When `sbt` & `build.sbt` are present |
| 3 | GitHub Releases download | Fresh machine / no Java yet |

**Manual GitHub Releases install:**
```bash
mkdir -p ~/.local/bin
curl -fsSL https://github.com/petrolal/polyomino.dotfiles/releases/latest/download/polyomino-x86_64-linux \
  -o ~/.local/bin/polyomino
chmod +x ~/.local/bin/polyomino
```

**Build from source:**
```bash
sbt nativeImage
cp target/native-image/polyomino ~/.local/bin/polyomino
```

### Stage 3: Deploy Dotfiles

```bash
polyomino install
```

---

## Arch Linux (AUR / PKGBUILD)

```bash
git clone https://github.com/petrolal/polyomino.dotfiles.git ~/polyomino.dotfiles
cd ~/polyomino.dotfiles
makepkg -si
```

Or, once published to AUR:
```bash
yay -S polyomino-dotfiles
```

---

## Environment Setup

Ensure `~/.local/bin` is in your PATH. Add to `~/.bashrc` or `~/.zshrc`:

```bash
export PATH="$HOME/.local/bin:$PATH"

# SDKMan (for Java management)
export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"
```

Then reload:
```bash
exec zsh   # or source ~/.bashrc
```

---

## Verification

```bash
# Check binary
polyomino --version

# Run full health check
polyomino healthcheck

# List commands
polyomino --help
```

---

## Troubleshooting

### "polyomino: command not found"
```bash
export PATH="$HOME/.local/bin:$PATH"
exec zsh
```

### "Java not found"
```bash
curl -s "https://get.sdkman.io" | bash
source ~/.sdkman/bin/sdkman-init.sh
sdk install java 21.0.1-graal --default
```

### Binary download fails (no release yet)
```bash
# Compile from source (requires Java + sbt)
cd ~/polyomino.dotfiles
sbt nativeImage
cp target/native-image/polyomino ~/.local/bin/polyomino
```

---

## Installation Timeline

| Step | Time | Notes |
|------|------|-------|
| System packages | 2-5 min | Sway, Waybar, Kitty, etc. |
| Java 21 GraalVM | 3-5 min | Via SDKMan |
| Native binary | ~10 sec | GitHub download or local build |
| Deploy & symlinks | 1-2 min | `polyomino install` |
| **Total** | **~10 min** | Fresh machine |

---

## See Also

- [PUBLISHING.md](PUBLISHING.md) — GitHub Releases & AUR publishing
- [README.md](../README.md) — Project overview
