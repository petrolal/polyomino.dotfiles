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

# 2. Select project with installed launcher (Wofi / Rofi / Fzf)
choose_project() {
  local project_list
  project_list=$(collect_projects)

  [ -z "$project_list" ] && exit 0

  # Pretty display with ~
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

choose_project
