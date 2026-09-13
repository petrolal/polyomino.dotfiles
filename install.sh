#!/usr/bin/env bash
# polyomino.dotfiles One-Shot Web Installer
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/petrolal/polyomino.dotfiles/master/install.sh | bash
#   curl -fsSL https://raw.githubusercontent.com/petrolal/polyomino.dotfiles/master/install.sh | bash -s -- --gaming
set -euo pipefail

# Visual branding
BOLD="\033[1m"
CYAN="\033[1;36m"
GREEN="\033[1;32m"
YELLOW="\033[1;33m"
RED="\033[1;31m"
RESET="\033[0m"

echo -e "${CYAN}"
cat << 'EOF'
 ▄▄▄▄▄▄▄▄▄▄▄  ▄▄▄▄▄▄▄▄▄▄▄  ▄            ▄▄▄▄▄▄▄▄▄▄▄  ▄▄▄▄▄▄▄▄▄▄▄  ▄▄▄▄▄▄▄▄▄▄▄  ▄         ▄  ▄▄▄▄▄▄▄▄▄▄▄ 
▐░░░░░░░░░░░▌▐░░░░░░░░░░░▌▐░▌          ▐░░░░░░░░░░░▌▐░░░░░░░░░░░▌▐░░░░░░░░░░░▌▐░▌       ▐░▌▐░░░░░░░░░░░▌
▐░█▀▀▀▀▀▀▀█░▌▐░█▀▀▀▀▀▀▀█░▌▐░▌          ▐░█▀▀▀▀▀▀▀█░▌▐░█▀▀▀▀▀▀▀█░▌▐░█▀▀▀▀▀▀▀█░▌▐░▌       ▐░▌▐░█▀▀▀▀▀▀▀█░▌
▐░▌       ▐░▌▐░▌       ▐░▌▐░▌          ▐░▌       ▐░▌▐░▌       ▐░▌▐░▌       ▐░▌▐░▌       ▐░▌▐░▌       ▐░▌
▐░█▄▄▄▄▄▄▄█░▌▐░▌       ▐░▌▐░▌          ▐░▌       ▐░▌▐░█▄▄▄▄▄▄▄█░▌▐░▌       ▐░▌▐░▌   ▄   ▐░▌▐░▌       ▐░▌
▐░░░░░░░░░░░▌▐░▌       ▐░▌▐░▌          ▐░▌       ▐░▌▐░░░░░░░░░░░▌▐░▌       ▐░▌▐░▌  ▐░▌  ▐░▌▐░▌       ▐░▌
▐░█▀▀▀▀▀▀▀▀▀ ▐░▌       ▐░▌▐░▌          ▐░▌       ▐░▌▐░█▀▀▀▀▀▀▀▀▀ ▐░▌       ▐░▌▐░▌ ▐░▌░▌ ▐░▌▐░▌       ▐░▌
▐░▌          ▐░▌       ▐░▌▐░▌          ▐░▌       ▐░▌▐░▌          ▐░▌       ▐░▌▐░▌▐░▌ ▐░▌▐░▌▐░▌       ▐░▌
▐░▌          ▐░█▄▄▄▄▄▄▄█░▌▐░█▄▄▄▄▄▄▄▄▄ ▐░█▄▄▄▄▄▄▄█░▌▐░▌          ▐░█▄▄▄▄▄▄▄█░▌▐░▌▐░▌  ▐░▐░▌▐░█▄▄▄▄▄▄▄█░▌
▐░▌          ▐░░░░░░░░░░░▌▐░░░░░░░░░░░▌▐░░░░░░░░░░░▌▐░▌          ▐░░░░░░░░░░░▌▐░░░▌   ▐░░▌▐░░░░░░░░░░░▌
 ▀            ▀▀▀▀▀▀▀▀▀▀▀  ▀▀▀▀▀▀▀▀▀▀▀  ▀▀▀▀▀▀▀▀▀▀▀  ▀            ▀▀▀▀▀▀▀▀▀▀▀  ▀▀▀     ▀▀▀  ▀▀▀▀▀▀▀▀▀▀▀ 
EOF
echo -e "${RESET}"
echo -e "${CYAN}[polyomino installer]${RESET} Automated One-Shot Deployment & Installer"
echo ""

DOTFILES_DIR="${POLYOMINO_DOTFILES_DIR:-$HOME/polyomino.dotfiles}"
BIN_DIR="$HOME/.local/bin"
REPO_URL="${POLYOMINO_REPO_URL:-https://github.com/petrolal/polyomino.dotfiles.git}"
SSH_REPO_URL="git@github.com:petrolal/polyomino.dotfiles.git"
BRANCH="${POLYOMINO_BRANCH:-master}"

mkdir -p "$BIN_DIR"
export PATH="$BIN_DIR:$PATH"

# Step 1: Ensure Git is available
if ! command -v git &> /dev/null; then
  echo -e "  ${CYAN}[polyomino]${RESET} Installing Git..."
  if command -v pacman &> /dev/null; then
    sudo pacman -S --needed --noconfirm git
  elif command -v apt-get &> /dev/null; then
    sudo apt-get update && sudo apt-get install -y git
  elif command -v dnf &> /dev/null; then
    sudo dnf install -y git
  elif command -v brew &> /dev/null; then
    brew install git
  else
    echo -e "  ${RED}[ERROR]${RESET} Git is required to continue. Please install git and re-run."
    exit 1
  fi
fi

# Step 2: Clone or Update the repository to ~/polyomino.dotfiles
echo -e "  ${CYAN}[1/4] Cloning polyomino.dotfiles repository...${RESET}"
if [ ! -d "$DOTFILES_DIR/.git" ]; then
  if [ -d "$DOTFILES_DIR" ]; then
    echo -e "  ${YELLOW}[WARN]${RESET} Directory $DOTFILES_DIR exists but is not a git repo. Backing up..."
    mv "$DOTFILES_DIR" "${DOTFILES_DIR}.bak.$(date +%s)"
  fi
  # Try SSH clone first if keys are configured, fallback to HTTPS
  if ! git clone --branch "$BRANCH" "$SSH_REPO_URL" "$DOTFILES_DIR" 2>/dev/null; then
    echo -e "  ${YELLOW}[INFO]${RESET} SSH clone unavailable; cloning via HTTPS..."
    git clone --branch "$BRANCH" "$REPO_URL" "$DOTFILES_DIR"
  fi
  echo -e "  ${GREEN}[OK]${RESET} Cloned repository to $DOTFILES_DIR"
else
  echo -e "  ${GREEN}[OK]${RESET} Repository already exists at $DOTFILES_DIR. Pulling latest updates..."
  git -C "$DOTFILES_DIR" pull --ff-only 2>/dev/null || true
fi

# Step 3: Execute bootstrap.sh
echo ""
echo -e "  ${CYAN}[2/4] Executing bootstrap.sh (system packages, swayfx, zoxide, projects & tetravim)...${RESET}"
chmod +x "$DOTFILES_DIR/bootstrap.sh"
"$DOTFILES_DIR/bootstrap.sh" "$@"

# Refresh shell environment paths in current runner
if [ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  set +u
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  set -u
fi

if [ -d "$HOME/.sdkman/candidates/java/current/bin" ]; then
  export PATH="$HOME/.sdkman/candidates/java/current/bin:$PATH"
fi
if [ -d "$HOME/.sdkman/candidates/sbt/current/bin" ]; then
  export PATH="$HOME/.sdkman/candidates/sbt/current/bin:$PATH"
fi
if [ -d "$HOME/.cargo/bin" ]; then
  export PATH="$HOME/.cargo/bin:$PATH"
fi

# Step 4: Compile Polyomino binary
echo ""
echo -e "  ${CYAN}[3/4] Compiling Polyomino native binary (sbt nativeImage)...${RESET}"
cd "$DOTFILES_DIR"

if command -v sbt &> /dev/null; then
  sbt nativeImage
elif command -v cs &> /dev/null; then
  echo -e "  ${YELLOW}[INFO]${RESET} Using Coursier sbt launcher..."
  cs launch sbt -- nativeImage
else
  echo -e "  ${RED}[ERROR]${RESET} Neither sbt nor cs found to build polyomino binary."
  exit 1
fi

COMPILED_BIN="$DOTFILES_DIR/target/native-image/polyomino"
if [ ! -f "$COMPILED_BIN" ]; then
  echo -e "  ${RED}[ERROR]${RESET} Compilation failed: $COMPILED_BIN not found."
  exit 1
fi

cp -f "$COMPILED_BIN" "$BIN_DIR/polyomino"
chmod +x "$BIN_DIR/polyomino"
echo -e "  ${GREEN}[OK]${RESET} Polyomino binary installed to $BIN_DIR/polyomino"

# Step 5: Execute Polyomino Installer
echo ""
echo -e "  ${CYAN}[4/4] Executing Polyomino Installer & Deployer...${RESET}"
"$BIN_DIR/polyomino" install "$@"

echo ""
echo -e "${GREEN}${BOLD}════════════════════════════════════════════════════════════════${RESET}"
echo -e "${GREEN}${BOLD}  polyomino.dotfiles installation completed successfully!       ${RESET}"
echo -e "${GREEN}${BOLD}════════════════════════════════════════════════════════════════${RESET}"
echo ""
echo -e "  Next steps:"
echo -e "    1. Start or reload Sway:  ${YELLOW}swaymsg reload${RESET} (or log in to Sway session)"
echo -e "    2. Launch project picker:  ${YELLOW}Super + P${RESET}"
echo -e "    3. Change themes:          ${YELLOW}polyomino-theme-picker${RESET} (or ${YELLOW}Super + Shift + T${RESET})"
echo -e "    4. Open terminal:          ${YELLOW}Super + Enter${RESET}"
echo ""
