# polyomino.dotfiles

Personal Sway/Wayland desktop configuration — native
Sway keybindings, wofi launcher, waybar status bar, kitty terminal, and zsh
shell config. Built in Scala 3 + GraalVM Native Image to be published to Maven Central or cloned onto a fresh machine.

## Contents

```
bootstrap.sh                      # System dependency & package installer
build.sbt                         # Scala 3 + GraalVM Native Image build specification
zsh/.zshrc                        # Thin oh-my-zsh bootstrap + modular config loader
zsh/zsh_config/                   # Modular zsh config (*.zsh)
config/sway/config                # Sway window manager config
config/wofi/                      # App launcher styling
config/waybar/                    # Status bar config & styling
config/kitty/                     # Terminal emulator config
src/                              # Scala 3 core engine & subcommand modules
.github/workflows/deploy.yml      # GitHub Actions CI/CD for Maven Central & GitHub Releases
```

## How it works

This repo is the source of truth for configuration files — they are **symlinked** into your `$HOME`:

```
~/.zshrc                          -> ~/polyomino.dotfiles/zsh/.zshrc
~/.config/polyomino/zsh_config      -> ~/polyomino.dotfiles/zsh/zsh_config
~/.config/sway                    -> ~/polyomino.dotfiles/config/sway
~/.config/wofi                    -> ~/polyomino.dotfiles/config/wofi
~/.config/waybar                  -> ~/polyomino.dotfiles/config/waybar
~/.config/kitty                   -> ~/polyomino.dotfiles/config/kitty
```

`polyomino install` handles creating those links safely:

1. If the target is already a symlink to the repo → skip.
2. If the target is a real file/dir → move it to `~/.polyomino_backup/<timestamp>/` first.
3. Runs `bootstrap.sh` to provision system packages (`pacman` / `apt-get`).
4. Runs `polyomino healthcheck` at the end to verify system health.

---

## Local Secrets & Environment Variables

If you have API keys, tokens, or environment variables that shouldn't be checked into version control, `polyomino` supports a local secrets file out of the box.

Simply create a file at `~/.polyomino.local.zsh` in your home directory:

```zsh
# ~/.polyomino.local.zsh
export GH_PAT="your_github_token"
export TF_VAR_google_credentials="..."
```

This file is automatically sourced by `zsh/zsh_config/40-environment.zsh` if it exists, keeping your credentials completely separate from the tracked git repository while still loading them on shell startup.

---

## Installation & Usage

### Case 1: One-Shot Automated Web Installer (Recommended)

Run the entire installation in a single shot via `curl` (zero prior setup needed):

```bash
curl -fsSL https://raw.githubusercontent.com/petrolal/polyomino.dotfiles/master/install.sh | bash
```

**With optional Gaming Stack (GameMode, Gamescope, MangoHud, Steam):**

```bash
curl -fsSL https://raw.githubusercontent.com/petrolal/polyomino.dotfiles/master/install.sh | bash -s -- --gaming
```

> **What the One-Shot Installer does end-to-end:**
>
> 1. **Clones / Updates Repository:** Fetches `polyomino.dotfiles` into `~/polyomino.dotfiles`.
> 2. **Executes `bootstrap.sh`:**
>    - Installs core desktop & system packages (`sway`/`swayfx`, `kitty`, `wofi`, `waybar`, `swaync`, `zoxide`, `fastfetch`, `zsh`, `neovim`).
>    - Provisions **SDKMAN!**, **Java 21 (GraalVM)**, **SBT**, and **Coursier (`cs`)**.
>    - Creates the **`~/Projects`** workspace directory.
>    - Clones **`git@github.com:petrolal/tetravim.nvim.git`** into `~/tetravim.nvim` and links it to `~/.config/nvim`.
> 3. **Compiles Native Binary:** Builds the standalone GraalVM native binary (`sbt nativeImage`) and places `polyomino` in `~/.local/bin/`.
> 4. **Deploys Configurations:** Runs `polyomino install` to deploy all symlinks, render active theme colors/tokens, and configure Sway shortcuts.

---

### Case 2: From Local Git Repository

If you have already cloned the repository or want to install from source:

```bash
git clone https://github.com/petrolal/polyomino.dotfiles.git ~/polyomino.dotfiles
cd ~/polyomino.dotfiles

# Run one-shot local installer
./install.sh
```

---

### Case 3: 3-Stage Modular Installation (Maven Central / Coursier)

For environments where you prefer running individual stages manually:

```bash
# Stage 1: Bootstrap system packages, Java, SBT, zoxide, and Tetravim
bash <(curl -fsSL https://raw.githubusercontent.com/petrolal/polyomino.dotfiles/master/bootstrap.sh)

# Stage 2: Download polyomino binary directly from Maven Central
cs bootstrap io.github.petrolal::polyomino -o ~/.local/bin/polyomino

# Stage 3: Deploy dotfiles, symlinks, and run healthcheck
polyomino install
```

---

### Case 4: Arch Linux Package (PKGBUILD / AUR)

On Arch Linux / Manjaro, build and install as a native Arch package:

```bash
git clone https://github.com/petrolal/polyomino.dotfiles.git ~/polyomino.dotfiles
cd ~/polyomino.dotfiles
makepkg -si
```

---

### Case 5: Day-to-Day Maintenance & Updates

Once installed, use the built-in CLI for updates, themes, and configuration backups:

- **Update everything:** `polyomino update` (pulls latest git changes and re-deploys)
- **Re-link configurations:** `polyomino deploy` or `polyomino install --links-only`
- **System Healthcheck:** `polyomino healthcheck`
- **Change desktop theme:** `polyomino-theme-picker` (or `Mod+Shift+T`)
- **Switch wallpaper:** `polyomino-wallpaper` (or `Mod+Shift+P`)
- **Snapshot configurations:** `polyomino backup`
- **Restore snapshot:** `polyomino restore <archive-path>`

---

### Case 6: Uninstallation & Restoring Backups

To remove symlinks and restore your original pre-installation configurations:

```bash
polyomino uninstall
```

---

## Building & Developing Locally

To develop, run tests, or compile from source:

```bash
cd ~/polyomino.dotfiles

# Run test suite (101+ unit tests)
sbt test

# Compile GraalVM Native Image
sbt nativeImage

# Install compiled binary
cp target/native-image/polyomino ~/.local/bin/polyomino
```

---

## Key Bindings

`$mod` = Mod4 (Super/Windows key). Full list available live via `polyomino-whichkey` (`Mod+Shift+?`).

| Keys                    | Action                    | Description                                                |
| ----------------------- | ------------------------- | ---------------------------------------------------------- |
| `Mod+Return`            | Open standard terminal    | Plain Kitty terminal in active workspace                   |
| `Mod+P`                 | **Neovim Project Picker** | Search `~/Projects` and open in dedicated Workspace 2      |
| `Mod+D`                 | App launcher              | Wofi application launcher                                  |
| `Mod+Shift+Return`      | Floating terminal         | Floating centered terminal                                 |
| `Mod+Shift+F`           | File manager TUI          | `yazi` file manager                                        |
| `Mod+Shift+M`           | Spotify player TUI        | `spotify_player`                                           |
| `Mod+Shift+U`           | Bluetooth manager TUI     | `bluetui`                                                  |
| `Mod+Shift+A`           | Email client TUI          | `aerc`                                                     |
| `Mod+Shift+T`           | Theme picker GUI          | `polyomino-theme-picker`                                   |
| `Mod+Shift+P`           | Wallpaper picker GUI      | `polyomino-wallpaper`                                      |
| `Mod+F6`                | Next wallpaper            | Cycle next wallpaper in active theme                       |
| `Mod+F5`                | Cycle desktop flavor      | Cycle theme flavor (matriz / encruza / caravela / aruanda) |
| `Mod+Shift+?` / `Mod+/` | Which-key cheatsheet      | `polyomino-whichkey` live Sway shortcuts                   |
| `Mod+Shift+Q`           | Kill window               | Close focused window                                       |
| `Mod+Shift+C`           | Reload Sway config        | Re-read Sway configuration (`swaymsg reload`)              |
| `Mod+Escape`            | Lock screen               | 3D Rubik's Cube lockscreen (`polyomino lock`)              |
| `Print`                 | Full screenshot           | Capture full screen                                        |
| `Mod+Print`             | Region screenshot         | Interactive rectangle selection screenshot                 |
| `Mod+Shift+Print`       | Window screenshot         | Capture active window                                      |
