#!/usr/bin/env bash
# polyomino release wrapper - delegates to Scala native CLI
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if command -v polyomino &>/dev/null; then
  exec polyomino release "$@"
elif [ -x "$HOME/.local/bin/polyomino" ]; then
  exec "$HOME/.local/bin/polyomino" release "$@"
elif [ -x "$SCRIPT_DIR/target/native-image/polyomino" ]; then
  exec "$SCRIPT_DIR/target/native-image/polyomino" release "$@"
else
  cd "$SCRIPT_DIR" && sbt "run release $*"
fi
