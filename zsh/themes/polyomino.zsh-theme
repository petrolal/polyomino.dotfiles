# Polyomino Zsh Theme
# Monospaced brackets, polyomino glyph motif, and violet/cyan accent hierarchy.
# Matches the SwayFX / Waybar / Tetravim desktop identity.

autoload -Uz vcs_info
autoload -Uz add-zsh-hook

setopt PROMPT_SUBST

# Color Palette Definitions (TrueColor Hex + 256 Fallbacks)
local C_BRACKET="%F{#475569}"
local C_GLYPH="%B%F{#8b5cf6}"
local C_PATH="%B%F{#38bdf8}"
local C_BRANCH="%B%F{#a855f7}"
local C_STAGED="%B%F{#10b981}+%f%b"
local C_UNSTAGED="%B%F{#f59e0b}*%f%b"
local C_RESET="%b%f"

# Fast & Lightweight Git status queries via vcs_info
zstyle ':vcs_info:*' enable git
zstyle ':vcs_info:git:*' check-for-changes true
zstyle ':vcs_info:git:*' stagedstr "${C_STAGED}"
zstyle ':vcs_info:git:*' unstagedstr "${C_UNSTAGED}"
zstyle ':vcs_info:git:*' formats "%b" "%u%c"
zstyle ':vcs_info:git:*' actionformats "%b|%a" "%u%c"

_polyomino_git_segment() {
  if [[ -n "$vcs_info_msg_0_" ]]; then
    local branch="${C_BRANCH}${vcs_info_msg_0_}${C_RESET}"
    local flags="${vcs_info_msg_1_}"
    if [[ -n "$flags" ]]; then
      echo "${C_BRACKET}[${C_RESET} ${branch} ${flags} ${C_BRACKET}]${C_RESET} "
    else
      echo "${C_BRACKET}[${C_RESET} ${branch} ${C_BRACKET}]${C_RESET} "
    fi
  fi
}

_polyomino_precmd() {
  vcs_info
}

add-zsh-hook precmd _polyomino_precmd

# Prompt Anatomy:
# [ ⮽ ] [ ~/polyomino.dotfiles ] [ master * ] ❯ 
PROMPT='${C_BRACKET}[${C_RESET} ${C_GLYPH}⮽${C_RESET} ${C_BRACKET}]${C_RESET} ${C_BRACKET}[${C_RESET} ${C_PATH}%(4~|…/%3~|%~)${C_RESET} ${C_BRACKET}]${C_RESET} $(_polyomino_git_segment)%(?.%B%F{#8b5cf6}❯%f%b.%B%F{#ef4444}❯%f%b) '
