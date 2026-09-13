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
BROWSER_MODE=""    # chromium | firefox | both | none
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

# ── Tetris-themed prompt palette ────────────────────────────────────────────
# Matches the sharp box-border / colour-accent aesthetic used across the rest
# of polyomino's TUIs (see PowerMenu): purple frames, cyan/green for active
# selections, yellow for warnings, muted gray for the secondary option.
T_PURPLE=$'\033[38;2;139;92;246m'
T_CYAN=$'\033[1;36m'
T_GREEN=$'\033[1;32m'
T_YELLOW=$'\033[1;33m'
T_RED=$'\033[1;31m'
T_BLUE=$'\033[1;34m'
T_ORANGE=$'\033[38;2;245;158;11m'
T_GRAY=$'\033[2;37m'
T_BOLD=$'\033[1m'
T_RESET=$'\033[0m'

# Sets globals GLYPH_TOP / GLYPH_BOTTOM to the two rows of a colored
# tetromino block glyph. $1 = piece letter (I O T S Z J L), $2 = color code.
_tetromino_glyph() {
  local piece="$1" color="$2"
  case "$piece" in
    I) GLYPH_TOP="${color}■■■■${T_RESET}"; GLYPH_BOTTOM="    " ;;
    O) GLYPH_TOP="${color}■■${T_RESET}"; GLYPH_BOTTOM="${color}■■${T_RESET}" ;;
    T) GLYPH_TOP="${color} ■ ${T_RESET}"; GLYPH_BOTTOM="${color}■■■${T_RESET}" ;;
    S) GLYPH_TOP="${color} ■■${T_RESET}"; GLYPH_BOTTOM="${color}■■ ${T_RESET}" ;;
    Z) GLYPH_TOP="${color}■■ ${T_RESET}"; GLYPH_BOTTOM="${color} ■■${T_RESET}" ;;
    J) GLYPH_TOP="${color}■  ${T_RESET}"; GLYPH_BOTTOM="${color}■■■${T_RESET}" ;;
    L) GLYPH_TOP="${color}  ■${T_RESET}"; GLYPH_BOTTOM="${color}■■■${T_RESET}" ;;
    *) GLYPH_TOP="${color}■■■■${T_RESET}"; GLYPH_BOTTOM="    " ;;
  esac
}

# Non-blocking check: true if we should skip the interactive prompt entirely
# (CI, forced non-interactive, or no controlling terminal at all).
_tetris_noninteractive() {
  [ "${CI:-}" = "1" ] || [ "${NON_INTERACTIVE:-false}" = true ] || { [ ! -t 0 ] && [ ! -e /dev/tty ]; }
}

# prompt_tetris_yn <piece> <color> <label> <default: true|false>
# Draws a "NEXT PIECE" card and asks a Hard Drop (yes) / Hold (skip)
# question. Prints "true" or "false" on stdout (only) so it can be
# captured with `choice="$(prompt_tetris_yn ...)"`; all UI chrome goes
# to stderr.
prompt_tetris_yn() {
  local piece="$1" color="$2" label="$3" default_yes="$4"
  _tetromino_glyph "$piece" "$color"

  {
    echo -e "  ${T_PURPLE}┌─ NEXT PIECE ────────────────────────────────────────────┐${T_RESET}"
    echo -e "  ${T_PURPLE}│${T_RESET}   ${GLYPH_TOP}"
    echo -e "  ${T_PURPLE}│${T_RESET}   ${GLYPH_BOTTOM}"
    echo -e "  ${T_PURPLE}│${T_RESET}"
    echo -e "  ${T_PURPLE}│${T_RESET}   ${T_BOLD}${label}${T_RESET}"
    echo -e "  ${T_PURPLE}│${T_RESET}"
    echo -e "  ${T_PURPLE}│${T_RESET}   ${T_GREEN}[H] Hard Drop${T_RESET} (yes)      ${T_GRAY}[h] Hold${T_RESET} (skip)"
    echo -e "  ${T_PURPLE}└─────────────────────────────────────────────────────────┘${T_RESET}"
  } >&2

  if _tetris_noninteractive; then
    if [ "$default_yes" = true ]; then
      echo -e "  ${T_CYAN}[INFO]${T_RESET} Non-interactive: auto Hard Drop." >&2
      echo "true"
    else
      echo -e "  ${T_GRAY}[INFO]${T_RESET} Non-interactive: auto Hold." >&2
      echo "false"
    fi
    return
  fi

  local hint="[H/h, Enter=Hold]"
  [ "$default_yes" = true ] && hint="[H/h, Enter=Hard Drop]"

  local key=""
  if [ -e /dev/tty ] && [ -r /dev/tty ]; then
    read -r -n1 -p "  Press key ${hint} > " key < /dev/tty || key=""
  else
    read -r -n1 -p "  Press key ${hint} > " key || key=""
  fi
  echo "" >&2

  case "$key" in
    H|Y|y)
      echo -e "  ${T_GREEN}>> Hard drop confirmed: installing.${T_RESET}" >&2
      echo "true"
      ;;
    h|N|n)
      echo -e "  ${T_GRAY}>> Piece held: skipping.${T_RESET}" >&2
      echo "false"
      ;;
    "")
      if [ "$default_yes" = true ]; then
        echo -e "  ${T_GREEN}>> Hard drop confirmed: installing.${T_RESET}" >&2
        echo "true"
      else
        echo -e "  ${T_GRAY}>> Piece held: skipping.${T_RESET}" >&2
        echo "false"
      fi
      ;;
    *)
      if [ "$default_yes" = true ]; then
        echo -e "  ${T_GREEN}>> Hard drop confirmed: installing.${T_RESET}" >&2
        echo "true"
      else
        echo -e "  ${T_GRAY}>> Piece held: skipping.${T_RESET}" >&2
        echo "false"
      fi
      ;;
  esac
}

# prompt_tetromino_select <title> <"label|piece|color"> ...
# Draws a multi-piece selection card and returns the 1-based index of the
# chosen option on stdout. Falls back to option 1 when non-interactive or
# on invalid input.
prompt_tetromino_select() {
  local title="$1"; shift
  local -a labels=() pieces=() colors=()
  local opt lbl pc col
  for opt in "$@"; do
    IFS='|' read -r lbl pc col <<< "$opt"
    labels+=("$lbl"); pieces+=("$pc"); colors+=("$col")
  done

  {
    echo -e "  ${T_PURPLE}┌─ ${title} ─────────────────────────────────────────┐${T_RESET}"
    local idx
    for idx in "${!labels[@]}"; do
      _tetromino_glyph "${pieces[$idx]}" "${colors[$idx]}"
      echo -e "  ${T_PURPLE}│${T_RESET}   ${T_BOLD}[$((idx + 1))]${T_RESET} ${GLYPH_TOP}  ${labels[$idx]}"
      echo -e "  ${T_PURPLE}│${T_RESET}       ${GLYPH_BOTTOM}"
    done
    echo -e "  ${T_PURPLE}└────────────────────────────────────────────────────────┘${T_RESET}"
  } >&2

  if _tetris_noninteractive; then
    echo -e "  ${T_CYAN}[INFO]${T_RESET} Non-interactive: defaulting to [1] ${labels[0]}." >&2
    echo "1"
    return
  fi

  local key=""
  if [ -e /dev/tty ] && [ -r /dev/tty ]; then
    read -r -n1 -p "  Rotate & drop [1-${#labels[@]}] > " key < /dev/tty || key=""
  else
    read -r -n1 -p "  Rotate & drop [1-${#labels[@]}] > " key || key=""
  fi
  echo "" >&2

  if [[ "$key" =~ ^[0-9]$ ]] && [ "$key" -ge 1 ] && [ "$key" -le "${#labels[@]}" ]; then
    echo -e "  ${T_GREEN}>> Line clear! Selected: ${labels[$((key - 1))]}${T_RESET}" >&2
    echo "$key"
  else
    echo -e "  ${T_YELLOW}[WARN]${T_RESET} Invalid input, defaulting to [1] ${labels[0]}." >&2
    echo "1"
  fi
}

prompt_optional_dependencies() {
  if [ "$ENABLE_ALL" = true ]; then
    ENABLE_TETRAVIM=true
    ENABLE_BROWSER=true
    BROWSER_MODE="${BROWSER_MODE:-both}"
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
    BROWSER_MODE="${BROWSER_MODE:-none}"
    ENABLE_TUI_TOOLS="${ENABLE_TUI_TOOLS:-false}"
    ENABLE_DEVOPS="${ENABLE_DEVOPS:-false}"
    ENABLE_DEV_RUNTIMES="${ENABLE_DEV_RUNTIMES:-false}"
    ENABLE_DESKTOP_APPS="${ENABLE_DESKTOP_APPS:-false}"
    ENABLE_GAMING="${ENABLE_GAMING:-false}"
    return
  fi

  echo -e "  ${T_PURPLE}[polyomino]${T_RESET} Configuring non-obligatory dependency installations:"
  echo -e "  ${T_GRAY}A piece is falling for each optional dependency. Hard Drop to install, Hold to skip.${T_RESET}"
  echo ""

  # 1. Neovim & Tetravim
  if [ -z "$ENABLE_TETRAVIM" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_TETRAVIM=true
    else
      choice="$(prompt_tetris_yn I "$T_CYAN" "Install Neovim & Tetravim distribution?" true)"
      ENABLE_TETRAVIM="$choice"
    fi
  fi
  echo ""

  # 2. Web Browser (multi-select: which browser(s) to drop in)
  if [ -z "$BROWSER_MODE" ] && [ -n "$ENABLE_BROWSER" ]; then
    # Set explicitly via CLI flag (--browser / --no-browser); skip the picker.
    if [ "$ENABLE_BROWSER" = true ]; then BROWSER_MODE="both"; else BROWSER_MODE="none"; fi
  fi
  if [ -z "$BROWSER_MODE" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      BROWSER_MODE="both"
    else
      sel="$(prompt_tetromino_select "HOLD: BROWSER" \
        "Chromium|O|$T_YELLOW" \
        "Firefox|O|$T_ORANGE" \
        "Both|I|$T_CYAN" \
        "Skip|Z|$T_GRAY")"
      case "$sel" in
        1) BROWSER_MODE="chromium" ;;
        2) BROWSER_MODE="firefox" ;;
        3) BROWSER_MODE="both" ;;
        *) BROWSER_MODE="none" ;;
      esac
    fi
  fi
  if [ "$BROWSER_MODE" = "none" ]; then
    ENABLE_BROWSER=false
  else
    ENABLE_BROWSER=true
  fi
  echo ""

  # 3. TUI Productivity Tools
  if [ -z "$ENABLE_TUI_TOOLS" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_TUI_TOOLS=true
    else
      choice="$(prompt_tetris_yn T "$T_GREEN" "Install TUI tools (spotify_player, bluetui, impala, aerc, zoxide, fastfetch)?" true)"
      ENABLE_TUI_TOOLS="$choice"
    fi
  fi
  echo ""

  # 4. DevOps & Cloud Tools
  if [ -z "$ENABLE_DEVOPS" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_DEVOPS=false
    else
      choice="$(prompt_tetris_yn S "$T_GREEN" "Install DevOps tools (Docker, Terraform, Ansible, kubectl, Helm, cloud CLIs)?" false)"
      ENABLE_DEVOPS="$choice"
    fi
  fi
  echo ""

  # 5. Developer Runtimes
  if [ -z "$ENABLE_DEV_RUNTIMES" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_DEV_RUNTIMES=false
    else
      choice="$(prompt_tetris_yn Z "$T_RED" "Install Developer runtimes (Node.js/npm via NVM, SDKMAN! & Kotlin)?" false)"
      ENABLE_DEV_RUNTIMES="$choice"
    fi
  fi
  echo ""

  # 6. Desktop Apps (Telegram)
  if [ -z "$ENABLE_DESKTOP_APPS" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_DESKTOP_APPS=false
    else
      choice="$(prompt_tetris_yn J "$T_BLUE" "Install Telegram Desktop?" false)"
      ENABLE_DESKTOP_APPS="$choice"
    fi
  fi
  echo ""

  # 7. Gaming Performance Stack
  if [ -z "$ENABLE_GAMING" ]; then
    if [ "$NON_INTERACTIVE" = true ]; then
      ENABLE_GAMING=false
    else
      choice="$(prompt_tetris_yn L "$T_ORANGE" "Install gaming optimizations & tools (gamemode, gamescope, mangohud, steam)?" false)"
      ENABLE_GAMING="$choice"
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
  local browser_pkgs_pacman="" browser_pkgs_apt="" browser_pkgs_dnf=""
  case "$BROWSER_MODE" in
    chromium) browser_pkgs_pacman="chromium"; browser_pkgs_apt="chromium-browser"; browser_pkgs_dnf="" ;;
    firefox)  browser_pkgs_pacman="firefox"; browser_pkgs_apt="firefox"; browser_pkgs_dnf="firefox" ;;
    both)     browser_pkgs_pacman="chromium firefox"; browser_pkgs_apt="firefox chromium-browser"; browser_pkgs_dnf="firefox" ;;
    *)        browser_pkgs_pacman=""; browser_pkgs_apt=""; browser_pkgs_dnf="" ;;
  esac

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
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs $browser_pkgs_pacman"
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
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs $browser_pkgs_apt"
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
      [ "$ENABLE_BROWSER" = true ] && opt_pkgs="$opt_pkgs $browser_pkgs_dnf"
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
