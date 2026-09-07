#!/usr/bin/env bash
# Polyomino Terminal Matrix / Tetromino Screen Saver

dir="$(dirname "${BASH_SOURCE[0]}")"
screensaver="$dir/screensaver.py"
tetromino_rain="$dir/tetromino-rain.sh"

if [[ -x "$screensaver" ]] && command -v python3 >/dev/null 2>&1; then
  exec "$screensaver"
elif [[ -x "$tetromino_rain" ]]; then
  exec "$tetromino_rain"
elif command -v cmatrix >/dev/null 2>&1; then
  exec cmatrix -s -C yellow
elif command -v tint >/dev/null 2>&1; then
  exec tint
else
  clear
  # Static Polyomino Matrix Screen in Axé Gold (#EBB434) and Maré Teal (#00D2D3)
  printf "\033[1;33m"
  cat << 'EOF'
 ╔═════════════════════════════════════════════════════════════════════════╗
 ║                  [⊞] POLYOMINO MATRIX IDLE SCREEN [⊞]                   ║
 ║                                                                         ║
 ║      .-------.-------.-------.                  .-------.               ║
 ║     /       /       /       /|                 /       /|               ║
 ║    +-------+-------+-------+ |                +-------+ |               ║
 ║    |       |       |       | |                |       | |               ║
 ║    |       |       |       |/|                |       |/|               ║
 ║    `-------+-------+-------' |                `-------+ |               ║
 ║            |       |       | |                        | |               ║
 ║            |       |       |/|                .-------+ |               ║
 ║            +-------+-------+ |               /       /| |               ║
 ║            |       |       | |              +-------+ |/|               ║
 ║            |       |       |/               |       |/| |               ║
 ║            `-------+-------'                +-------+ |/                ║
 ║                                             |       |/                  ║
 ║                                             `-------'                   ║
 ║                                                                         ║
 ║     [■■■■] Axé Gold (#EBB434)     |     [■■■■] Maré Teal (#00D2D3)      ║
 ║                                                                         ║
 ║                 Press any key or Ctrl+C to resume session               ║
 ╚═════════════════════════════════════════════════════════════════════════╝
EOF
  printf "\033[0m"
  read -r -s -n 1 _
fi
