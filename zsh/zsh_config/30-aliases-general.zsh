# ── General aliases ──
alias ll='ls -alh --color=auto'
alias la='ls -A --color=auto'
alias l='ls -CF --color=auto'
alias ..='cd ..'
alias ...='cd ../..'
alias grep='grep --color=auto'
alias vim='nvim'
alias vi='nvim'
alias cn='[ -f "${XDG_CONFIG_HOME:-$HOME/.config}/polyomino/init.lua" ] && NVIM_APPNAME=polyomino nvim || ([ -x "$HOME/.local/bin/cn" ] && "$HOME/.local/bin/cn" || nvim)'
alias c='clear'
alias reload='source ~/.zshrc'
alias matrix='~/.config/sway/scripts/matrix.sh'
alias nvp='nvim-project'

# ── Project launcher (routes Neovim to workspace 2:code) ──
nvim-project() {
    local target_dir="${1:-.}"
    kitty --class nvim-project -d "$target_dir" nvim . >/dev/null 2>&1 &|
}

# ── Terminal idle screensaver (3 minutes of inactivity at prompt) ──
TMOUT=180
TRAPALRM() {
    if [[ -o interactive && -t 0 && -t 1 ]]; then
        local saver="${XDG_CONFIG_HOME:-$HOME/.config}/sway/scripts/matrix.sh"
        if [[ -x "$saver" ]]; then
            "$saver"
            zle && zle reset-prompt 2>/dev/null
        fi
    fi
}
