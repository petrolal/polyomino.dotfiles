# ~/.zshrc — oh-my-zsh bootstrap + modular config loader
#
# All actual configuration (history, completion, keybindings, aliases,
# environment, tool integrations) lives as separate files under
# ~/.config/polyomino/zsh_config/*.zsh — see scripts/install-zsh.sh and the
# repo README. Files are sourced in alphabetical/numeric order, so:
#   - to add your own customizations, just drop a new *.zsh file in that
#     directory (e.g. 50-my-stuff.zsh) — no need to touch this file.
#   - numbering matters: 99-sdkman-cargo.zsh must stay last (SDKMAN
#     requires its init line to run at the very end of shell startup).

export ZSH="$HOME/.oh-my-zsh"
ZSH_THEME="polyomino"
plugins=(git)
[ -s "$ZSH/oh-my-zsh.sh" ] && source "$ZSH/oh-my-zsh.sh"

# ── Load modular config from ~/.config/polyomino/zsh_config/ ──
ZSH_CONFIG_DIR="$HOME/.config/polyomino/zsh_config"
if [ -d "$ZSH_CONFIG_DIR" ]; then
  for _zsh_config_file in "$ZSH_CONFIG_DIR"/*.zsh(N); do
    source "$_zsh_config_file"
  done
  unset _zsh_config_file
fi

# ── Run fastfetch on new interactive terminal ──
if [[ -o interactive ]] && command -v fastfetch >/dev/null 2>&1; then
  fastfetch
fi

