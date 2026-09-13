# project-context.md — polyomino.dotfiles AI Context & System Architecture

## Project Overview & Purpose

`polyomino.dotfiles` is a lightweight, high-performance desktop environment tooling suite designed for Sway/Wayland Linux desktop environments. It manages dynamic window autotiling, desktop theme switching across application surfaces, system health validation, config snapshot maintenance, and automated machine provisioning.

The suite follows a **Multi-Call Binary Architecture**: all sub-commands compile into a single executable umbrella (`polyomino`) via GraalVM native image. Sub-command invocations like `polyomino-theme` or `polyomino-autotiling` are handled via filesystem symlinks pointing to `polyomino`. The main binary inspects `argv[0]` or the first argument to route execution to the target submodule.

---

## Technical Stack

### Current Implementation (Scala 3 + GraalVM Native Image)
- **Language & Version**: Scala 3.5.2
- **JDK Base**: JDK 21 GraalVM Community Edition
- **Build Tool**: sbt 1.10.2+ with sbt-native-image 0.5.0 plugin
- **Runtime**: GraalVM native image (Ahead-of-Time compilation)
- **Target OS / Environment**: Arch Linux / Fedora Linux on Wayland with Sway window manager

### Core Dependencies
- **System I/O & Process Spawning**: `os-lib` (com.lihaoyi %% os-lib % "0.11.9-M8")
  - Direct process execution without intermediate buffering
  - File system operations (read, write, symlink, chmod)
  - Path manipulation and environment variable access

- **JSON Serialization**: `uPickle` (com.lihaoyi %% upickle % "4.4.3")
  - Zero-reflection compile-time macros for serialization
  - 100% GraalVM native-image compatible
  - No reflect-config.json needed

- **CLI Argument Parsing**: `mainargs` (com.lihaoyi %% mainargs % "0.7.0")
  - Type-safe command-line argument parsing
  - Compile-time macro-based generation

- **Testing**: `munit` (org.scalameta %% munit % "1.0.0")
  - Lightweight unit testing framework

### Build Configuration (build.sbt)
```scala
scalaVersion := "3.5.2"
name := "polyomino"
organization := "io.github.petrolal"
version := "0.1.0"
Compile / mainClass := Some("polyomino.Main")

// GitHub Releases via GraalVM Native Image




// Native image configuration
nativeImageOptions ++= Seq(
  "--no-fallback",
  "-H:+ReportExceptionStackTraces",
  "--enable-preview"
)

// Fat JAR assembly (local dev / assembly)
assembly / assemblyJarName := s"${name.value}-${version.value}-assembly.jar"
assembly / mainClass := Some("polyomino.Main")

// Package manifest for Coursier compatibility
packageBin / packageOptions += Package.ManifestAttributes(...)
```

---

## Architectural Invariants & Patterns

### 1. Single Multi-Call Native Executable
- All modules (`autotiling`, `theme`, `refresh`, `sysutils`, `maintenance`, `install`, `pickers`, `sdd`, `validate`) compile into a single native binary (`polyomino`)
- Binary size optimized via GraalVM native-image compilation (~40-60 MB)
- Startup latency: 15-50 ms
- Memory RSS: Under 60 MB peak RAM
- Command aliases (`polyomino-<subcommand>`) are symlinks in `~/.local/bin` pointing to the single `polyomino` binary

### 2. Non-Reflective System I/O
- Process execution and file manipulation use `os-lib` (direct POSIX syscalls)
- JSON parsing uses `uPickle` derive macros (compile-time code generation, zero reflection)
- No reflection configuration (`reflect-config.json`) required for GraalVM native-image

### 3. Error Handling via Either[PolyominoError, T]
- Functional error propagation using `Either` monad
- No exceptions at module boundaries
- Typed error domain: `CommandError`, `ConfigError`, `ProcessExecutionError`, `UnknownCommandError`
- Main entry point catches and formats errors with ANSI color codes

### 4. Context-Driven Architecture
- `Context` case class holds XDG paths, environment variables, Sway IPC socket
- Discovered once at startup via `Context.discover()`
- Threaded through all subcommand invocations
- Enables testability and path portability across environments

### 5. Streamed Process Execution
- Heavy sub-process output (`swaymsg`, `wofi`, `pactl`) streams directly without intermediate heap buffering
- Maintains low memory usage even during long-running operations
- Implemented via `os.proc(...).stream()` in os-lib

---

## Module Structure & Responsibility Map

```
polyomino.dotfiles/
├── src/main/scala/polyomino/
│   ├── Main.scala                          # Entry point, command dispatch router
│   │
│   └── dotfiles/
│       ├── context/Context.scala           # XDG path discovery, environment setup
│       ├── error/PolyominoError.scala        # Error domain types (Either-based)
│       │
│       ├── autotiling/
│       │   └── AutotilingDaemon.scala      # Sway IPC listener, Fibonacci spiral window layout
│       │
│       ├── install/
│       │   ├── DeployInstaller.scala       # Main installer, symlink creation, healthcheck
│       │   ├── Manifest.scala              # Configuration manifest model
│       │   └── ToolInstallers.scala        # Subcommands: fonts, apps, browser, devops, zsh, sdkman, nvim
│       │
│       ├── theme/
│       │   ├── ThemeEngine.scala           # Theme application logic, surface coordination
│       │   └── Palette.scala               # Color palette definitions (AWS, Azure, GCP, OCI)
│       │
│       ├── refresh/
│       │   └── RefreshEngine.scala         # App reload signals (waybar, kitty, sway, GTK color-scheme)
│       │
│       ├── sysutils/
│       │   └── SysUtils.scala              # Lock screen, idle daemon, screenshot helpers
│       │
│       ├── wallpaper/
│       │   └── WallpaperEngine.scala        # Swap wallpaper within the active flavor (next/prev/random)
│       │
│       ├── pickers/
│       │   └── WofiPickers.scala           # Wofi GUI launchers (theme-picker, wallpaper-picker, whichkey)
│       │
│       ├── maintenance/
│       │   └── Maintenance.scala           # Backup/restore snapshots, git pull & re-install
│       │
│       ├── sdd/
│       │   └── SpecDrivenDev.scala         # AI context generator for development workflows
│       │
│       └── validate/
│           └── Validator.scala             # Read-only health check of system setup
│
├── src/test/scala/polyomino/
│   ├── *Suite.scala                        # Unit tests for each module (munit)
│
├── build.sbt                               # sbt build configuration
├── bootstrap.sh                            # Quick installer bootstrap script
├── .github/workflows/deploy.yml            # GitHub Actions CI/CD pipeline
└── docs/
    ├── DOCUMENTATION.md                    # Documentation index
    ├── INSTALLATION_FLOW.md                # 3-stage installation workflow
    ├── PUBLISHING.md                       # GitHub Releases & AUR publishing
    ├── SDKMAN_MAINTENANCE.md               # JVM tool management
    ├── migration-rust-to-scala.md          # Rust → Scala migration notes
    └── project-context.md                  # This file
```

---

## Desktop Surface Integrations

`polyomino.dotfiles` coordinates state and configuration files across the following desktop components:

| Component | Integration Method | Purpose |
|-----------|-------------------|---------|
| **Sway Window Manager** | IPC socket (`SWAYSOCK`) via `swaymsg` | Layout autotiling (`swaymsg split v/h`), window focus, config reload |
| **Waybar** | Signal trigger (`pkill -SIGUSR1 waybar`) | Status bar reload and dynamic style updates |
| **Kitty Terminal** | IPC signaling (`kill -USR1`) | Live theme color palette updates without restart |
| **Wofi Launcher** | Graphical UI menus | Interactive theme selection (`theme-picker`), wallpaper selection (`wallpaper-picker`), keybindings cheatsheet (`whichkey`) |
| **Neovim** | `$NVIM_LISTEN_ADDRESS` IPC | Live color scheme reload |
| **GTK / GNOME Settings** | `gsettings` CLI | Sync dark/light color-scheme setting |
| **Swaylock** | Configuration file substitution | Lock screen styling (colors, font) per active theme |
| **Swayidle** | Daemon subprocess | Auto-lock, DPMS screen off, suspend on inactivity |

---

## Subcommands & Use Cases

### Installation & System Setup
| Command | Purpose |
|---------|---------|
| `polyomino install` | Deploy dotfiles, create symlinks, run full installer setup |
| `polyomino full-install` | Install system packages, Homebrew, gh, Coursier, apps, fonts, and tooling |
| `polyomino install-deps` | Install system & build dependencies (sbt, gcc, git, etc.) |
| `polyomino install-brew` | Install Homebrew package manager |
| `polyomino install-gh` | Install GitHub CLI (`gh`) |

| `polyomino install-fonts` | Download and install JetBrainsMono Nerd Font |
| `polyomino install-apps` | Install core desktop apps (sway, waybar, kitty, etc.) |
| `polyomino install-browser` | Install web browser (chromium/firefox) |
| `polyomino install-devops` | Install devops tooling (docker, terraform, kubectl) |
| `polyomino install-zsh` | Install zsh + oh-my-zsh + plugins, set shell |
| `polyomino install-sdkman` | Install SDKMAN! and JVM tooling |
| `polyomino install-tools` | Install TUI tools (spotify_player, bluetui, aerc) |

### Desktop Management
| Command | Purpose |
|---------|---------|
| `polyomino theme` | Apply desktop theme flavor + wallpaper mode live |
| `polyomino wallpaper` | Swap the wallpaper within the active flavor (`next`/`prev`/`random`/`list`/`<name>`) without re-theming |
| `polyomino theme-picker` | Wofi GUI menu to select and apply themes |
| `polyomino wallpaper-picker` | Wofi GUI menu of the active flavor's wallpapers |
| `polyomino runtime-refresh` | Trigger live reload signals across running apps |
| `polyomino os-colorscheme` | Sync GNOME/GTK dark/light color-scheme setting |
| `polyomino autotiling` | Background daemon: Fibonacci spiral window autotiling |
| `polyomino lock` | Lock screen via swaylock with active theme colors |
| `polyomino idle` | Launch swayidle daemon (auto-lock, DPMS, suspend) |
| `polyomino screenshot` | Capture screen (full/region/window) to file and clipboard |
| `polyomino whichkey` | Wofi GUI cheatsheet of live Sway keybindings |

### Maintenance & Validation
| Command | Purpose |
|---------|---------|
| `polyomino healthcheck` | Read-only health check: configs, tools, fonts, env vars |
| `polyomino backup` | Create timestamped `.tar.gz` archive of dotfiles |
| `polyomino restore` | Restore snapshot created by `polyomino backup` |
| `polyomino update` | Git pull dotfiles repository and re-run installer |
| `polyomino sdd` | Generate token-efficient AI context and specs |

---

## Command Dispatch Flow

```
polyomino [COMMAND] [ARGS...]
    ↓
Main.main(args) / dispatch(args)
    ↓
Parse argv[0] or argv[1] to extract command name
    ↓
Context.discover() → Either[ConfigError, Context]
    ↓
dispatchModule(cmd, ctx, args) → Either[PolyominoError, Unit]
    ↓
Route to module:
  - "theme" → ThemeEngine.run()
  - "autotiling" → AutotilingDaemon.run()
  - "install" → DeployInstaller.run()
  - "validate" → Validator.run()
  - "lock" → SysUtils.runLock()
  - etc.
    ↓
Execute, catch Either[PolyominoError, _]
    ↓
Format error with ANSI colors or exit cleanly
```

---

## Performance & Optimization Targets

### Binary Optimization
- **GraalVM native-image flags**:
  - `--no-fallback`: Fail at build time if reflection can't be resolved
  - `-H:+ReportExceptionStackTraces`: Include stack trace symbols
  - `--enable-preview`: Enable preview features for future Scala/JVM compatibility

### Runtime Performance
- **Startup latency**: 15–50 ms (native image, no JVM startup)
- **Memory RSS**: <60 MB peak RAM
- **Process spawning**: Direct `os.proc()` calls, no intermediate buffering
- **Serialization**: Compile-time macros (uPickle), zero reflection overhead

### Binary Size
- Typical native image size: 40–60 MB
- Standalone native binary: ~20-35 MB (GraalVM native image, zero JVM startup)
- 

---

## Testing Strategy

All modules have corresponding unit test suites in `src/test/scala/polyomino/`:
- `MainSuite.scala` — Command dispatch, argv parsing
- `AutotilingSuite.scala` — Fibonacci layout calculations
- `InstallSuite.scala` — Symlink creation, path validation
- `ThemeSuite.scala` — Palette lookups, color conversions
- `SysUtilsSuite.scala` — Lock/idle/screenshot command generation
- `ValidateSuite.scala` — Health check validators
- `MaintenanceSuite.scala` — Backup/restore logic
- `RefreshSuite.scala` — App reload signal generation

**Test framework**: munit (lightweight, compile-time friendly)

**Execution**: `sbt test` or `sbt 'testOnly polyomino.AutotilingSuite'`

---

## Publishing & Distribution

### GitHub Releases (Native Binary)
- Published via Sonatype Central Portal
- Coordinates: `io.github.petrolal:polyomino:0.1.0`
- Artifact types:
  - JAR: `polyomino-0.1.0.jar` (with dependencies in manifest)
  - Fat JAR: `polyomino-0.1.0-assembly.jar` (all deps bundled)
  - Native image: Built via GitHub Actions, attached to GitHub Releases

### Installation Methods
1. **Pre-built native binary (Recommended)**:
   ```bash
   curl -fsSL https://github.com/petrolal/polyomino.dotfiles/releases/latest/download/polyomino-x86_64-linux -o ~/.local/bin/polyomino
   ```

2. **From GitHub Releases (Native Binary)**:
   ```bash
   curl -L https://github.com/petrolal/polyomino.dotfiles/releases/download/v0.1.0/polyomino -o ~/.local/bin/polyomino
   chmod +x ~/.local/bin/polyomino
   ```

3. **From Source**:
   ```bash
   git clone https://github.com/petrolal/polyomino.dotfiles.git
   cd polyomino.dotfiles
   sbt nativeImage
   cp target/native-image/polyomino ~/.local/bin/
   ```

### AUR Package
- Arch Linux users: `yay -S polyomino-dotfiles`
- PKGBUILD manages binary download and symlink creation

---

## Key Design Decisions

### 1. Why Scala 3 + GraalVM?
- **Type safety**: Scala 3 enum-based error types over C-style error codes
- **Native image**: Startup latency <50ms (critical for interactive tools)
- **Zero reflection**: `uPickle` and `mainargs` macros avoid GraalVM config burden
- **Functional style**: `Either[E, A]` enables clean error propagation
- **Ecosystem**: `os-lib` provides batteries-included Unix I/O
- **Distribution**: GitHub Releases native binary + AUR (no JVM required)

### 2. Why Not Scala.js or JVM?
- **Startup latency**: JVM warm-up would violate 50ms target
- **Memory overhead**: JVM heap adds 100+ MB, exceeds 60MB RSS target
- **Distribution**: Native image eliminates "Java not installed" friction

### 3. Why Multi-Call Binary Over Multiple Binaries?
- **Single deploy surface**: One binary + symlinks easier to manage than 20 separate binaries
- **Shared state**: Context discovered once, threaded through all subcommands
- **Fast launch**: Symlink invocation still ~30ms (faster than forking multiple JVM processes)

### 4. Why XDG Base Directory Spec?
- **Portable**: Works across different Linux distributions
- **User-friendly**: Respects existing user config locations
- **Testable**: Environment variables can override paths in tests

---

## Future Enhancements & TODOs

- [ ] OpenRGB hardware RGB lighting sync module
- [ ] Wayland clipboard access (currently X11-only screenshots)
- [ ] Hyprland window manager support (alongside Sway)
- [ ] systemd user service templates for autotiling/idle daemons
- [ ] Shell completion generators (bash, zsh, fish)
- [ ] Configuration schema validation with `Tapir` or similar

---

## External References

- **Scala 3 Documentation**: https://docs.scala-lang.org/
- **GraalVM Native Image**: https://www.graalvm.org/native-image/
- **os-lib Documentation**: https://github.com/lihaoyi/os-lib
- **uPickle Documentation**: https://github.com/lihaoyi/upickle
- **mainargs Documentation**: https://github.com/lihaoyi/mainargs
- **Sway IPC Documentation**: https://man.archlinux.org/man/sway-ipc.7.en
- **Sway Configuration**: https://man.archlinux.org/man/sway.5.en
- **XDG Base Directory Specification**: https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html
- **GitHub Repository**: https://github.com/petrolal/polyomino.dotfiles
  - **GitHub Releases**: https://github.com/petrolal/polyomino.dotfiles/releases

---

**Last Updated**: 2026-08-13  
**Status**: Scala 3 + GraalVM native image implementation complete  
**Previous Stack**: Rust 2021 (migrated to Scala 3 for GraalVM native image distribution)
