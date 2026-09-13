#!/usr/bin/env bash
# polyomino sdk wrapper - delegates to Scala native CLI
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if command -v polyomino &>/dev/null; then
  exec polyomino sdk "$@"
elif [ -x "$HOME/.local/bin/polyomino" ]; then
  exec "$HOME/.local/bin/polyomino" sdk "$@"
elif [ -x "$SCRIPT_DIR/target/native-image/polyomino" ]; then
  exec "$SCRIPT_DIR/target/native-image/polyomino" sdk "$@"
else
  cd "$SCRIPT_DIR" && sbt "run sdk $*"
fi
