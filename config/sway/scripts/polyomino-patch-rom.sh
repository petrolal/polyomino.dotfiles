#!/usr/bin/env bash
# polyomino-patch-rom.sh — one-click IPS/BPS patcher for practice/tournament ROM hacks
# (Tetris Gym, DAS Trainer, etc.) via `flips`. No frontend, just a CLI pipeline.
#
# Usage:
#   polyomino-patch-rom.sh <base_rom> <patch.ips|patch.bps>
#   polyomino-patch-rom.sh --auto <base_rom> [patch_dir]   (default patch_dir: ~/Downloads)
set -euo pipefail

require_flips() {
  command -v flips >/dev/null 2>&1 || {
    echo "polyomino-patch-rom: 'flips' not found in PATH" >&2
    exit 1
  }
}

apply_patch() {
  local base_rom="$1" patch_file="$2"
  [ -f "$base_rom" ] || { echo "polyomino-patch-rom: base ROM not found: $base_rom" >&2; exit 1; }
  [ -f "$patch_file" ] || { echo "polyomino-patch-rom: patch not found: $patch_file" >&2; exit 1; }

  local dir base ext out
  dir="$(dirname "$base_rom")"
  base="$(basename "$base_rom")"
  ext="${base##*.}"
  base="${base%.*}"
  out="$dir/${base}-patched.${ext}"

  require_flips
  flips --apply "$patch_file" "$base_rom" "$out"
  echo "$out"
}

auto_patch() {
  local base_rom="$1" patch_dir="${2:-$HOME/Downloads}"
  [ -d "$patch_dir" ] || { echo "polyomino-patch-rom: patch dir not found: $patch_dir" >&2; exit 1; }

  local latest
  latest="$(find "$patch_dir" -maxdepth 1 -type f \( -iname '*.ips' -o -iname '*.bps' \) \
    -printf '%T@ %p\n' | sort -rn | head -n1 | cut -d' ' -f2-)"
  [ -n "$latest" ] || { echo "polyomino-patch-rom: no .ips/.bps patch found in $patch_dir" >&2; exit 1; }

  apply_patch "$base_rom" "$latest"
}

case "${1:-}" in
  --auto)
    shift
    [ "${1:-}" ] || { echo "Usage: $0 --auto <base_rom> [patch_dir]" >&2; exit 1; }
    if [ "${2:-}" ]; then auto_patch "$1" "$2"; else auto_patch "$1"; fi
    ;;
  -h|--help|"")
    echo "Usage: $0 <base_rom> <patch_file> | --auto <base_rom> [patch_dir]" >&2
    exit 1
    ;;
  *)
    [ "${2:-}" ] || { echo "Usage: $0 <base_rom> <patch_file>" >&2; exit 1; }
    apply_patch "$1" "$2"
    ;;
esac
