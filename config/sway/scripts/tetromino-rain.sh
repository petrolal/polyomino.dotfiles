#!/usr/bin/env bash
# Polyomino Tetromino Rain — passive terminal screen saver.
# Multiple tetromino pieces fall concurrently and stack into a bordered
# Tetris-style board; there is no player control, scoring, or line-clear
# logic. Any keypress exits.

screensaver="$(dirname "${BASH_SOURCE[0]}")/screensaver.py"
if [[ -x "$screensaver" ]] && command -v python3 >/dev/null 2>&1; then
  exec "$screensaver"
fi

cleanup() { tput cnorm; clear; }
trap cleanup EXIT INT TERM

tput civis
clear

term_rows=$(tput lines)
term_cols=$(tput cols)

board_cols=$(( (term_cols - 2) / 2 ))
(( board_cols < 10 )) && board_cols=10
board_rows=$(( term_rows - 2 ))
(( board_rows < 10 )) && board_rows=10

offset_x=$(( (term_cols - board_cols * 2) / 2 ))
(( offset_x < 0 )) && offset_x=0
offset_y=0

colors=(31 33 32 36 34 35 91 93 96 92)

shapes=(
  "0,0 0,1 0,2 0,3"
  "0,0 0,1 1,0 1,1"
  "0,0 0,1 0,2 1,1"
  "0,1 0,2 1,0 1,1"
  "0,0 0,1 1,1 1,2"
  "0,0 1,0 1,1 1,2"
  "0,2 1,0 1,1 1,2"
)

declare -A locked_color
declare -A p_shape p_color p_col p_row
active_ids=()
next_id=0
locked_count=0
board_capacity=$(( board_rows * board_cols ))
max_active=$(( board_cols / 6 ))
(( max_active < 4 )) && max_active=4

put() {
  local r=$1 c=$2 color=$3
  (( r < 0 || r >= board_rows || c < 0 || c >= board_cols )) && return
  local sr=$((offset_y + 1 + r)) sc=$((offset_x + 1 + c * 2))
  printf "\033[%d;%dH" "$sr" "$sc"
  if [[ -n "$color" ]]; then
    printf "\033[1;%sm██\033[0m" "$color"
  else
    printf "  "
  fi
}

draw_border() {
  local bar; bar=$(printf '%*s' $((board_cols * 2)) '' | tr ' ' '═')
  printf "\033[%d;%dH\033[1;36m╔%s╗\033[0m" "$offset_y" "$offset_x" "$bar"
  local r
  for ((r = 0; r < board_rows; r++)); do
    printf "\033[%d;%dH\033[1;36m║\033[0m" $((offset_y + 1 + r)) "$offset_x"
    printf "\033[%d;%dH\033[1;36m║\033[0m" $((offset_y + 1 + r)) $((offset_x + board_cols * 2 + 1))
  done
  printf "\033[%d;%dH\033[1;36m╚%s╝\033[0m" $((offset_y + board_rows + 1)) "$offset_x" "$bar"
}

spawn_piece() {
  local shape="${shapes[RANDOM % ${#shapes[@]}]}"
  local color="${colors[RANDOM % ${#colors[@]}]}"
  local -a cells; read -r -a cells <<< "$shape"
  local maxdc=0 cell dc
  for cell in "${cells[@]}"; do
    dc=${cell#*,}
    (( dc > maxdc )) && maxdc=$dc
  done
  local col=$(( RANDOM % (board_cols - maxdc) ))
  local id=$next_id
  (( next_id++ ))
  p_shape[$id]="$shape"
  p_color[$id]="$color"
  p_col[$id]=$col
  p_row[$id]=-4
  active_ids+=("$id")
}

reset_board() {
  local i r
  for ((i = 0; i < 2; i++)); do
    printf "\033[1;37m"
    for ((r = 0; r < board_rows; r++)); do
      printf "\033[%d;%dH%*s" $((offset_y + 1 + r)) $((offset_x + 1)) $((board_cols * 2)) ""
    done
    printf "\033[0m"
    sleep 0.06
  done
  locked_color=()
  locked_count=0
  active_ids=()
  clear
  draw_border
}

tick() {
  local -a new_active=()
  local id
  for id in "${active_ids[@]}"; do
    local -a cells; read -r -a cells <<< "${p_shape[$id]}"
    local row=${p_row[$id]} col=${p_col[$id]} color=${p_color[$id]}
    local next=$((row + 1)) collide=0 cell dr dc rr cc

    for cell in "${cells[@]}"; do
      dr=${cell%,*}; dc=${cell#*,}
      rr=$((next + dr)); cc=$((col + dc))
      if (( rr >= board_rows )) || [[ -n "${locked_color[$rr,$cc]:-}" ]]; then
        collide=1; break
      fi
    done

    for cell in "${cells[@]}"; do
      dr=${cell%,*}; dc=${cell#*,}
      put $((row + dr)) $((col + dc)) ""
    done

    if (( collide )); then
      for cell in "${cells[@]}"; do
        dr=${cell%,*}; dc=${cell#*,}
        rr=$((row + dr)); cc=$((col + dc))
        if (( rr >= 0 )); then
          locked_color[$rr,$cc]=$color
          put "$rr" "$cc" "$color"
          (( locked_count++ ))
        fi
      done
    else
      p_row[$id]=$next
      for cell in "${cells[@]}"; do
        dr=${cell%,*}; dc=${cell#*,}
        put $((next + dr)) $((col + dc)) "$color"
      done
      new_active+=("$id")
    fi
  done
  active_ids=("${new_active[@]}")
}

draw_border

while :; do
  tick

  if (( ${#active_ids[@]} < max_active )) && (( RANDOM % 2 == 0 )); then
    spawn_piece
  fi

  if (( locked_count >= board_capacity * 85 / 100 )); then
    reset_board
  fi

  read -r -s -n 1 -t 0.07 && exit 0
done
