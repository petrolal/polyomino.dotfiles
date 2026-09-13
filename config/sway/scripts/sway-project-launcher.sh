#!/usr/bin/env bash
# ~/.local/bin/sway-project-launcher
# Project picker launcher for Sway + Kitty + Neovim
set -euo pipefail

# 1. Collect candidate project directories
collect_projects() {
  local search_roots=(
    "$HOME/Projects"
    "$HOME/polyomino.dotfiles"
    "$HOME/tetravim.nvim"
    "$HOME/.config"
  )

  # Check optional common project locations if they exist
  for extra in "$HOME/dev" "$HOME/code" "$HOME/src" "$HOME/workspace" "$HOME/repos" "$HOME/Documents/Projects"; do
    if [ -d "$extra" ]; then
      search_roots+=("$extra")
    fi
  done

  local found_dirs=()

  # Find git repositories
  if command -v fd >/dev/null 2>&1; then
    while IFS= read -r dir; do
      [ -d "$dir" ] && found_dirs+=("$dir")
    done < <(fd -H '^\.git$' -t d -d 5 "${search_roots[@]}" 2>/dev/null -x dirname {} | sort -u)
  else
    while IFS= read -r dir; do
      [ -d "$dir" ] && found_dirs+=("$dir")
    done < <(find "${search_roots[@]}" -maxdepth 5 -name .git -type d 2>/dev/null | sed 's#/\.git$##' | sort -u)
  fi

  # Also include category/project directories under ~/Projects
  if [ -d "$HOME/Projects" ]; then
    while IFS= read -r dir; do
      [ -d "$dir" ] && found_dirs+=("$dir")
    done < <(find "$HOME/Projects" -mindepth 1 -maxdepth 2 -type d 2>/dev/null)
  fi

  # Include explicit dotfiles / config directories
  for root in "$HOME/polyomino.dotfiles" "$HOME/tetravim.nvim" "$HOME/.config"; do
    if [ -d "$root" ]; then
      found_dirs+=("$root")
    fi
  done

  # Print sorted, unique project directory list
  printf "%s\n" "${found_dirs[@]}" | sort -u
}

DOTFILES_DIR="${POLYOMINO_DOTFILES_DIR:-$HOME/polyomino.dotfiles}"
TILEMENU_SCRIPT="$DOTFILES_DIR/config/sway/scripts/polyomino-tilemenu.py"
[ -f "$TILEMENU_SCRIPT" ] || TILEMENU_SCRIPT="${XDG_CONFIG_HOME:-$HOME/.config}/sway/scripts/polyomino-tilemenu.py"

# 2a. Preferred picker: the shared glass-blur GTK tile grid
choose_project_tilemenu() {
  local project_list="$1"

  # Toggle: a second invocation while the grid is open closes it instead of
  # opening a second one, matching the other tile-menu pickers.
  local pids
  pids=$(pgrep -f "polyomino-tilemenu.*PROJECTS" || true)
  if [ -n "$pids" ]; then
    kill $pids 2>/dev/null || true
    exit 0
  fi

  local selected_path
  selected_path=$(
    HOME="$HOME" python3 - "$project_list" <<'PYEOF'
import json
import os
import subprocess
import sys

paths = [p for p in sys.argv[1].splitlines() if p]
home = os.environ["HOME"]

accents = ["blue", "teal", "green", "peach", "mauve", "sapphire", "sky", "yellow"]
tiles = []
for i, path in enumerate(paths):
    display = path.replace(home, "~", 1) if path.startswith(home) else path
    tiles.append({
        "id": path,
        "icon": "",
        "title": os.path.basename(path.rstrip("/")) or display,
        "desc": display,
        "accent": accents[i % len(accents)],
    })

proc = subprocess.run(
    ["python3", os.environ.get("POLYOMINO_TILEMENU_SCRIPT", ""), "--title", "[ ⊞ ] PROJECTS",
     "--columns", "3", "--width", "760", "--height", "520"],
    input=json.dumps(tiles), capture_output=True, text=True,
)
sys.stdout.write(proc.stdout.strip())
PYEOF
  )

  [ -z "$selected_path" ] && exit 0

  if [ -d "$selected_path" ]; then
    exec kitty --class nvim-project -d "$selected_path" nvim .
  fi
  exit 0
}

# 2b. Fallback picker chain (Wofi / Rofi / Fzf) for environments without
# python3/GTK available
choose_project_fallback() {
  local project_list="$1"

  local display_list
  display_list=$(echo "$project_list" | sed "s|^$HOME|~|")

  local selected=""

  if command -v wofi >/dev/null 2>&1; then
    local wofi_opts=(
      --show dmenu
      --prompt "[ ⊞ ] PROJECTS"
      --insensitive
      --width 850
      --lines 12
      --cache-file /dev/null
    )
    [ -f "$HOME/.config/wofi/config" ] && wofi_opts+=(--conf "$HOME/.config/wofi/config")
    [ -f "$HOME/.config/wofi/style.css" ] && wofi_opts+=(--style "$HOME/.config/wofi/style.css")

    selected=$(echo "$display_list" | wofi "${wofi_opts[@]}" 2>/dev/null || true)
  elif command -v rofi >/dev/null 2>&1; then
    selected=$(echo "$display_list" | rofi -dmenu -i -p "Projects" 2>/dev/null || true)
  elif command -v fzf >/dev/null 2>&1; then
    selected=$(echo "$display_list" | fzf --prompt="Projects: " 2>/dev/null || true)
  else
    echo "No launcher found (wofi, rofi, fzf)" >&2
    exit 1
  fi

  # Exit if nothing was selected
  [ -z "$selected" ] && exit 0

  # Expand ~ back to $HOME
  local selected_path="${selected/#\~/$HOME}"

  if [ -d "$selected_path" ]; then
    exec kitty --class nvim-project -d "$selected_path" nvim .
  fi
}

# 3. Select project
choose_project() {
  local project_list
  project_list=$(collect_projects)

  [ -z "$project_list" ] && exit 0

  if command -v python3 >/dev/null 2>&1 && [ -f "$TILEMENU_SCRIPT" ] && python3 -c "import gi; gi.require_version('Gtk', '3.0'); from gi.repository import Gtk" >/dev/null 2>&1; then
    POLYOMINO_TILEMENU_SCRIPT="$TILEMENU_SCRIPT" choose_project_tilemenu "$project_list"
  else
    choose_project_fallback "$project_list"
  fi
}

choose_project
