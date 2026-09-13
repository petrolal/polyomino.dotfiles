#!/usr/bin/env bash
# polyomino.dotfiles Bootstrap Installer
# Minimal installer: Installs Java & Coursier only
# Full setup is handled by: polyomino install
set -euo pipefail



echo -e "\033[1;36m[polyomino bootstrap]\033[0m Starting polyomino.dotfiles installer..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$HOME/.local/bin"

NON_INTERACTIVE=false
ENABLE_ALL=false
ENABLE_MINIMAL=false

# Optional component flags (unset by default to allow prompting)
ENABLE_TETRAVIM=""
ENABLE_BROWSER=""
ENABLE_TUI_TOOLS=""
ENABLE_DEVOPS=""
ENABLE_DEV_RUNTIMES=""
ENABLE_DESKTOP_APPS=""
ENABLE_GAMING=""

for arg in "$@"; do
  case "$arg" in
    --all|-y|--yes)
      ENABLE_ALL=true
      NON_INTERACTIVE=true
      ;;
    --minimal|--no-optional)
      ENABLE_MINIMAL=true
      NON_INTERACTIVE=true
      ;;
    --non-interactive|-n)
      NON_INTERACTIVE=true
      ;;
    --gaming|--with-gaming|-g)
      ENABLE_GAMING=true
      ;;
    --without-gaming|--no-gaming)
      ENABLE_GAMING=false
      ;;
    --tetravim|--with-tetravim|--neovim)
      ENABLE_TETRAVIM=true
      ;;
    --without-tetravim|--no-tetravim|--no-neovim)
      ENABLE_TETRAVIM=false
      ;;
    --browser|--with-browser)
      ENABLE_BROWSER=true
      ;;
    --without-browser|--no-browser)
      ENABLE_BROWSER=false
      ;;
    --tui|--with-tui|--tools|--with-tools)
      ENABLE_TUI_TOOLS=true
      ;;
    --without-tui|--no-tui|--no-tools)
      ENABLE_TUI_TOOLS=false
      ;;
    --devops|--with-devops|--docker)
      ENABLE_DEVOPS=true
      ;;
    --without-devops|--no-devops|--no-docker)
      ENABLE_DEVOPS=false
      ;;
    --dev-runtimes|--with-dev-runtimes|--node|--with-node)
      ENABLE_DEV_RUNTIMES=true
      ;;
    --without-dev-runtimes|--no-dev-runtimes|--no-node)
      ENABLE_DEV_RUNTIMES=false
      ;;
    --desktop-apps|--with-desktop-apps|--telegram|--with-telegram)
      ENABLE_DESKTOP_APPS=true
      ;;
    --without-desktop-apps|--no-desktop-apps|--no-telegram)
      ENABLE_DESKTOP_APPS=false
      ;;
  esac
done

prompt_read() {
  local prompt_text="$1"
  local choice=""
  if [ -e /dev/tty ] && [ -r /dev/tty ]; then
    read -r -p "$prompt_text" choice < /dev/tty || choice=""
  else
    read -r -p "$prompt_text" choice || choice=""
  fi
  echo "$choice"
}

prompt_optional_dependencies() {
  if [ "$ENABLE_ALL" = true ]; then
    ENABLE_TETRAVIM=true
    ENABLE_BROWSER=true
    ENABLE_TUI_TOOLS=true
    ENABLE_DEVOPS=true
    ENABLE_DEV_RUNTIMES=true
    ENABLE_DESKTOP_APPS=true
    ENABLE_GAMING=true
    return
  fi

  if [ "$ENABLE_MINIMAL" = true ]; then
    ENABLE_TETRAVIM="${ENABLE_TETRAVIM:-false}"
    ENABLE_BROWSER="${ENABLE_BROWSER:-false}"
    ENABLE_TUI_TOOLS="${ENABLE_TUI_TOOLS:-false}"
    ENABLE_DEVOPS="${ENABLE_DEVOPS:-false}"
    ENABLE_DEV_RUNTIMES="${ENABLE_DEV_RUNTIMES:-false}"
    ENABLE_DESKTOP_APPS="${ENABLE_DESKTOP_APPS:-false}"
    ENABLE_GAMING="${ENABLE_GAMING:-false}"
    return
  fi

  echo -e "  \033[1;36m[polyomino]\033[0m Configuring non-obligatory dependency installations:"
  echo ""

  # 1. Neovim & Tetravim
  if [ -z "$ENABLE_TETRAVIM" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_TETRAVIM=true
    else
      choice="$(prompt_read "  Install Neovim & Tetravim distribution? [Y/n] ")"
      if [[ -z "$choice" || "$choice" =~ ^[Yy]$ ]]; then ENABLE_TETRAVIM=true; else ENABLE_TETRAVIM=false; fi
    fi
  fi

  # 2. Web Browser
  if [ -z "$ENABLE_BROWSER" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_BROWSER=true
    else
      choice="$(prompt_read "  Install Web Browser (Chromium / Firefox)? [Y/n] ")"
      if [[ -z "$choice" || "$choice" =~ ^[Yy]$ ]]; then ENABLE_BROWSER=true; else ENABLE_BROWSER=false; fi
    fi
  fi

  # 3. TUI Productivity Tools
  if [ -z "$ENABLE_TUI_TOOLS" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_TUI_TOOLS=true
    else
      choice="$(prompt_read "  Install TUI tools (spotify_player, bluetui, impala, aerc, zoxide, fastfetch)? [Y/n] ")"
      if [[ -z "$choice" || "$choice" =~ ^[Yy]$ ]]; then ENABLE_TUI_TOOLS=true; else ENABLE_TUI_TOOLS=false; fi
    fi
  fi

  # 4. DevOps & Cloud Tools
  if [ -z "$ENABLE_DEVOPS" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_DEVOPS=false
    else
      choice="$(prompt_read "  Install DevOps tools (Docker, Terraform, Ansible, kubectl, Helm, cloud CLIs)? [y/N] ")"
      if [[ "$choice" =~ ^[Yy]$ ]]; then ENABLE_DEVOPS=true; else ENABLE_DEVOPS=false; fi
    fi
  fi

  # 5. Developer Runtimes
  if [ -z "$ENABLE_DEV_RUNTIMES" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_DEV_RUNTIMES=false
    else
      choice="$(prompt_read "  Install Developer runtimes (Node.js/npm via NVM, SDKMAN! & Kotlin)? [y/N] ")"
      if [[ "$choice" =~ ^[Yy]$ ]]; then ENABLE_DEV_RUNTIMES=true; else ENABLE_DEV_RUNTIMES=false; fi
    fi
  fi

  # 6. Desktop Apps (Telegram)
  if [ -z "$ENABLE_DESKTOP_APPS" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_DESKTOP_APPS=false
    else
      choice="$(prompt_read "  Install Telegram Desktop? [y/N] ")"
      if [[ "$choice" =~ ^[Yy]$ ]]; then ENABLE_DESKTOP_APPS=true; else ENABLE_DESKTOP_APPS=false; fi
    fi
  fi

  # 7. Gaming Performance Stack
  if [ -z "$ENABLE_GAMING" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_GAMING=false
    else
      choice="$(prompt_read "  Install gaming optimizations & tools (gamemode, gamescope, mangohud, steam)? [y/N] ")"
      if [[ "$choice" =~ ^[Yy]$ ]]; then ENABLE_GAMING=true; else ENABLE_GAMING=false; fi
    fi
  fi

  echo ""
}

# Step 1: Detect package manager & install system dependencies
detect_pkg_mgr() {
  if command -v pacman &> /dev/null; then
    echo "pacman"
  elif command -v apt-get &> /dev/null; then
    echo "apt-get"
  elif command -v dnf &> /dev/null; then
    echo "dnf"
  else
    echo "unknown"
  fi
}

install_system_deps() {
  local pkg_mgr="$1"

  if [ "$pkg_mgr" = "unknown" ]; then
    echo -e "  \033[33m[NOTE]\033[0m Package manager not detected. Skipping system package installation."
    return
  fi

  echo -e "  \033[1;36m[polyomino]\033[0m Installing system dependencies for $pkg_mgr..."
  echo -e "  \033[33m[INFO]\033[0m You may be prompted for your sudo password..."

  # Optional packages to include
  local opt_pkgs=""

  case "$pkg_mgr" in
    pacman)
      # Core system + desktop + dev tools + lockscreen & Wayland stack
      SWAY_PKG=""
      if ! pacman -Qq swayfx &>/dev/null && ! pacman -Qq sway &>/dev/null; then
        if ! command -v yay &>/dev/null; then
          SWAY_PKG="sway"
        fi
      fi

      [ "$ENABLE_TETRAVIM" = true ] && opt_pkgs="$opt_pkgs neovim"
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs chromium firefox"
      [ "$ENABLE_TUI_TOOLS" = true ] && opt_pkgs="$opt_pkgs fastfetch zoxide"
      [ "$ENABLE_DEVOPS" = true ] && opt_pkgs="$opt_pkgs docker"
      [ "$ENABLE_DESKTOP_APPS" = true ] && opt_pkgs="$opt_pkgs telegram-desktop"

      sudo pacman -S --needed --noconfirm \
        base-devel git curl wget \
        zsh fontconfig \
        $SWAY_PKG waybar kitty wofi swaylock gtklock swayidle grim slurp \
        brightnessctl libpulse playerctl wireplumber swaync mako mpv \
        python-gobject python-cairo gtk3 gtk-layer-shell gtk-session-lock pam \
        ttf-jetbrains-mono-nerd \
        $opt_pkgs
      echo -e "  \033[32m[OK]\033[0m System packages installed"
      ;;
    apt-get)
      sudo apt-get update
      DOCKER_PKG=""
      if [ "$ENABLE_DEVOPS" = true ] && ! command -v docker &> /dev/null; then
        DOCKER_PKG="docker.io"
      fi

      [ "$ENABLE_TETRAVIM" = true ] && opt_pkgs="$opt_pkgs neovim"
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs firefox chromium-browser"
      [ "$ENABLE_TUI_TOOLS" = true ] && opt_pkgs="$opt_pkgs fastfetch zoxide"

      sudo apt-get install -y \
        build-essential git curl wget \
        zsh fontconfig \
        sway waybar kitty wofi swaylock swayidle grim slurp \
        brightnessctl playerctl wireplumber pulseaudio-utils sway-notification-center mako-notifier mpv \
        python3-gi python3-cairo gir1.2-gtk-3.0 gir1.2-gtklayershell-0.1 libpam0g-dev \
        fonts-jetbrains-mono \
        $DOCKER_PKG \
        $opt_pkgs
      echo -e "  \033[32m[OK]\033[0m System packages installed"
      ;;
    dnf)
      [ "$ENABLE_TETRAVIM" = true ] && opt_pkgs="$opt_pkgs neovim"
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs firefox"
      [ "$ENABLE_TUI_TOOLS" = true ] && opt_pkgs="$opt_pkgs fastfetch zoxide"
      [ "$ENABLE_DEVOPS" = true ] && opt_pkgs="$opt_pkgs docker"
      [ "$ENABLE_DESKTOP_APPS" = true ] && opt_pkgs="$opt_pkgs telegram-desktop"

      sudo dnf install -y \
        gcc gcc-c++ git curl wget \
        zsh fontconfig \
        sway waybar kitty wofi swaylock swayidle grim slurp \
        brightnessctl playerctl wireplumber pulseaudio-libs sway-notification-center mako mpv \
        python3-gobject python3-cairo gtk3 gtk-layer-shell pam-devel \
        $opt_pkgs
      echo -e "  \033[32m[OK]\033[0m System packages installed"
      ;;
    *)
      echo -e "  \033[33m[NOTE]\033[0m Package manager '$pkg_mgr' not automatically managed. Skipping installation."
      ;;
  esac
}

install_java() {
  if [ -d "$HOME/.sdkman/candidates/java/current/bin" ]; then
    export PATH="$HOME/.sdkman/candidates/java/current/bin:$PATH"
  fi

  if command -v java &> /dev/null; then
    echo -e "  \033[32m[OK]\033[0m Java already installed:"
    java -version 2>&1 | head -1
    return
  fi

  echo -e "  \033[1;36m[polyomino]\033[0m Installing Java (GraalVM 21)..."

  # Try to install via SDKMan
  if [ ! -d "$HOME/.sdkman" ]; then
    echo -e "  \033[36m[INFO]\033[0m Installing SDKMan..."
    curl -s "https://get.sdkman.io" | bash
  fi

  if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
    set +u
    source "$HOME/.sdkman/bin/sdkman-init.sh"
    sdk install java 21.0.1-graal --default 2>/dev/null || true
    if ! command -v sbt &> /dev/null; then
      sdk install sbt --default 2>/dev/null || true
    fi
    set -u
  fi

  if [ -d "$HOME/.sdkman/candidates/java/current/bin" ]; then
    export PATH="$HOME/.sdkman/candidates/java/current/bin:$PATH"
  fi
  if [ -d "$HOME/.sdkman/candidates/sbt/current/bin" ]; then
    export PATH="$HOME/.sdkman/candidates/sbt/current/bin:$PATH"
  fi

  if command -v java &> /dev/null; then
    echo -e "  \033[32m[OK]\033[0m Java installed via SDKMan"
    java -version 2>&1 | head -1
  else
    echo -e "  \033[31m[ERROR]\033[0m Java installation failed"
    exit 1
  fi
}

install_polyomino_binary() {
  echo -e "  \033[1;36m[polyomino]\033[0m Installing polyomino native binary..."
  mkdir -p "$BIN_DIR"

  # 1. If local native binary exists, install it
  if [ -f "$SCRIPT_DIR/target/native-image/polyomino" ]; then
    cp "$SCRIPT_DIR/target/native-image/polyomino" "$BIN_DIR/polyomino"
    chmod +x "$BIN_DIR/polyomino"
    echo -e "  \033[32m[OK]\033[0m Installed local native binary to $BIN_DIR/polyomino"
  elif command -v sbt &>/dev/null && [ -f "$SCRIPT_DIR/build.sbt" ]; then
    echo -e "  \033[36m[INFO]\033[0m Compiling standalone GraalVM native binary..."
    (cd "$SCRIPT_DIR" && sbt nativeImage) || true
    if [ -f "$SCRIPT_DIR/target/native-image/polyomino" ]; then
      cp "$SCRIPT_DIR/target/native-image/polyomino" "$BIN_DIR/polyomino"
      chmod +x "$BIN_DIR/polyomino"
      echo -e "  \033[32m[OK]\033[0m Built & installed native binary to $BIN_DIR/polyomino"
    fi
  fi

  # 2. If not installed yet, download latest native binary from GitHub Releases
  if [ ! -f "$BIN_DIR/polyomino" ]; then
    echo -e "  \033[36m[INFO]\033[0m Fetching latest native binary release from GitHub..."
    if curl -fL "https://github.com/petrolal/polyomino.dotfiles/releases/latest/download/polyomino-x86_64-linux" -o "$BIN_DIR/polyomino" 2>/dev/null; then
      chmod +x "$BIN_DIR/polyomino"
      echo -e "  \033[32m[OK]\033[0m Downloaded polyomino native binary to $BIN_DIR/polyomino"
    else
      echo -e "  \033[33m[NOTE]\033[0m Could not download binary directly (run 'sbt nativeImage' to compile locally)"
    fi
  fi

  # 3. Create helper symlinks if binary is present
  if [ -x "$BIN_DIR/polyomino" ]; then
    "$BIN_DIR/polyomino" deploy 2>/dev/null || true
  fi
}

install_tools() {
  local pkg_mgr="$1"
  echo -e "  \033[1;36m[polyomino]\033[0m Installing terminal & TUI tools (spotify_player, bluetui)..."

  # Ensure cargo/rust and required build dependencies are available
  if ! command -v cargo &> /dev/null; then
    echo -e "  \033[36m[INFO]\033[0m Installing Rust & Cargo build toolchain..."
    case "$pkg_mgr" in
      pacman)
        sudo pacman -S --needed --noconfirm rust cargo alsa-lib libpulse dbus openssl pkgconf fastfetch
        ;;
      apt-get)
        sudo apt-get install -y cargo rustc pkg-config libasound2-dev libpulse-dev libdbus-1-dev libssl-dev fastfetch
        ;;
      dnf)
        sudo dnf install -y cargo rust alsa-lib-devel pulseaudio-libs-devel dbus-devel openssl-devel pkgconf-pkg-config fastfetch
        ;;
      *)
        if command -v curl &> /dev/null; then
          curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
          export PATH="$HOME/.cargo/bin:$PATH"
        fi
        ;;
    esac
  else
    # Install build headers if cargo is already installed
    case "$pkg_mgr" in
      pacman)
        sudo pacman -S --needed --noconfirm alsa-lib libpulse dbus openssl pkgconf 2>/dev/null || true
        ;;
      apt-get)
        sudo apt-get install -y pkg-config libasound2-dev libpulse-dev libdbus-1-dev libssl-dev 2>/dev/null || true
        ;;
      dnf)
        sudo dnf install -y alsa-lib-devel pulseaudio-libs-devel dbus-devel openssl-devel pkgconf-pkg-config 2>/dev/null || true
        ;;
      *)
        ;;
    esac
  fi

  if [ -d "$HOME/.cargo/bin" ]; then
    export PATH="$HOME/.cargo/bin:$PATH"
  fi

  # 1. spotify_player TUI (cargo)
  if command -v spotify_player &> /dev/null; then
    echo -e "  \033[32m[OK]\033[0m spotify_player already installed"
  elif command -v cargo &> /dev/null; then
    echo -e "  \033[36m[INFO]\033[0m Installing spotify_player via cargo..."
    cargo install spotify_player --locked --features daemon,pulseaudio-backend,rodio-backend 2>/dev/null || true
  fi

  # 2. bluetui Bluetooth TUI (cargo)
  if command -v bluetui &> /dev/null; then
    echo -e "  \033[32m[OK]\033[0m bluetui already installed"
  elif command -v cargo &> /dev/null; then
    echo -e "  \033[36m[INFO]\033[0m Installing bluetui via cargo..."
    cargo install bluetui --locked 2>/dev/null || true
  fi

  # 3. aerc Email Client TUI (package manager)
  if command -v aerc &> /dev/null; then
    echo -e "  \033[32m[OK]\033[0m aerc email client already installed"
  else
    echo -e "  \033[36m[INFO]\033[0m Installing aerc email client..."
    case "$pkg_mgr" in
      pacman)
        sudo pacman -S --needed --noconfirm aerc 2>/dev/null || true
        ;;
      apt-get)
        sudo apt-get install -y aerc 2>/dev/null || true
        ;;
      dnf)
        sudo dnf install -y aerc 2>/dev/null || true
        ;;
      *)
        ;;
    esac
  fi

  # 4. zoxide directory jumper (system / cargo / standalone fallback)
  if command -v zoxide &> /dev/null; then
    echo -e "  \033[32m[OK]\033[0m zoxide already installed"
  elif command -v cargo &> /dev/null; then
    echo -e "  \033[36m[INFO]\033[0m Installing zoxide via cargo..."
    cargo install zoxide --locked 2>/dev/null || true
  elif command -v curl &> /dev/null; then
    echo -e "  \033[36m[INFO]\033[0m Installing zoxide via standalone script..."
    curl -sSfL https://raw.githubusercontent.com/ajeetdsouza/zoxide/main/install.sh | sh 2>/dev/null || true
  fi
}

setup_workspace_and_tetravim() {
  echo -e "  \033[1;36m[polyomino]\033[0m Setting up ~/Projects workspace & Tetravim Neovim distribution..."

  # 1. Ensure ~/Projects workspace exists
  mkdir -p "$HOME/Projects"
  echo -e "  \033[32m[OK]\033[0m Workspace directory ready at $HOME/Projects"

  # 2. Clone Tetravim (git@github.com:petrolal/tetravim.nvim.git) with HTTPS fallback
  local tetravim_dir="$HOME/tetravim.nvim"
  local nvim_config_dir="$HOME/.config/nvim"

  if [ ! -d "$tetravim_dir/.git" ]; then
    echo -e "  \033[36m[INFO]\033[0m Cloning Tetravim Neovim distribution..."
    if ! git clone git@github.com:petrolal/tetravim.nvim.git "$tetravim_dir" 2>/dev/null; then
      echo -e "  \033[33m[WARN]\033[0m SSH clone failed (SSH keys not registered). Falling back to HTTPS..."
      git clone https://github.com/petrolal/tetravim.nvim.git "$tetravim_dir" 2>/dev/null || {
        echo -e "  \033[31m[ERROR]\033[0m Could not clone Tetravim repo"
      }
    fi
  else
    echo -e "  \033[32m[OK]\033[0m Tetravim repo already present at $tetravim_dir"
  fi

  # 3. Symlink ~/.config/nvim -> ~/tetravim.nvim
  if [ -d "$tetravim_dir" ]; then
    mkdir -p "$HOME/.config"
    if [ -e "$nvim_config_dir" ] && [ ! -L "$nvim_config_dir" ]; then
      local backup_dir="$HOME/.polyomino_backup/nvim_$(date +%s)"
      mkdir -p "$backup_dir"
      mv "$nvim_config_dir" "$backup_dir/"
      echo -e "  \033[33m[INFO]\033[0m Existing nvim config backed up to $backup_dir"
    fi
    ln -sfn "$tetravim_dir" "$nvim_config_dir"
    echo -e "  \033[32m[OK]\033[0m Linked $nvim_config_dir -> $tetravim_dir"
  fi
}

install_swayfx() {
  local pkg_mgr="$1"
  if command -v sway &> /dev/null && sway --version 2>&1 | grep -iq "swayfx"; then
    echo -e "  \033[32m[OK]\033[0m SwayFX is already installed"
    return
  fi

  echo -e "  \033[1;36m[polyomino]\033[0m Checking/Installing SwayFX ($pkg_mgr)..."
  case "$pkg_mgr" in
    pacman)
      if command -v yay &> /dev/null; then
        echo -e "  \033[36m[INFO]\033[0m Installing SwayFX via yay..."
        if pacman -Qq sway &>/dev/null; then
          echo -e "  \033[36m[INFO]\033[0m Replacing standard Sway with SwayFX..."
          sudo pacman -Rdd --noconfirm sway 2>/dev/null || true
        fi
        yay -S --needed --noconfirm --answerclean None --answerdiff None swayfx 2>/dev/null || true
      elif ! command -v sway &> /dev/null; then
        echo -e "  \033[36m[INFO]\033[0m yay not found; installing standard Sway via pacman..."
        sudo pacman -S --needed --noconfirm sway 2>/dev/null || true
      fi
      ;;
    dnf)
      echo -e "  \033[36m[INFO]\033[0m Enabling SwayFX COPR repository..."
      sudo dnf copr enable -y swayfx/swayfx 2>/dev/null || true
      sudo dnf install -y swayfx 2>/dev/null || true
      ;;
    apt-get)
      echo -e "  \033[36m[INFO]\033[0m Compiling and installing SwayFX from source for Ubuntu..."
      sudo apt-get install -y meson ninja-build libwlroots-dev wayland-protocols libwayland-dev \
        libpango1.0-dev libcairo2-dev libgdk-pixbuf-2.0-dev libjson-c-dev libpcre2-dev libevdev-dev \
        libinput-dev libxkbcommon-dev scdoc cmake git sway 2>/dev/null || true

      local build_dir="$HOME/.cache/polyomino/swayfx"
      mkdir -p "$HOME/.cache/polyomino"
      if [ ! -d "$build_dir/.git" ]; then
        rm -rf "$build_dir"
        git clone --depth 1 --branch 0.4 https://github.com/WillPower3309/swayfx.git "$build_dir" 2>/dev/null || true
      fi
      mkdir -p "$build_dir/subprojects"
      if [ ! -d "$build_dir/subprojects/scenefx/.git" ]; then
        rm -rf "$build_dir/subprojects/scenefx"
        git clone --depth 1 --branch 0.1 https://github.com/wlrfx/scenefx.git "$build_dir/subprojects/scenefx" 2>/dev/null || true
      fi
      if [ -f "$build_dir/meson.build" ]; then
        sed -i "s/subproject(\t'wlroots'/# subproject('wlroots'/g" "$build_dir/meson.build" 2>/dev/null || true
        sed -i "s/subproject(  'wlroots'/# subproject('wlroots'/g" "$build_dir/meson.build" 2>/dev/null || true
        mkdir -p "$HOME/.local/bin"
        if [ ! -f "$build_dir/build/build.ninja" ]; then
          meson setup "$build_dir/build" "$build_dir" --prefix="$HOME/.local" -Dman-pages=disabled -Dtray=disabled "-Dc_link_args=-Wl,-rpath,\$ORIGIN/../lib/x86_64-linux-gnu:\$ORIGIN/../lib" 2>/dev/null || true
        fi
        ninja -C "$build_dir/build" 2>/dev/null || true
        ninja -C "$build_dir/build" install 2>/dev/null || true
        if [ -f "$HOME/.local/bin/sway" ]; then
          echo -e "  \033[32m[OK]\033[0m SwayFX compiled and installed to $HOME/.local/bin/sway"
        fi
      fi
      ;;
  esac
}

install_gaming() {
  local pkg_mgr="$1"
  echo -e "  \033[1;36m[polyomino]\033[0m Installing gaming performance tools & dependencies ($pkg_mgr)..."
  case "$pkg_mgr" in
    pacman)
      sudo pacman -S --needed --noconfirm \
        gamemode gamescope mangohud vulkan-icd-loader vulkan-tools nvidia-prime 2>/dev/null || true
      if pacman -Si lib32-gamemode &>/dev/null; then
        sudo pacman -S --needed --noconfirm lib32-gamemode lib32-mangohud lib32-vulkan-icd-loader 2>/dev/null || true
      fi
      if command -v yay &>/dev/null; then
        yay -S --needed --noconfirm --answerclean None --answerdiff None steam 2>/dev/null || true
      fi
      echo -e "  \033[32m[OK]\033[0m Gaming dependencies installed (gamemode, gamescope, mangohud, vulkan, nvidia-prime)"
      ;;
    dnf)
      sudo dnf install -y gamemode gamescope mangohud vulkan-tools steam 2>/dev/null || true
      echo -e "  \033[32m[OK]\033[0m Gaming dependencies installed"
      ;;
    apt-get)
      sudo apt-get install -y gamemode gamescope mangohud vulkan-tools 2>/dev/null || true
      echo -e "  \033[32m[OK]\033[0m Gaming dependencies installed"
      ;;
    *)
      echo -e "  \033[33m[NOTE]\033[0m Gaming installation skipped for $pkg_mgr"
      ;;
  esac
}

enable_path() {
  if ! echo "$PATH" | grep -q "$BIN_DIR"; then
    echo -e "  \033[36m[INFO]\033[0m Adding $BIN_DIR to PATH..."
    export PATH="$BIN_DIR:$PATH"

    # Also add SDKMan to PATH
    if [ -d "$HOME/.sdkman/candidates/java/current/bin" ]; then
      export PATH="$HOME/.sdkman/candidates/java/current/bin:$PATH"
    elif [ -d "$HOME/.sdkman/bin" ]; then
      export PATH="$HOME/.sdkman/bin:$PATH"
    fi
  fi

  # Cargo binaries in PATH
  if [ -d "$HOME/.cargo/bin" ] && ! echo "$PATH" | grep -q "$HOME/.cargo/bin"; then
    export PATH="$HOME/.cargo/bin:$PATH"
  fi
}

# Main installation flow
PKG_MGR="$(detect_pkg_mgr)"

echo -e "  \033[36m[INFO]\033[0m Package manager: $PKG_MGR"
echo ""

# Prompt or configure optional dependencies
prompt_optional_dependencies

# Install system dependencies (mandatory base + selected optional)
install_system_deps "$PKG_MGR"
echo ""

# Install TUI tools (spotify_player, bluetui, aerc, zoxide) if enabled
if [ "$ENABLE_TUI_TOOLS" = true ]; then
  install_tools "$PKG_MGR"
  echo ""
fi

# Setup ~/Projects and Tetravim Neovim distribution if enabled
if [ "$ENABLE_TETRAVIM" = true ]; then
  setup_workspace_and_tetravim
  echo ""
fi

# Install / Build SwayFX
install_swayfx "$PKG_MGR"
echo ""

# Gaming Stack if enabled
if [ "$ENABLE_GAMING" = true ]; then
  install_gaming "$PKG_MGR"
  echo ""
fi

# Install Java
install_java
echo ""

# Install polyomino native binary & create helper symlinks
install_polyomino_binary
echo ""

# Ensure PATH is set
enable_path
echo ""

# Installation complete
echo -e "\033[1;32m[SUCCESS]\033[0m Bootstrap complete!"
echo ""

# Only show Next Steps when bootstrap.sh is run standalone (not from install.sh)
if [ -z "${POLYOMINO_BOOTSTRAP_CALLED_FROM_INSTALLER:-}" ]; then
  echo -e "\033[1;36m[Next Steps]\033[0m"
  echo -e "  1. Run the interactive installer:"
  echo -e "     \033[33mpolyomino install\033[0m"
  echo ""
  echo -e "  2. Optional: Install or toggle gaming optimization:"
  echo -e "     \033[33mpolyomino install-gaming\033[0m    (install GameMode, Gamescope, MangoHud)"
  echo -e "     \033[33mpolyomino gamemode toggle\033[0m   (toggle Game Mode ON/OFF live)"
  echo ""
  echo -e "  3. Follow the interactive prompts to:"
  echo -e "     - Choose your preferred tools and versions"
  echo -e "     - Deploy dotfiles and symlinks"
  echo -e "     - Run system health check"
  echo ""
  echo -e "\033[1;36m[INFO]\033[0m Ensure \$HOME/.local/bin is in your PATH:"
  echo -e "  \033[33mexport PATH=\\\"$HOME/.local/bin:\$PATH\\\"\033[0m"
  echo ""
fi
