# Rust to Scala Migration Specification — polyomino.dotfiles

## 1. Executive Summary & Migration Status

- **Status**: **Phase 1 Complete (Scaffold & Module Entrypoints Implemented - Commit `7af5ea1`) | Phase 2 Active (Deep Implementation, MUnit Testing & Rust Decommissioning)**
- **Current Baseline**: Rust 2021 codebase (`src/`, `Cargo.toml`) supplying 24 multi-call CLI subcommands for Sway/Wayland Linux desktop automation.
- **Target Stack**: **Scala 3.5.2** compiled via **GraalVM `native-image`** (`sbt-native-image` 0.5.0, JDK 21 base) into a single standalone native Linux ELF binary (`polyomino`).
- **Primary Driver**: Maintainer ergonomics and JVM ecosystem familiarity (Java/Kotlin/Scala).
- **Accepted Trade-offs**: Startup latency tolerance of 15ms–50ms (acceptable for Sway hooks) and 1–5 minute GraalVM compilation times during release builds.

---

## 2. Pinned Stack & Architectural Rules

### Technology Stack Pins

| Component | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Language** | Scala | `3.5.2` | Primary development language |
| **JVM Base** | GraalVM Community Edition | `21.0.2` | AOT compilation engine |
| **Build Tool** | `sbt` | `1.10.2` | Build and dependency management |
| **AOT Plugin** | `sbt-native-image` | `0.5.0` | sbt task for GraalVM `native-image` generation |
| **Process & File I/O** | `os-lib` | `0.11.9-M8` | POSIX subprocess spawning and filesystem operations |
| **JSON Parser** | `uPickle` | `4.4.3` | Compile-time static derive macro serialization (zero reflection) |
| **CLI Parser** | `mainargs` | `0.7.0` | Command line parameter parsing |
| **Test Framework** | `MUnit` | `1.0.0` | Unit and integration test suite |

### Architectural Invariants & Rules

- **AD-1 — Single Multi-Call Native Binary Target `[ADOPTED]`**
  - All 24 subcommand entrypoints compile into a single executable `polyomino`. Subcommand aliases (`polyomino-autotiling`, `polyomino-theme`, etc.) are created as filesystem symlinks pointing to `polyomino` in `~/.local/bin`.
  - *Prevents:* Multiplying GraalVM build times by 24x (~40+ minutes of build time) and duplicating Substrate VM memory footprints.

- **AD-2 — Non-Reflective JSON & System I/O Abstractions `[ADOPTED]`**
  - File/process operations MUST use `os-lib`. JSON serialization MUST use `uPickle` static `ReadWriter` macros.
  - *Prevents:* Reflection runtime crashes under GraalVM Native Image and eliminates complex `reflect-config.json` setup.

- **AD-3 — Pure Command-Line Argument Parsing Strategy `[ADOPTED]`**
  - `polyomino.Main` inspects `argv(0)` to detect `polyomino-<cmd>` symlinks or `argv(1)` under umbrella `polyomino <cmd>` calls, stripping prefixes and delegating cleanly to submodules.
  - *Prevents:* Signature mismatch between symlinks and umbrella invocations.

- **AD-4 — Direct Process Stream Handling & Zero Heap Bloat `[ADOPTED]`**
  - Subprocess calls (`swaymsg`, `wofi`, `swaylock`, `pactl`) must stream stdout/stderr using `os.proc(...).call()` or `os.proc(...).spawn()`.
  - *Prevents:* Unnecessary JVM heap buffer allocations during high-frequency Sway window focus events.

---

## 3. Module & Use Case Migration Mapping

The table below maps every legacy Rust module in `src/` to its corresponding target Scala package, primary use case, status, and assigned Epic/Story:

| Legacy Rust Module | Target Scala Module | Primary Use Case | Status | Epic / Story Reference |
| :--- | :--- | :--- | :--- | :--- |
| `src/main.rs` & `src/lib.rs` | `polyomino.Main` | Umbrella `argv(0)` CLI dispatcher & subcommand routing | **COMPLETE** | **Epic 1**: Story 1.1, 1.2 |
| `src/context.rs` | `polyomino.dotfiles.context.Context` | XDG paths (`~/.config`), active theme state & Sway socket discovery | **COMPLETE** | **Epic 2**: Story 2.1 |
| `src/sysutils.rs` | `polyomino.dotfiles.sysutils.SysUtils` | Styled screen lock (`lock`), auto-idle daemon (`idle`), screen capture (`screenshot`) | **COMPLETE** | **Epic 2**: Story 2.4 |
| `src/refresh.rs` | `polyomino.dotfiles.refresh.RefreshEngine` | Live app reloads (`runtime-refresh`), GTK color sync (`os-colorscheme`), RGB sync (`rgb-theme`) | **COMPLETE** | **Epic 3**: Story 3.2 |
| `src/pickers.rs` | `polyomino.dotfiles.pickers.WofiPickers` | Interactive Wofi GUI theme launcher (`theme-picker`) & keybindings cheatsheet (`whichkey`) | **COMPLETE** | **Epic 3**: Story 3.3 |
| `src/maintenance.rs` | `polyomino.dotfiles.maintenance.Maintenance` | Tarball backup (`backup`), snapshot restore (`restore`), and git pull update (`update`) | **COMPLETE** | **Epic 5**: Story 5.1, 5.2 |
| `src/install/deploy.rs` | `polyomino.dotfiles.install.DeployInstaller` | Machine setup installer (`install`/`deploy`), manifest tracking & symlinks in `~/.local/bin` | **IN PROGRESS** | **Epic 6**, **Epic 7**: Story 7.1 |
| `src/install/*.rs` | `polyomino.dotfiles.install.ToolInstallers` | Granular installers (`install-fonts`, `install-apps`, `install-browser`, `install-devops`, `install-zsh`, `install-sdkman`, `install-nvim`) | **IN PROGRESS** | **Epic 6**, **Epic 7**: Story 7.2 |
| `src/theme/` | `polyomino.dotfiles.theme.ThemeEngine` | Dynamic desktop theme & wallpaper switching (`theme`) & template rendering | **IN PROGRESS** | **Epic 3**, **Epic 8**: Story 8.1 |
| `src/autotiling.rs` | `polyomino.dotfiles.autotiling.AutotilingDaemon` | Sway IPC socket event listener daemon for Fibonacci spiral window autotiling (`autotiling`) & multi-monitor support | **IN PROGRESS** | **Epic 4**, **Epic 8**: Story 8.2 |
| `src/validate.rs` | `polyomino.dotfiles.validate.Validator` | Read-only desktop health check (`validate`) & 25+ point system audit | **IN PROGRESS** | **Epic 2**, **Epic 9**: Story 9.1 |
| `tests/*.rs` (8 files) | `src/test/scala/polyomino/*Suite.scala` | MUnit integration test suites for all 8 modules | **PLANNED** | **Epic 9**: Story 9.2 |
| `bootstrap.sh` & `.github/` | `bootstrap.sh` & `.github/workflows/ci.yml` | sbt nativeImage build automation & GraalVM CI pipeline | **PLANNED** | **Epic 10**: Story 10.1 |
| `src/*.rs` & `Cargo.toml` | N/A | Safe decommissioning & removal of legacy Rust files | **PLANNED** | **Epic 10**: Story 10.2 |

---

## 4. Phase 2 Execution Roadmap

1. **Epic 7**: Deep Manifest Tracking (`.dotfiles_manifest`), Pre-Config Preservation, and Package Manager Installers (`pacman`/`dnf`/`apt`/`brew`).
2. **Epic 8**: Theme Color Template Rendering (`kitty.conf`, `waybar/style.css`, `wofi.css`) & Sway IPC Multi-Monitor Autotiling.
3. **Epic 9**: 25+ Diagnostic Point System Audit (`validate`) & MUnit Test Suite Migration (`src/test/scala/polyomino/`).
4. **Epic 10**: `bootstrap.sh` GraalVM Integration, GitHub Actions CI Workflow (`sbt nativeImage`), and Final Rust Stack Cleanup.
