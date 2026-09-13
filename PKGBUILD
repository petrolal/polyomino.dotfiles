# Maintainer: petrolal <petrolalucas@gmail.com>
pkgname=polyomino-dotfiles
pkgver=0.1.0
pkgrel=1
pkgdesc="Sway dotfiles installer with Scala 3 + GraalVM native image"
arch=('x86_64')
url="https://github.com/petrolal/polyomino.dotfiles"
license=('MIT')
depends=(
  'sway'
  'waybar'
  'kitty'
  'wofi'
  'swaylock'
  'swayidle'
  'grim'
  'slurp'
  'brightnessctl'
  'libpulse'
  'playerctl'
  'mpv'
  'chromium'
  'docker'
  'terraform'
  'kubectl'
  'helm'
  'neovim'
  'fastfetch'
  'ttf-jetbrains-mono-nerd'
  'swaync'
  'mako'
  'zsh'
)
makedepends=(
  'jdk-openjdk'
  'sbt'
  'gcc'
  'git'
)
optdepends=(
  'spotify_player: feature-rich Spotify terminal UI player with daemon streaming'
  'ncspot: alternative lightweight ncurses Spotify TUI client'
  'alacritty: for terminal screenshot region selection'
  'termite: alternative terminal for screenshot'
)
source=("git+https://github.com/petrolal/polyomino.dotfiles.git")
sha256sums=('SKIP')

build() {
  cd "${pkgname}"
  sbt nativeImage
}

package() {
  cd "${pkgname}"

  # Install main binary
  install -Dm755 "target/native-image/polyomino" "${pkgdir}/usr/local/bin/polyomino"

  # Create subcommand symlinks
  local subcommands=(
    theme runtime-refresh os-colorscheme lock idle screenshot autotiling
    healthcheck backup restore update sdd install deploy install-deps
    install-brew install-homebrew install-gh install-github-cli install-coursier install-cs
    install-fonts install-apps install-browser install-devops install-zsh
    install-sdkman install-nvim install-nvim-deps install-neovim install-tools install-spotify install-spotify-player install-fastfetch full-install
    theme-picker whichkey
  )

  for cmd in "${subcommands[@]}"; do
    ln -s "/usr/local/bin/polyomino" "${pkgdir}/usr/local/bin/polyomino-${cmd}"
  done

  # Install dotfiles config directory
  install -dm755 "${pkgdir}/opt/polyomino"
  cp -r config zsh bootstrap.sh "${pkgdir}/opt/polyomino/"

  # Install systemd user service for idle daemon (optional)
  install -dm755 "${pkgdir}/usr/lib/systemd/user"
  # You can add a systemd service file here if needed

  # Install license
  install -Dm644 LICENSE "${pkgdir}/usr/share/licenses/${pkgname}/LICENSE"
}
