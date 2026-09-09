#!/usr/bin/env bash
# polyomino.dotfiles Bootstrap Installer
# Minimal installer: Installs Java & Coursier only
# Full setup is handled by: polyomino install
set -euo pipefail

echo -e "\033[1;36m[polyomino bootstrap]\033[0m Starting polyomino.dotfiles installer..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$HOME/.local/bin"

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

  case "$pkg_mgr" in
    pacman)
      # Core system + desktop + dev tools
      sudo pacman -S --needed --noconfirm \
        base-devel git curl wget \
        zsh fontconfig fastfetch cmatrix \
        sway waybar kitty wofi swaylock gtklock swayidle grim slurp \
        brightnessctl libpulse playerctl wireplumber swaync mako \
        chromium firefox \
        neovim \
        docker \
        ttf-jetbrains-mono-nerd
      echo -e "  \033[32m[OK]\033[0m System packages installed"
      ;;
    apt-get)
      sudo apt-get update
      sudo apt-get install -y \
        build-essential git curl wget \
        zsh fontconfig fastfetch cmatrix \
        sway waybar kitty wofi swaylock swayidle grim slurp \
        brightnessctl playerctl wireplumber pulseaudio-utils sway-notification-center mako-notifier \
        python3-gi python3-cairo gir1.2-gtk-3.0 \
        firefox chromium-browser \
        neovim \
        docker.io \
        fonts-jetbrains-mono
      echo -e "  \033[32m[OK]\033[0m System packages installed"
      ;;
    dnf)
      sudo dnf install -y \
        gcc gcc-c++ git curl wget \
        zsh fontconfig fastfetch cmatrix \
        sway waybar kitty wofi swaylock swayidle grim slurp \
        brightnessctl playerctl wireplumber pulseaudio-libs sway-notification-center mako \
        firefox \
        neovim \
        docker
      echo -e "  \033[32m[OK]\033[0m System packages installed"
      ;;
    *)
      echo -e "  \033[33m[NOTE]\033[0m Package manager '$pkg_mgr' not automatically managed. Skipping installation."
      ;;
  esac
}

install_java() {
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

  bash -c "
    source $HOME/.sdkman/bin/sdkman-init.sh
    sdk install java 21.0.1-graal --default 2>/dev/null || true
  "

  if command -v java &> /dev/null; then
    echo -e "  \033[32m[OK]\033[0m Java installed via SDKMan"
    java -version 2>&1 | head -1
  else
    echo -e "  \033[31m[ERROR]\033[0m Java installation failed"
    exit 1
  fi
}

install_coursier() {
  if command -v cs &> /dev/null; then
    echo -e "  \033[32m[OK]\033[0m Coursier already installed"
    cs --version 2>/dev/null || true
    return
  fi

  echo -e "  \033[1;36m[polyomino]\033[0m Installing Coursier..."

  mkdir -p "$BIN_DIR"

  case "$(uname -s)" in
    Linux)
      COURSIER_FILE="cs-x86_64-pc-linux.gz"
      ;;
    Darwin)
      COURSIER_FILE="cs-x86_64-apple-darwin.gz"
      ;;
    *)
      echo -e "  \033[33m[NOTE]\033[0m Unsupported platform for Coursier installation"
      return 1
      ;;
  esac

  curl -fL "https://github.com/coursier/launchers/raw/master/$COURSIER_FILE" | gzip -d > "$BIN_DIR/cs"
  chmod +x "$BIN_DIR/cs"

  echo -e "  \033[32m[OK]\033[0m Coursier installed to $BIN_DIR/cs"
  "$BIN_DIR/cs" update 2>/dev/null || true
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
}

enable_path() {
  if ! echo "$PATH" | grep -q "$BIN_DIR"; then
    echo -e "  \033[36m[INFO]\033[0m Adding $BIN_DIR to PATH..."
    export PATH="$BIN_DIR:$PATH"

    # Also add SDKMan to PATH
    if [ -d "$HOME/.sdkman/bin" ]; then
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

# Install system dependencies
install_system_deps "$PKG_MGR"
echo ""

# Install TUI tools (spotify_player, bluetui)
install_tools "$PKG_MGR"
echo ""

# Install Java
install_java
echo ""

# Install Coursier
install_coursier
echo ""

# Ensure PATH is set
enable_path
echo ""

# Installation complete
echo -e "\033[1;32m[SUCCESS]\033[0m Bootstrap complete!"
echo ""
echo -e "\033[1;36m[Next Steps]\033[0m"
echo -e "  1. Install polyomino from Maven Central:"
echo -e "     \033[33mcs bootstrap io.github.petrolal::polyomino:0.1.0 -o ~/.local/bin/polyomino\033[0m"
echo ""
echo -e "  2. Run the interactive installer:"
echo -e "     \033[33mpolyomino install\033[0m"
echo ""
echo -e "  3. Follow the interactive prompts to:"
echo -e "     - Choose your preferred tools and versions"
echo -e "     - Deploy dotfiles and symlinks"
echo -e "     - Run system health check"
echo ""
echo -e "\033[1;36m[INFO]\033[0m Ensure \$HOME/.local/bin is in your PATH:"
echo -e "  \033[33mexport PATH=\\\"\$HOME/.local/bin:\$PATH\\\"\033[0m"
echo ""
