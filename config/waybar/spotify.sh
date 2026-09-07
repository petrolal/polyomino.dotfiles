#!/usr/bin/env bash
## Polyomino Waybar Spotify integration
## Provides instant playback state, metadata, and tooltips for Waybar

set -e

# Query playerctl for spotify_player, fallback to spotify or any MPRIS player
raw=$(playerctl -p spotify_player,spotify,%any metadata --format $'{{status}}\t{{artist}}\t{{title}}\t{{album}}' 2>/dev/null || true)

if [[ -z "$raw" ]]; then
  if command -v jq &>/dev/null; then
    jq -cn \
      --arg text "󰓇" \
      --arg alt "offline" \
      --arg tooltip "Spotify is offline (SUPER+Shift+M to launch)" \
      --arg class "offline" \
      '{text: $text, alt: $alt, tooltip: $tooltip, class: $class}'
  else
    printf '{"text":"󰓇","alt":"offline","tooltip":"Spotify is offline (SUPER+Shift+M to launch)","class":"offline"}\n'
  fi
  exit 0
fi

IFS=$'\t' read -r status artist title album <<< "$raw"

# Handle ads
if [[ "$title" == "Advertisement" || ( "$artist" == "Spotify" && "$title" == "" ) ]]; then
  if command -v jq &>/dev/null; then
    jq -cn \
      --arg text " Ad" \
      --arg alt "ad" \
      --arg tooltip "Spotify Advertisement" \
      --arg class "ad" \
      '{text: $text, alt: $alt, tooltip: $tooltip, class: $class}'
  else
    printf '{"text":" Ad","alt":"ad","tooltip":"Spotify Advertisement","class":"ad"}\n'
  fi
  exit 0
fi

display_artist="${artist:-Unknown Artist}"
display_title="${title:-Unknown Track}"

if [[ "$status" == "Playing" ]]; then
  display_text="󰐊 ${display_artist} - ${display_title}"
  class="playing"
elif [[ "$status" == "Paused" ]]; then
  display_text="󰏤 ${display_artist} - ${display_title}"
  class="paused"
else
  display_text="󰓇 ${display_artist} - ${display_title}"
  class="offline"
fi

tooltip=$(printf " Spotify (%s)\nTitle:  %s\nArtist: %s\nAlbum:  %s" "$status" "$display_title" "$display_artist" "${album:-N/A}")

if command -v jq &>/dev/null; then
  jq -cn \
    --arg text "$display_text" \
    --arg alt "$status" \
    --arg tooltip "$tooltip" \
    --arg class "$class" \
    '{text: $text, alt: $alt, tooltip: $tooltip, class: $class}'
else
  clean_text="${display_text//\"/\\\"}"
  clean_tooltip="${tooltip//\"/\\\"}"
  clean_tooltip="${clean_tooltip//$'\n'/\\n}"
  printf '{"text":"%s","alt":"%s","tooltip":"%s","class":"%s"}\n' \
    "$clean_text" "$status" "$clean_tooltip" "$class"
fi
