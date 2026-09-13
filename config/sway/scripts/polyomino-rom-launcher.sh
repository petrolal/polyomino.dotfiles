#!/usr/bin/env bash
# polyomino-rom-launcher.sh — lightweight ROM scanner + direct-boot launcher.
# No scraping, no metadata daemon: just directory globbing + native emulator invocation.
#
# Usage:
#   polyomino-rom-launcher.sh --list        tab-separated: platform<TAB>title<TAB>path
#   polyomino-rom-launcher.sh --rofi        interactive rofi dmenu picker; launches selection
#   polyomino-rom-launcher.sh <rom_path>    launch a specific ROM directly
set -euo pipefail

ROMS_ROOT="${POLYOMINO_ROMS_DIR:-$HOME/Games/ROMs}"

# platform (= ROMs/<platform>/ directory name) -> command to run, {ROM} substituted
emulator_cmd() {
  local platform="$1" rom="$2"
  case "$platform" in
    nes) printf 'mesen %q' "$rom" ;;
    gb|gbc) printf 'sameboy %q' "$rom" ;;
    gba) printf 'mgba-qt %q' "$rom" ;;
    snes) printf 'bsnes %q' "$rom" ;;
    arcade) printf 'mame -rompath %q %q' "$(dirname "$rom")" "$(basename "${rom%.*}")" ;;
    *) return 1 ;;
  esac
}

# Strip extension, bracketed/parenthesized region-code tags, underscores/dots -> spaces
clean_title() {
  local base
  base="$(basename "$1")"
  base="${base%.*}"
  base="$(printf '%s' "$base" | sed -E 's/[][()][^][()]*[])]//g; s/[_.]+/ /g; s/ +/ /g')"
  printf '%s' "$base" | sed -E 's/^ +| +$//g'
}

list_roms() {
  [ -d "$ROMS_ROOT" ] || return 0
  local platform_dir platform rom
  for platform_dir in "$ROMS_ROOT"/*/; do
    [ -d "$platform_dir" ] || continue
    platform="$(basename "$platform_dir")"
    while IFS= read -r -d '' rom; do
      printf '%s\t%s\t%s\n' "$platform" "$(clean_title "$rom")" "$rom"
    done < <(find "$platform_dir" -maxdepth 1 -type f \( \
        -iname '*.nes' -o -iname '*.sfc' -o -iname '*.smc' -o -iname '*.gb' \
        -o -iname '*.gbc' -o -iname '*.gba' -o -iname '*.zip' -o -iname '*.chd' \
      \) -print0 | sort -z)
  done
}

launch_rom() {
  local rom="$1"
  [ -f "$rom" ] || { echo "polyomino-rom-launcher: not found: $rom" >&2; exit 1; }
  local platform cmd
  platform="$(basename "$(dirname "$rom")")"
  cmd="$(emulator_cmd "$platform" "$rom")" || {
    echo "polyomino-rom-launcher: no emulator mapped for platform '$platform'" >&2
    exit 1
  }
  setsid bash -c "$cmd" >/dev/null 2>&1 &
  disown
}

rofi_pick() {
  mapfile -t rows < <(list_roms)
  if [ "${#rows[@]}" -eq 0 ]; then
    command -v notify-send >/dev/null 2>&1 && notify-send "ROM Launcher" "No ROMs found under $ROMS_ROOT"
    exit 0
  fi
  local menu=""
  local platform title path row
  for row in "${rows[@]}"; do
    IFS=$'\t' read -r platform title path <<<"$row"
    menu+="$(printf '%-8s %s' "$platform" "$title")"$'\n'
  done
  local idx=""
  if command -v rofi >/dev/null 2>&1; then
    idx="$(printf '%s' "$menu" | rofi -dmenu -i -p "ROMs" -format i 2>/dev/null)" || exit 0
  elif command -v wofi >/dev/null 2>&1; then
    idx="$(printf '%s' "$menu" | wofi --dmenu --prompt "ROMs" --index 2>/dev/null)" || exit 0
  elif command -v fuzzel >/dev/null 2>&1; then
    idx="$(printf '%s' "$menu" | fuzzel --dmenu --index -p "ROMs: " 2>/dev/null)" || exit 0
  fi
  [ -n "$idx" ] || exit 0
  IFS=$'\t' read -r platform title path <<<"${rows[$idx]}"
  launch_rom "$path"
}

case "${1:-}" in
  --list) list_roms ;;
  --rofi) rofi_pick ;;
  -h|--help|"") echo "Usage: $0 [--list|--rofi|<rom_path>]" >&2; exit 1 ;;
  *) launch_rom "$1" ;;
esac
