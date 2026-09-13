# Documentation Index

Complete guide to all polyomino.dotfiles documentation.

## Getting Started

### For End Users

**Start here:** [INSTALLATION_FLOW.md](INSTALLATION_FLOW.md)
- 3-stage installation overview
- Quick start guide (3 commands)
- Detailed walkthrough of each stage
- Troubleshooting

**Quick reference:** [README.md](../README.md)
- Project overview
- Commands and key bindings
- What gets installed
- Quick installation

### For Development

**Building from source:** [README.md](../README.md#building-from-source)
- Clone repository
- Build with sbt
- Install from source

**Publishing to Maven Central:** [PUBLISHING.md](PUBLISHING.md)
- Build GraalVM native binary
- Configure GPG keys
- Manual publishing commands
- CI/CD integration

## Installation Guides

| Document | Purpose | Audience |
|----------|---------|----------|
| [INSTALLATION_FLOW.md](INSTALLATION_FLOW.md) | Complete 3-stage installation workflow | Everyone |
| | - Quick start (3 commands) | |
| | - Detailed Stage 1, 2, 3 breakdown | |
| | - Troubleshooting guide | |
| | - Advanced options & post-install management | |

## Publishing & Release Guides

| Document | Purpose | Use Case |
|----------|---------|----------|
| [PUBLISHING.md](PUBLISHING.md) | Complete publishing workflow | All release processes |
| | - Quick start TL;DR | Maintainers (2 min/release) |
| | - One-time setup guide | New maintainers (5-10 min) |
| | - Semantic versioning | Version management |
| | - Release checklist | Pre/post-release verification |
| | - Troubleshooting | Problem resolution |
| [SDKMAN_MAINTENANCE.md](SDKMAN_MAINTENANCE.md) | Manage SDKMan & JVM tools | Post-installation tool management |

## File Structure

```
polyomino.dotfiles/
├── bootstrap.sh                    # Stage 1: Bootstrap installer
├── build.sbt                       # Scala build configuration
├── src/
│   ├── main/scala/polyomino/        # Scala CLI implementation
│   │   ├── Main.scala             # Entry point, command dispatch
│   │   └── dotfiles/              # Feature modules
│   │       ├── install/           # Installation (Stage 3)
│   │       ├── validate/          # Health checks
│   │       ├── theme/             # Theme engine
│   │       ├── sysutils/          # System utilities
│   │       └── ...
│   └── test/scala/polyomino/        # Unit tests
│
├── scripts/
│   ├── maintain-sdkman.sh          # SDKMan tool management
│   └── ...
│
├── README.md                       # Project overview
├── docs/
│   ├── DOCUMENTATION.md            # Documentation index (this file)
│   ├── INSTALLATION_FLOW.md        # Complete 3-stage installation
│   ├── PUBLISHING.md               # Publishing workflow & checklist
│   ├── SDKMAN_MAINTENANCE.md       # Tool management
│   ├── project-context.md          # Project context
│   └── migration-rust-to-scala.md  # Migration guide
│
└── Configuration/
    ├── config/sway/               # Sway window manager config
    ├── config/waybar/             # Status bar config
    ├── config/kitty/              # Terminal config
    ├── zsh/                       # ZSH shell config
    └── .github/workflows/         # CI/CD pipelines
```

## Understanding the 3-Stage Flow

See [INSTALLATION_FLOW.md](INSTALLATION_FLOW.md) for complete details including setup, troubleshooting, and advanced options.

### Stage 1: Bootstrap
Installs minimal system setup:
- System dependencies (Sway, Waybar, Kitty, etc.)
- Java 21 GraalVM (for native image support)
- Coursier (dependency manager)
- SDKMan (tool manager)

**Script:** `bootstrap.sh`
**Time:** 5-10 minutes

### Stage 2: Coursier
Downloads the polyomino CLI binary:
- Fetches JAR from Maven Central
- Resolves dependencies
- Creates launch script

**Command:** `cs bootstrap io.github.petrolal::polyomino:0.1.0 -o ~/.local/bin/polyomino`
**Time:** 1-2 minutes

### Stage 3: Full Setup & Tooling Provisioning
Scala-based CLI handles full setup:
- Symlink deployment & manifest tracking
- Homebrew, GitHub CLI (`gh`), and Coursier (`cs`) provisioning
- Desktop apps, fonts, and TUI tooling installation
- System health checks

**Command:** `polyomino install`
**Time:** 5-15 minutes
**Location:** `src/main/scala/polyomino/dotfiles/install/DeployInstaller.scala`

## Key Components

### Scala CLI (polyomino)

**Main entry point:** `src/main/scala/polyomino/Main.scala`

**Subcommands:**
- `install` - Full setup (Stage 3)
- `theme` - Desktop theme management
- `healthcheck` - System verification
- `lock` - Screen locker
- `idle` - Auto-lock daemon
- `screenshot` - Screen capture
- `backup/restore` - Configuration snapshots
- `install-*` - Tool-specific installers (`install-brew`, `install-gh`, `install-coursier`, `install-fonts`, etc.)

### Bootstrap Script (bash)

**File:** `bootstrap.sh`

**Functions:**
- `detect_pkg_mgr()` - Detect package manager
- `install_system_deps()` - Install OS packages
- `install_java()` - Install Java via SDKMan
- `install_coursier()` - Install Coursier

### SDKMan Tool Management

**File:** `scripts/maintain-sdkman.sh`

**Interactive setup:**
```bash
./scripts/maintain-sdkman.sh install
```

**Commands:**
- `check` - Status verification
- `upgrade` - Upgrade SDKMan
- `list` - List installed tools
- `available <tool>` - Show available versions
- `update-*` - Update specific tools
- `update-all` - Update everything

## Publishing Workflow

### Automatic (GitHub Actions)

1. Push version tag: `git tag v0.1.0 && git push origin v0.1.0`
2. GitHub Actions runs CI/CD pipeline
3. Artifacts automatically published to Maven Central

**Pipeline:** `.github/workflows/deploy.yml`

### Manual Publishing

See [MANUAL_PUBLISHING.md](MANUAL_PUBLISHING.md)

```bash
export 
export 
export PGP_PASSPHRASE="..."
sbt nativeImage
```

## Development Workflow

### Building

```bash
# Build Scala code
sbt compile

# Run tests
sbt test

# Build native image
sbt nativeImage

# Assemble fat JAR
sbt assembly
```

### Testing

```bash
# Run unit tests
sbt test

# Run specific test
sbt 'testOnly polyomino.InstallSuite'

# Run with coverage
sbt 'clean; coverage; test; coverageReport'
```

### Local Installation

```bash
# Build native image
sbt nativeImage

# Install to ~/.local/bin
mkdir -p ~/.local/bin
cp target/native-image/polyomino ~/.local/bin/

# Test
polyomino --version
```

## Configuration Files

| File | Purpose |
|------|---------|
| `build.sbt` | Scala build configuration |
| `project/plugins.sbt` | sbt plugins |
| `.github/workflows/deploy.yml` | CI/CD pipeline |
| `PKGBUILD` | Arch Linux package |
| `.SRCINFO` | AUR metadata |

## Documentation Best Practices

When updating documentation:

1. **Keep it current** - Update docs when code changes
2. **Link across docs** - Use [MarkdownLinks](files.md) for navigation
3. **Provide examples** - Show command usage with output
4. **Test instructions** - Verify guides work end-to-end
5. **Update index** - Add new docs to this file

## See Also

- [Project Context](project-context.md) - Detailed project background
- [GitHub Repository](https://github.com/petrolal/polyomino.dotfiles)
- [Maven Central](https://search.maven.org/search?q=io.github.petrolal:polyomino)
- [AUR Package](https://aur.archlinux.org/packages/polyomino-dotfiles)

## Quick Links by Task

### "I want to install polyomino"
→ [INSTALLATION_FLOW.md](INSTALLATION_FLOW.md) - Quick start (3 commands)

### "I'm having installation issues"
→ [INSTALLATION_FLOW.md](INSTALLATION_FLOW.md#troubleshooting) - Troubleshooting section

### "I want to publish a release"
→ [PUBLISHING.md](PUBLISHING.md) - Quick start (2 min) or complete setup guide

### "I need to manage JVM tools"
→ [SDKMAN_MAINTENANCE.md](SDKMAN_MAINTENANCE.md)

### "I want to build from source"
→ [README.md](../README.md#building-from-source)

### "I'm developing polyomino"
→ This file's "Development Workflow" section

---

**Last Updated:** 2026-08-12
**Status:** Documentation complete for 3-stage installation flow
