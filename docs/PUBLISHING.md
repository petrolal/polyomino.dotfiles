# Publishing Guide: GitHub Releases & AUR (Arch Linux)

Guide for publishing **polyomino.dotfiles** native binaries to **GitHub Releases** and **AUR (Arch User Repository)**.

---

## Architecture Overview

```
                      ┌───────────────────────────────┐
                      │  sbt nativeImage (GraalVM)    │
                      └───────────────┬───────────────┘
                                      │
              ┌───────────────────────┴───────────────────────┐
              ▼                                               ▼
  ┌───────────────────────────────┐               ┌───────────────────────────────┐
  │   GitHub Releases Artifacts   │               │     Arch Linux AUR Package    │
  │   • polyomino-x86_64-linux    │               │       (PKGBUILD build)        │
  │   • tar.gz bundle & SHA256    │               │                               │
  └───────────────────────────────┘               └───────────────────────────────┘
```

---

## 1. Automated Release via GitHub Actions

When you push a git tag (e.g. `v0.1.0`), GitHub Actions automatically:
1. Runs full Scala 3 test suite (`sbt test`).
2. Compiles a standalone Linux x86_64 ELF binary using GraalVM Native Image (`sbt nativeImage`).
3. Packages `polyomino-x86_64-linux` and compressed `.tar.gz` distribution with `SHA256SUMS.txt`.
4. Creates a GitHub Release with downloadable artifacts attached.

### How to Trigger a Release:

```bash
# 1. Run the interactive release helper
./scripts/release.sh

# 2. Select version bump (Patch / Minor / Major)

# 3. Push to GitHub with tags
git push origin master
git push origin --tags
```

---

## 2. Publishing to AUR (Arch User Repository)

### Prerequisites (One-Time Setup)
1. Register an account at [aur.archlinux.org](https://aur.archlinux.org/).
2. Add your SSH public key to your AUR profile.
3. Clone your AUR repository:
   ```bash
   git clone ssh://aur@aur.archlinux.org/polyomino-dotfiles.git ~/polyomino-dotfiles-aur
   ```

### Updating the AUR Package:
Whenever a new version is tagged:
```bash
# 1. Update .SRCINFO
makepkg --printsrcinfo > .SRCINFO

# 2. Copy PKGBUILD & .SRCINFO to AUR repo
cp PKGBUILD .SRCINFO ~/polyomino-dotfiles-aur/

# 3. Commit and push to AUR
cd ~/polyomino-dotfiles-aur
git add PKGBUILD .SRCINFO
git commit -m "chore: bump version to $NEW_VERSION"
git push
```

Arch Linux / Sway users can now install/update with:
```bash
yay -S polyomino-dotfiles
```

---

## 3. Local Binary Compilation

To build and install the standalone native image binary on your machine:

```bash
# Compile native binary with GraalVM
sbt nativeImage

# Install binary to ~/.local/bin
cp target/native-image/polyomino ~/.local/bin/polyomino

# Deploy symlinks
polyomino deploy
```
