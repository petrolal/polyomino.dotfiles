#!/usr/bin/env bash
# Release script for semantic versioning and tagging

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_FILE="$REPO_ROOT/build.sbt"

# Get current version
CURRENT_VERSION=$(grep 'version := ' "$BUILD_FILE" | head -1 | sed -E 's/.*version := "([^"]+)".*/\1/')

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  polyomino.dotfiles Release Helper                              ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo
echo "Current version: ${GREEN}$CURRENT_VERSION${NC}"
echo

# Parse version
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

# Show options
echo "Select release type:"
echo "  1) Patch  (${MAJOR}.${MINOR}.$((PATCH+1))) - Bug fixes"
echo "  2) Minor  (${MAJOR}.$((MINOR+1)).0)     - New features"
echo "  3) Major  ($((MAJOR+1)).0.0)      - Breaking changes"
echo "  4) Custom version"
echo

read -p "Enter choice (1-4): " CHOICE

case $CHOICE in
  1)
    NEW_VERSION="${MAJOR}.${MINOR}.$((PATCH+1))"
    ;;
  2)
    NEW_VERSION="${MAJOR}.$((MINOR+1)).0"
    ;;
  3)
    NEW_VERSION="$((MAJOR+1)).0.0"
    ;;
  4)
    read -p "Enter new version (e.g., 1.0.0): " NEW_VERSION
    ;;
  *)
    echo -e "${RED}Invalid choice${NC}"
    exit 1
    ;;
esac

echo
echo -e "New version will be: ${GREEN}$NEW_VERSION${NC}"
echo

# Validate version format
if ! [[ $NEW_VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo -e "${RED}Error: Invalid version format (use X.Y.Z)${NC}"
  exit 1
fi

read -p "Continue with release v$NEW_VERSION? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
  echo "Release cancelled"
  exit 0
fi

echo
echo -e "${BLUE}[1/4] Updating version in build.sbt...${NC}"
sed -i.bak "s/version := \".*\"/version := \"$NEW_VERSION\"/" "$BUILD_FILE"
rm -f "$BUILD_FILE.bak"
echo -e "  ${GREEN}✓${NC} Version updated to $NEW_VERSION"

echo
echo -e "${BLUE}[2/4] Updating PKGBUILD...${NC}"
PKGBUILD="$REPO_ROOT/PKGBUILD"
sed -i.bak "s/pkgver=.*/pkgver=$NEW_VERSION/" "$PKGBUILD"
sed -i.bak "s/pkgrel=.*/pkgrel=1/" "$PKGBUILD"
rm -f "$PKGBUILD.bak"
echo -e "  ${GREEN}✓${NC} PKGBUILD updated"

echo
echo -e "${BLUE}[3/4] Updating .SRCINFO...${NC}"
SRCINFO="$REPO_ROOT/.SRCINFO"
sed -i.bak "s/pkgver = .*/pkgver = $NEW_VERSION/" "$SRCINFO"
sed -i.bak "s/pkgrel = .*/pkgrel = 1/" "$SRCINFO"
rm -f "$SRCINFO.bak"
echo -e "  ${GREEN}✓${NC} .SRCINFO updated"

echo
echo -e "${BLUE}[4/4] Creating git commit and tag...${NC}"
cd "$REPO_ROOT"
git add build.sbt PKGBUILD .SRCINFO
git commit -m "chore: bump version to $NEW_VERSION"
git tag -a "v$NEW_VERSION" -m "Release version $NEW_VERSION"
echo -e "  ${GREEN}✓${NC} Commit and tag created"

echo
echo -e "${GREEN}✓ Release prepared!${NC}"
echo
echo "Next steps:"
echo "  1. Review changes: git log -1"
echo "  2. Push to GitHub:"
echo "     ${YELLOW}git push origin master${NC}"
echo "     ${YELLOW}git push origin --tags${NC}"
echo "  3. Watch CI/CD pipeline:"
echo "     https://github.com/petrolal/polyomino.dotfiles/actions"
echo
echo "This will automatically:"
echo "  • Run unit tests with Scala 3"
echo "  • Build native standalone Linux binary with GraalVM"
echo "  • Create GitHub Release with binary & checksums"
echo "  • Package for AUR / Arch Linux"
