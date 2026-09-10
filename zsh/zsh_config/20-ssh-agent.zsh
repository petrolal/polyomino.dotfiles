# ── SSH agent (persist across shells, don't spawn a new one every terminal) ──
SSH_ENV="${XDG_RUNTIME_DIR:-$HOME/.ssh}/ssh-agent.env"

_start_ssh_agent() {
  mkdir -p "$(dirname "$SSH_ENV")"
  ssh-agent -s | sed 's/^echo/#echo/' > "$SSH_ENV"
  chmod 600 "$SSH_ENV"
  source "$SSH_ENV" > /dev/null
}

if [ -n "$SSH_AUTH_SOCK" ] && [ -S "$SSH_AUTH_SOCK" ]; then
  # Already provided by desktop session / keyring / forwarded agent
  :
elif [ -f "$SSH_ENV" ]; then
  source "$SSH_ENV" > /dev/null
  if ! kill -0 "$SSH_AGENT_PID" 2>/dev/null; then
    _start_ssh_agent
  fi
else
  _start_ssh_agent
fi
