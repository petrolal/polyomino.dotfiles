#!/usr/bin/env bash
# SDKMan Maintenance Script
# Manages existing SDKMan installations, updates tools, and maintains versions

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDKMAN_DIR="${SDKMAN_DIR:-$HOME/.sdkman}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[1;36m'
NC='\033[0m' # No Color

print_header() {
  echo -e "${BLUE}[polyomino-sdkman]${NC} $1"
}

print_ok() {
  echo -e "  ${GREEN}[OK]${NC} $1"
}

print_info() {
  echo -e "  ${BLUE}[INFO]${NC} $1"
}

print_warn() {
  echo -e "  ${YELLOW}[WARN]${NC} $1"
}

print_error() {
  echo -e "  ${RED}[ERROR]${NC} $1"
}

export RED GREEN YELLOW BLUE NC
export -f print_header print_ok print_info print_warn print_error

install_sdkman() {
  print_header "Installing SDKMan..."
  curl -s "https://get.sdkman.io" | bash
  print_ok "SDKMan installed"
}

check_sdkman() {
  print_header "Checking SDKMan installation..."

  if [ ! -d "$SDKMAN_DIR" ]; then
    print_error "SDKMan not found at $SDKMAN_DIR"
    print_info "Install with: bash <(curl -s https://get.sdkman.io)"
    return 1
  fi

  print_ok "SDKMan found at $SDKMAN_DIR"
  return 0
}

upgrade_sdkman() {
  print_header "Upgrading SDKMan..."

  if ! check_sdkman; then
    return 1
  fi

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh
    print_info 'Running: sdk selfupdate'
    sdk selfupdate force 2>/dev/null || true
    print_ok 'SDKMan upgraded'
  "
}

list_installed() {
  print_header "Listing installed SDKs..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh
    sdk current
  " || print_warn "Could not list current versions"
}

show_available_versions() {
  local tool=$1
  print_header "Available versions for $tool..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh
    sdk list $tool | head -20
  "
}

update_java() {
  local version="${1:-21.0.1-graal}"
  print_header "Updating Java to $version..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    print_info 'Installing Java $version...'
    sdk install java $version --default 2>/dev/null || true

    print_info 'Current Java version:'
    java -version
    print_ok 'Java updated'
  "
}

update_scala() {
  local version="${1:-3.5.2}"
  print_header "Updating Scala to $version..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    print_info 'Installing Scala $version...'
    sdk install scala $version --default 2>/dev/null || true

    print_info 'Current Scala version:'
    scala -version
    print_ok 'Scala updated'
  "
}

update_sbt() {
  local version="${1:-1.9.9}"
  print_header "Updating sbt to $version..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    print_info 'Installing sbt $version...'
    sdk install sbt $version --default 2>/dev/null || true

    print_info 'Current sbt version:'
    sbt --version
    print_ok 'sbt updated'
  "
}

update_maven() {
  local version="${1:-3.9.6}"
  print_header "Updating Maven to $version..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    print_info 'Installing Maven $version...'
    sdk install maven $version --default 2>/dev/null || true

    print_info 'Current Maven version:'
    mvn --version | head -1
    print_ok 'Maven updated'
  "
}

update_gradle() {
  local version="${1:-8.5}"
  print_header "Updating Gradle to $version..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    print_info 'Installing Gradle $version...'
    sdk install gradle $version --default 2>/dev/null || true

    print_info 'Current Gradle version:'
    gradle --version | head -1
    print_ok 'Gradle updated'
  "
}

update_kotlin() {
  local version="${1:-1.9.22}"
  print_header "Updating Kotlin to $version..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    print_info 'Installing Kotlin $version...'
    sdk install kotlin $version --default 2>/dev/null || true

    print_info 'Current Kotlin version:'
    kotlin -version
    print_ok 'Kotlin updated'
  "
}

update_groovy() {
  local version="${1:-4.0.17}"
  print_header "Updating Groovy to $version..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    print_info 'Installing Groovy $version...'
    sdk install groovy $version --default 2>/dev/null || true

    print_info 'Current Groovy version:'
    groovy --version
    print_ok 'Groovy updated'
  "
}

update_all() {
  print_header "Updating all SDKMan tools..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    print_info 'Updating SDKMan...'
    sdk selfupdate force 2>/dev/null || true

    print_info 'Installing Java 21 GraalVM...'
    sdk install java 21.0.1-graal --default 2>/dev/null || true

    print_info 'Installing Scala 3.5.2...'
    sdk install scala 3.5.2 --default 2>/dev/null || true

    print_info 'Installing sbt 1.9.9...'
    sdk install sbt 1.9.9 --default 2>/dev/null || true

    print_info 'Current versions:'
    sdk current
  "

  print_ok "All tools updated successfully"
}

install_interactive() {
  print_header "Interactive SDKMan Setup"
  echo ""

  # Step 1: Java version selection
  print_info "Select Java version:"
  echo "  1) Java 21 GraalVM (recommended, native image support)"
  echo "  2) Java 21 OpenJDK"
  echo "  3) Java 17 GraalVM"
  echo "  4) Java 17 OpenJDK"
  echo "  5) Java 11 OpenJDK (legacy)"
  echo "  6) Skip Java installation"
  echo -en "${YELLOW}[?] Choose (1-6): ${NC}"
  read -r java_choice || java_choice="1"

  case $java_choice in
    1) update_java "21.0.1-graal" ;;
    2) update_java "21.0.1-open" ;;
    3) update_java "17.0.8-graal" ;;
    4) update_java "17.0.8-open" ;;
    5) update_java "11.0.20-open" ;;
    6) print_info "Skipping Java installation" ;;
    *) print_warn "Invalid choice, using Java 21 GraalVM" && update_java "21.0.1-graal" ;;
  esac

  echo ""

  # Step 2: Scala selection
  print_info "Select Scala version:"
  echo "  1) Scala 3.5.2 (latest, recommended)"
  echo "  2) Scala 3.4.0"
  echo "  3) Scala 2.13.12 (legacy)"
  echo "  4) Skip Scala installation"
  echo -en "${YELLOW}[?] Choose (1-4): ${NC}"
  read -r scala_choice || scala_choice="1"

  case $scala_choice in
    1) update_scala "3.5.2" ;;
    2) update_scala "3.4.0" ;;
    3) update_scala "2.13.12" ;;
    4) print_info "Skipping Scala installation" ;;
    *) print_warn "Invalid choice, using Scala 3.5.2" && update_scala "3.5.2" ;;
  esac

  echo ""

  # Step 3: Build tools selection
  print_info "Select build tools to install:"
  echo "  1) sbt (Scala Build Tool) - recommended for Scala"
  echo "  2) Maven (Java/Kotlin/Groovy)"
  echo "  3) Gradle (modern build system)"
  echo "  4) All build tools (sbt + Maven + Gradle)"
  echo "  5) Skip build tools"
  echo -en "${YELLOW}[?] Choose (1-5): ${NC}"
  read -r build_choice || build_choice="1"

  case $build_choice in
    1) update_sbt "1.9.9" ;;
    2) update_maven "3.9.6" ;;
    3) update_gradle "8.5" ;;
    4)
      update_sbt "1.9.9"
      update_maven "3.9.6"
      update_gradle "8.5"
      ;;
    5) print_info "Skipping build tools" ;;
    *) print_warn "Invalid choice, using sbt" && update_sbt "1.9.9" ;;
  esac

  echo ""

  # Step 4: JVM languages selection
  print_info "Select additional JVM languages:"
  echo "  1) Kotlin (modern JVM language)"
  echo "  2) Groovy (dynamic JVM language)"
  echo "  3) Both Kotlin and Groovy"
  echo "  4) Skip language installation"
  echo -en "${YELLOW}[?] Choose (1-4): ${NC}"
  read -r lang_choice || lang_choice="4"

  case $lang_choice in
    1) update_kotlin "1.9.22" ;;
    2) update_groovy "4.0.17" ;;
    3)
      update_kotlin "1.9.22"
      update_groovy "4.0.17"
      ;;
    4) print_info "Skipping language installation" ;;
    *) print_info "Skipping language installation" ;;
  esac

  echo ""
  print_header "Setup complete!"
  list_installed
}

clean_old_versions() {
  print_header "Cleaning old SDK versions (keeping current default)..."

  bash -c "
    source $SDKMAN_DIR/bin/sdkman-init.sh

    for tool in java scala sbt; do
      print_info 'Checking \$tool versions...'
      current=\$(sdk current | grep \$tool | awk '{print \$3}')
      if [ -n \"\$current\" ]; then
        print_info \"Current \$tool version: \$current\"
      fi
    done

    print_warn 'Manual cleanup required for old versions'
    print_info 'To remove old versions:'
    print_info '  rm -rf $SDKMAN_DIR/candidates/java/<old-version>'
    print_info '  rm -rf $SDKMAN_DIR/candidates/scala/<old-version>'
    print_info '  rm -rf $SDKMAN_DIR/candidates/sbt/<old-version>'
  "
}

reset_sdkman() {
  print_header "WARNING: This will reinstall SDKMan from scratch"
  echo -en "${YELLOW}[?] Are you sure? (y/N): ${NC}"
  read -r response || response="n"

  if [[ "$response" =~ ^[Yy]$ ]]; then
    print_warn "Backing up existing SDKMan to ${SDKMAN_DIR}.backup..."
    mv "$SDKMAN_DIR" "${SDKMAN_DIR}.backup"

    print_info "Installing fresh SDKMan..."
    curl -s "https://get.sdkman.io" | bash

    print_ok "SDKMan reinstalled. Old installation backed up at ${SDKMAN_DIR}.backup"
  else
    print_info "Reset cancelled"
  fi
}

show_help() {
  cat << 'EOF'
SDKMan Maintenance Script

Usage: ./scripts/maintain-sdkman.sh <command> [args]

Interactive Setup:
  install               Interactive menu to install/configure tools

Status & Information:
  check                 Check SDKMan installation status
  upgrade               Upgrade SDKMan itself
  list                  List currently installed SDK versions
  available <tool>      Show available versions (java|scala|sbt|maven|gradle|kotlin|groovy)

Update Individual Tools:
  update-java [ver]     Update Java (default: 21.0.1-graal)
  update-scala [ver]    Update Scala (default: 3.5.2)
  update-sbt [ver]      Update sbt (default: 1.9.9)
  update-maven [ver]    Update Maven (default: 3.9.6)
  update-gradle [ver]   Update Gradle (default: 8.5)
  update-kotlin [ver]   Update Kotlin (default: 1.9.22)
  update-groovy [ver]   Update Groovy (default: 4.0.17)
  update-all            Update all core tools to latest recommended

Maintenance:
  clean                 Show guidance for cleaning old versions
  reset                 Reinstall SDKMan from scratch (backup existing)

  help                  Show this help message

Examples:
  # Interactive setup (choose versions)
  ./scripts/maintain-sdkman.sh install

  # Check installation
  ./scripts/maintain-sdkman.sh check

  # List installed versions
  ./scripts/maintain-sdkman.sh list

  # Update specific tools
  ./scripts/maintain-sdkman.sh update-java 21.0.5-graal
  ./scripts/maintain-sdkman.sh update-maven 3.9.8
  ./scripts/maintain-sdkman.sh update-kotlin 1.9.23

  # List available versions
  ./scripts/maintain-sdkman.sh available java
  ./scripts/maintain-sdkman.sh available maven
  ./scripts/maintain-sdkman.sh available kotlin

Environment Variables:
  SDKMAN_DIR            Location of SDKMan installation (default: ~/.sdkman)

  # Example custom installation path
  SDKMAN_DIR=/opt/sdkman ./scripts/maintain-sdkman.sh check

Supported Tools:
  Java:    21.0.1-graal, 21.0.1-open, 17.0.8-graal, 17.0.8-open, 11.0.20-open
  Scala:   3.5.2, 3.4.0, 2.13.12
  sbt:     1.9.9, 1.8.3, 1.7.3
  Maven:   3.9.6, 3.9.5, 3.8.8
  Gradle:  8.5, 8.4, 8.3
  Kotlin:  1.9.22, 1.9.20, 1.8.22
  Groovy:  4.0.17, 4.0.15, 3.0.19

See Also:
  - SDKMan Docs: https://sdkman.io/
  - Java: https://sdkman.io/jdks
  - Scala: https://sdkman.io/sdks/scala
  - Maven: https://sdkman.io/sdks/maven
  - Gradle: https://sdkman.io/sdks/gradle
  - Kotlin: https://sdkman.io/sdks/kotlin
  - Groovy: https://sdkman.io/sdks/groovy
EOF
}

# Main command routing
main() {
  local command="${1:-help}"

  case "$command" in
    install)
      check_sdkman || install_sdkman
      install_interactive
      ;;
    check)
      check_sdkman
      ;;
    upgrade)
      upgrade_sdkman
      ;;
    list)
      list_installed
      ;;
    available)
      show_available_versions "${2:-java}"
      ;;
    update-java)
      update_java "${2:-21.0.1-graal}"
      ;;
    update-scala)
      update_scala "${2:-3.5.2}"
      ;;
    update-sbt)
      update_sbt "${2:-1.9.9}"
      ;;
    update-maven)
      update_maven "${2:-3.9.6}"
      ;;
    update-gradle)
      update_gradle "${2:-8.5}"
      ;;
    update-kotlin)
      update_kotlin "${2:-1.9.22}"
      ;;
    update-groovy)
      update_groovy "${2:-4.0.17}"
      ;;
    update-all)
      update_all
      ;;
    clean)
      clean_old_versions
      ;;
    reset)
      reset_sdkman
      ;;
    help|--help|-h)
      show_help
      ;;
    *)
      print_error "Unknown command: $command"
      echo ""
      show_help
      exit 1
      ;;
  esac
}

main "$@"
