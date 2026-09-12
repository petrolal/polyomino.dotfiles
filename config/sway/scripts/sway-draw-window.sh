#!/usr/bin/env bash
#
# sway-draw-window.sh — Polyomino interactive window geometry drawer using slurp
#
# Usage:
#   sway-draw-window.sh [options] [command...]
#
# Options:
#   -s, --spawn [cmd...]  Draw area and spawn a new floating window (defaults to kitty/terminal)
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
    W=900
    H=550
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
    swaymsg "floating enable; resize set $W $H; move absolute position $X $Y" >/dev/null 2>&1
else
    # Generate unique ID for this drawn window instance
    UNIQUE_ID="sway_drawn_$RANDOM$RANDOM"

    # Pre-register the for_window rule in Sway so the window maps immediately with exact drawn geometry
    swaymsg "for_window [app_id=\"$UNIQUE_ID\"] floating enable, border pixel 2, resize set $W $H, move absolute position $X $Y; for_window [class=\"$UNIQUE_ID\"] floating enable, border pixel 2, resize set $W $H, move absolute position $X $Y" >/dev/null 2>&1

    # Detect preferred terminal
    TERM_BIN="kitty"
    if command -v kitty >/dev/null 2>&1; then
        TERM_BIN="kitty"
    elif command -v foot >/dev/null 2>&1; then
        TERM_BIN="foot"
    elif command -v alacritty >/dev/null 2>&1; then
        TERM_BIN="alacritty"
    fi

    # Spawn terminal with unique app-id/class
    if [[ "$TERM_BIN" == "kitty" ]]; then
        if [[ ${#TARGET_CMD[@]} -eq 0 ]]; then
            kitty --class "$UNIQUE_ID" --title "$UNIQUE_ID" &
        else
            kitty --class "$UNIQUE_ID" --title "$UNIQUE_ID" -e "${TARGET_CMD[@]}" &
        fi
    elif [[ "$TERM_BIN" == "foot" ]]; then
        if [[ ${#TARGET_CMD[@]} -eq 0 ]]; then
            foot --app-id "$UNIQUE_ID" --title "$UNIQUE_ID" &
        else
            foot --app-id "$UNIQUE_ID" --title "$UNIQUE_ID" "${TARGET_CMD[@]}" &
        fi
    elif [[ "$TERM_BIN" == "alacritty" ]]; then
        if [[ ${#TARGET_CMD[@]} -eq 0 ]]; then
            alacritty --class "$UNIQUE_ID","$UNIQUE_ID" &
        else
            alacritty --class "$UNIQUE_ID","$UNIQUE_ID" -e "${TARGET_CMD[@]}" &
        fi
    else
        "$TERM_BIN" &
    fi

    # Background polling to enforce exact geometry once mapped
    (
        for _ in {1..25}; do
            sleep 0.04
            swaymsg "[app_id=\"$UNIQUE_ID\"] floating enable; [app_id=\"$UNIQUE_ID\"] resize set $W $H; [app_id=\"$UNIQUE_ID\"] move absolute position $X $Y" >/dev/null 2>&1 || true
        done
    ) &
fi
