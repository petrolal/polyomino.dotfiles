#!/usr/bin/env bash
# ~/.config/sway/scripts/sway-screensaver.sh
# Full-system animated lofi screensaver using mpv/mpvpaper and swayidle

set -euo pipefail

VIDEO_PATH="${LOFI_VIDEO:-$HOME/Videos/anime_lofi_girl_video.mp4}"
PID_FILE="${XDG_RUNTIME_DIR:-/tmp}/polyomino-screensaver.pid"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

stop_screensaver() {
    # 1. Clean up PID file if recorded
    if [[ -f "$PID_FILE" ]]; then
        local pid
        pid=$(cat "$PID_FILE" 2>/dev/null || true)
        if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
            kill "$pid" 2>/dev/null || true
        fi
        rm -f "$PID_FILE"
    fi

    # 2. Kill any matching mpv/mpvpaper screensaver instances
    pkill -f "anime_lofi_girl_video.mp4" 2>/dev/null || true
    pkill -f "wayland-app-id=screensaver" 2>/dev/null || true
    pkill -f "app_id=screensaver" 2>/dev/null || true
}

start_screensaver() {
    # Avoid duplicate instances
    if pgrep -f "anime_lofi_girl_video.mp4" >/dev/null 2>&1 || pgrep -f "wayland-app-id=screensaver" >/dev/null 2>&1; then
        exit 0
    fi

    # Check if target video exists
    if [[ ! -f "$VIDEO_PATH" ]]; then
        # Fallback to terminal flow field screensaver if video is missing
        if [[ -x "$SCRIPT_DIR/screensaver.py" ]] && command -v kitty >/dev/null 2>&1; then
            exec kitty --app-id=screensaver --class=screensaver -e python3 "$SCRIPT_DIR/screensaver.py"
        fi
        exit 1
    fi

    # Hardware-accelerated fullscreen playback via mpv
    if command -v mpv >/dev/null 2>&1; then
        mpv --fullscreen \
            --loop-file=inf \
            --no-audio \
            --hwdec=auto \
            --panscan=1.0 \
            --wayland-app-id=screensaver \
            --title=screensaver \
            --really-quiet \
            "$VIDEO_PATH" >/dev/null 2>&1 &
        local mpv_pid=$!
        echo "$mpv_pid" > "$PID_FILE"
    elif command -v mpvpaper >/dev/null 2>&1; then
        mpvpaper -o "no-audio --loop" '*' "$VIDEO_PATH" >/dev/null 2>&1 &
        local mpv_pid=$!
        echo "$mpv_pid" > "$PID_FILE"
    else
        # Terminal fallback
        if [[ -x "$SCRIPT_DIR/screensaver.py" ]] && command -v kitty >/dev/null 2>&1; then
            exec kitty --app-id=screensaver --class=screensaver -e python3 "$SCRIPT_DIR/screensaver.py"
        fi
    fi
}

case "${1:-start}" in
    start)
        start_screensaver
        ;;
    stop|dismiss|resume)
        stop_screensaver
        ;;
    toggle)
        if pgrep -f "anime_lofi_girl_video.mp4" >/dev/null 2>&1 || pgrep -f "wayland-app-id=screensaver" >/dev/null 2>&1; then
            stop_screensaver
        else
            start_screensaver
        fi
        ;;
    *)
        echo "Usage: $0 {start|stop|dismiss|resume|toggle}"
        exit 1
        ;;
esac
