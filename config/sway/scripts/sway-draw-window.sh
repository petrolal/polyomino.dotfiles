#!/usr/bin/env bash
#
# sway-draw-window.sh — Polyomino interactive window geometry drawer using slurp
#
# Usage:
#   sway-draw-window.sh [options] [command...]
#
# Options:
#   -s, --spawn [cmd...]  Draw area and spawn a new floating window (defaults to $term / kitty)
#   -c, --current         Draw area and apply geometry to the currently focused window
#   -h, --help            Show this help message
#
# Behavior when no options given:
#   If a regular window is currently focused, it will be floated, positioned, and resized
#   to the drawn area. If no window is focused (e.g. empty workspace), it spawns a new terminal.

set -euo pipefail

MODE="auto"
TARGET_CMD=()

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -s|--spawn)
            MODE="spawn"
            shift
            if [[ $# -gt 0 ]]; then
                TARGET_CMD=("$@")
                break
            fi
            ;;
        -c|--current)
            MODE="current"
            shift
            ;;
        -h|--help)
            echo "Usage: $(basename "$0") [-s|--spawn [cmd...]] [-c|--current] [-h|--help]"
            echo "Draw interactive window geometry in Sway using slurp."
            exit 0
            ;;
        *)
            # If arguments passed without flag, treat as command to spawn
            MODE="spawn"
            TARGET_CMD=("$@")
            break
            ;;
    esac
done

# Ensure slurp and swaymsg are available
if ! command -v slurp >/dev/null 2>&1; then
    echo "Error: slurp is required but not installed." >&2
    exit 1
fi
if ! command -v swaymsg >/dev/null 2>&1; then
    echo "Error: swaymsg is required but not installed." >&2
    exit 1
fi

# Visual styling matching the Polyomino theme:
# Border: Axé Gold (#EBB434)
# Selection: Semi-transparent Mantle (#191C2488)
# Highlight: Maré Teal (#00D2D3)
SLURP_BG="#191C2488"
SLURP_BORDER="#EBB434ff"
SLURP_SEL="#00D2D344"
SLURP_BOX="#0F1117"

# Run slurp to capture coordinates (x, y, width, height)
# Cancelled selection exits quietly
GEOMETRY=$(slurp -d -F "JetBrainsMono Nerd Font" -b "$SLURP_BG" -c "$SLURP_BORDER" -s "$SLURP_SEL" -B "$SLURP_BOX" -w 2 -f "%x %y %w %h" 2>/dev/null || true)

if [[ -z "$GEOMETRY" ]]; then
    exit 0
fi

read -r X Y W H <<< "$GEOMETRY"

# Guard against accidental click-without-drag (< 30px)
if [[ "$W" -lt 30 || "$H" -lt 30 ]]; then
    W=800
    H=500
    X=$(( X - W / 2 ))
    Y=$(( Y - H / 2 ))
    # Prevent placing off-screen top/left
    [[ "$X" -lt 0 ]] && X=50
    [[ "$Y" -lt 0 ]] && Y=50
fi

# Detect focused window type
FOCUSED_TYPE=""
if command -v jq >/dev/null 2>&1; then
    FOCUSED_TYPE=$(swaymsg -t get_tree 2>/dev/null | jq -r '.. | select(.focused? == true) | .type // empty' | head -n 1 || true)
fi

# Determine action
if [[ "$MODE" == "current" ]] || ([[ "$MODE" == "auto" ]] && [[ "$FOCUSED_TYPE" =~ ^(con|floating_con)$ ]]); then
    # Move and resize the current focused window
    swaymsg "floating enable; move absolute position $X $Y; resize set $W $H" >/dev/null 2>&1
else
    # Spawn a new floating window fitted to the drawn region
    UNIQUE_APP_ID="polyomino-draw-$RANDOM"
    TERM_BIN="${TERM_PROGRAM:-kitty}"
    command -v "$TERM_BIN" >/dev/null 2>&1 || TERM_BIN="kitty"

    if [[ ${#TARGET_CMD[@]} -eq 0 ]]; then
        "$TERM_BIN" --class "$UNIQUE_APP_ID" &
    else
        # If user passed a command (e.g. yazi, btop, or custom shell)
        "$TERM_BIN" --class "$UNIQUE_APP_ID" -e "${TARGET_CMD[@]}" &
    fi

    # Position the spawned window directly without leaking permanent for_window criteria into Sway
    for _ in {1..30}; do
        if swaymsg "[app_id=\"$UNIQUE_APP_ID\"] floating enable, move absolute position $X $Y, resize set $W $H" >/dev/null 2>&1; then
            break
        fi
        sleep 0.02
    done
fi
