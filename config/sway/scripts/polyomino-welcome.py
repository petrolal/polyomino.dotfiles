#!/usr/bin/env python3
"""
Polyomino Welcome Center
Asymmetrical Polyomino Grid layout for quick start actions, gaming optimizations, and system maintenance.
"""

import os
import re
import sys
import json
import shlex
import shutil
import subprocess
from pathlib import Path
from datetime import datetime

# Ensure GTK 3
import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk, Gdk, GLib, Gio

GLib.set_prgname("polyomino-welcome")
GLib.set_application_name("Polyomino Welcome Center")

# Force dark theme for standard GTK elements
try:
    gtk_settings = Gtk.Settings.get_default()
    if gtk_settings:
        gtk_settings.set_property("gtk-application-prefer-dark-theme", True)
except Exception:
    pass

CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "polyomino"
SETTINGS_FILE = CONFIG_DIR / "welcome.json"
TOKENS_FILE = CONFIG_DIR / "theme" / "tokens.css"
THEME_STATE_FILE = CONFIG_DIR / "theme" / "state"

DEFAULT_PALETTE = {
    "bg_base": "#0d1117",
    "bg_mantle": "#161b22",
    "bg_surface": "#1c2128",
    "bg_surface_hover": "#21262d",
    "bg_surface_active": "#2d333b",
    "border_subtle": "#30363d",
    "text_primary": "#f0f6fc",
    "text_secondary": "#8b949e",
    "accent_violet": "#8b5cf6",
    "accent_violet_glow": "#a78bfa",
    "accent_amber": "#d97706",
    "accent_amber_glow": "#f59e0b",
    "accent_blue": "#3b82f6",
    "accent_blue_glow": "#60a5fa",
    "accent_teal": "#06b6d4",
    "accent_teal_glow": "#22d3ee",
}

def load_settings():
    if SETTINGS_FILE.exists():
        try:
            with open(SETTINGS_FILE, "r") as f:
                return json.load(f)
        except Exception:
            pass
    return {"autostart": True}

def save_settings(settings):
    try:
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        with open(SETTINGS_FILE, "w") as f:
            json.dump(settings, f, indent=2)
    except Exception as e:
        print(f"Error saving settings: {e}", file=sys.stderr)

def run_cmd(cmd_list, in_terminal=False):
    term = os.environ.get("TERMINAL", "kitty")
    if in_terminal:
        cmd_str = shlex.join(cmd_list)
        full_cmd = [term, "-e", "bash", "-c", f"{cmd_str}; echo ''; read -n 1 -s -r -p 'Press any key to close...'"]
    else:
        full_cmd = cmd_list
    subprocess.Popen(full_cmd, start_new_session=True)

def load_controller_settings():
    return load_settings().get("controller", {"steam_input_enabled": True})

def save_controller_setting(key, value):
    settings = load_settings()
    controller = settings.get("controller", {})
    controller[key] = value
    settings["controller"] = controller
    save_settings(settings)

def resolve_editor():
    editor = os.environ.get("EDITOR")
    if editor:
        return editor
    if shutil.which("nvim"):
        return "nvim"
    return "vi"

def _read_text(path):
    try:
        with open(path, "r", errors="ignore") as f:
            return f.read()
    except Exception:
        return ""

def _udev_bus_for_device(dev_path):
    try:
        out = subprocess.run(["udevadm", "info", "-q", "property", dev_path],
                              capture_output=True, text=True, timeout=2).stdout
        m = re.search(r"^ID_BUS=(\S+)$", out, re.MULTILINE)
        if m:
            bus = m.group(1).lower()
            if bus == "usb":
                return "USB"
            if bus == "bluetooth":
                return "Bluetooth"
            return bus.upper()
    except Exception:
        pass
    return "Unknown"

def _battery_percent_for_name(device_name):
    try:
        out = subprocess.run(["upower", "-e"], capture_output=True, text=True, timeout=2).stdout
        for line in out.splitlines():
            line = line.strip()
            low = line.lower()
            if not line or ("input" not in low and "gaming" not in low and "joypad" not in low and "gamepad" not in low):
                continue
            info = subprocess.run(["upower", "-i", line], capture_output=True, text=True, timeout=2).stdout
            m = re.search(r"percentage:\s+(\d+)%", info)
            if m:
                return int(m.group(1))
    except Exception:
        pass
    return None

def list_gamepads():
    """Parse /proc/bus/input/devices for js* handlers, enrich with bus type + battery."""
    devices = []
    content = _read_text("/proc/bus/input/devices")
    for block in content.split("\n\n"):
        if not block.strip():
            continue
        handlers_line = next((l for l in block.splitlines() if l.startswith("H: Handlers=")), "")
        m = re.search(r"\b(js\d+)\b", handlers_line)
        if not m:
            continue
        js_name = m.group(1)
        name_line = next((l for l in block.splitlines() if l.startswith("N: Name=")), "")
        name = name_line.split("=", 1)[1].strip('"') if "=" in name_line else js_name
        dev_path = f"/dev/input/{js_name}"
        bus = _udev_bus_for_device(dev_path)
        battery = _battery_percent_for_name(name)
        devices.append({"js": js_name, "name": name or js_name, "bus": bus, "battery": battery, "path": dev_path})
    return devices

EMULATOR_LABELS = {
    "mesen": "Mesen (NES)",
    "bsnes": "bsnes (SNES)",
    "sameboy": "SameBoy (GB/GBC)",
    "mgba": "mGBA (GBA)",
    "mame": "MAME (Arcade)",
    "flycast": "Flycast (Dreamcast)",
    "blastem": "BlastEm (Genesis)",
    "duckstation": "DuckStation (PS1)",
    "simple64": "simple64 (N64)",
}

EMULATOR_BINARIES = {
    "mesen": ["mesen"],
    "bsnes": ["bsnes"],
    "sameboy": ["sameboy"],
    "mgba": ["mgba-qt", "mgba"],
    "mame": ["mame"],
    "flycast": ["flycast"],
    "blastem": ["blastem"],
    "duckstation": ["duckstation-qt", "duckstation-nogui", "duckstation"],
    "simple64": ["simple64-gui", "simple64"],
}

def find_emulator_binary(emu_id):
    for candidate in EMULATOR_BINARIES.get(emu_id, []):
        if shutil.which(candidate):
            return candidate
    return None

ROMS_DIR = Path.home() / "Games" / "ROMs"
ROM_BACKUPS_DIR = Path.home() / "Games" / "Backups"

def _rom_launcher_script():
    exe = shutil.which("polyomino-rom-launcher")
    if exe:
        return [exe]
    local = Path(__file__).resolve().parent / "polyomino-rom-launcher.sh"
    return ["bash", str(local)]

def _patch_rom_script():
    exe = shutil.which("polyomino-patch-rom")
    if exe:
        return [exe]
    local = Path(__file__).resolve().parent / "polyomino-patch-rom.sh"
    return ["bash", str(local)]

def list_roms():
    """Runs `polyomino-rom-launcher --list`, parsing tab-separated platform/title/path rows."""
    roms = []
    try:
        out = subprocess.run(_rom_launcher_script() + ["--list"],
                              capture_output=True, text=True, timeout=5).stdout
        for line in out.splitlines():
            parts = line.split("\t")
            if len(parts) == 3:
                roms.append({"platform": parts[0], "title": parts[1], "path": parts[2]})
    except Exception:
        pass
    return roms

def launch_rom(rom_path):
    run_cmd(_rom_launcher_script() + [rom_path])

def apply_rom_patch(base_rom, patch_file):
    run_cmd(_patch_rom_script() + [base_rom, patch_file], in_terminal=True)

def quick_update_gym(base_rom):
    run_cmd(_patch_rom_script() + ["--auto", base_rom], in_terminal=True)

def snapshot_saves():
    """Tar up emulator save/SRAM data into a timestamped archive under ~/Games/Backups/."""
    ts = datetime.now().strftime("%Y%m%d-%H%M%S")
    ROM_BACKUPS_DIR.mkdir(parents=True, exist_ok=True)
    archive = ROM_BACKUPS_DIR / f"saves-{ts}.tar.gz"
    save_dirs = [
        Path.home() / ".local" / "share" / "mesen",
        Path.home() / ".local" / "share" / "bsnes",
        Path.home() / ".local" / "share" / "sameboy",
        Path.home() / ".local" / "share" / "mgba",
        Path.home() / ".local" / "share" / "mame",
        Path.home() / ".config" / "duckstation",
        Path.home() / ".local" / "share" / "simple64",
    ]
    existing = [str(d) for d in save_dirs if d.exists()]
    try:
        cmd = ["tar", "-czf", str(archive)]
        cmd += existing
        if ROMS_DIR.exists():
            srm_sav = subprocess.run(
                ["find", str(ROMS_DIR), "-type", "f", "(", "-iname", "*.srm", "-o", "-iname", "*.sav", ")"],
                capture_output=True, text=True, timeout=5).stdout.splitlines()
            cmd += srm_sav
        if len(cmd) > 3:
            subprocess.run(cmd, timeout=30)
            run_cmd(["notify-send", "Snapshot Saves", f"Saved to {archive}"])
        else:
            run_cmd(["notify-send", "Snapshot Saves", "No save data found to back up."])
    except Exception as e:
        run_cmd(["notify-send", "Snapshot Saves", f"Failed: {e}"])

# (icon, title, description, install subcommand, badge)
INSTALL_SECTIONS = [
    ("📀", "System Dependencies", "Base build tooling: sbt, gcc, git, and friends.", "install-deps", "SYSTEM"),
    ("🖥️", "Desktop Apps", "Core desktop apps: sway, waybar, kitty, etc.", "install-apps", "DESKTOP"),
    ("🪟", "SwayFX Compositor", "Install/upgrade the SwayFX Wayland compositor.", "install-sway", "DESKTOP"),
    ("🔤", "Fonts", "JetBrainsMono Nerd Font.", "install-fonts", "DESKTOP"),
    ("🌐", "Web Browser", "Chromium or Firefox.", "install-browser", "DESKTOP"),
    ("🔔", "Notifications (SwayNC)", "SwayNC notification daemon & control center.", "install-swaync", "DESKTOP"),
    ("🕹️", "Gaming Stack", "Feral GameMode, Gamescope, MangoHud, Vulkan drivers, Steam.", "install-gaming", "GAMING"),
    ("☁️", "DevOps Tooling", "docker, terraform, ansible, aws/gcp/oci, kubectl, etc.", "install-devops", "DEVOPS"),
    ("🐚", "Zsh + Plugins", "zsh, oh-my-zsh, plugins, and set as default shell.", "install-zsh", "SHELL"),
    ("🟩", "Node.js (NVM)", "Node.js & npm via NVM.", "install-node", "LANG"),
    ("☕", "SDKMAN! (JVM)", "SDKMAN! and JVM tooling.", "install-sdkman", "LANG"),
    ("🍺", "Homebrew", "Homebrew package manager.", "install-brew", "SYSTEM"),
    ("🐙", "GitHub CLI", "gh command-line tool.", "install-gh", "SYSTEM"),
    ("⚙️", "Coursier", "Scala artifact/launcher manager.", "install-coursier", "LANG"),
    ("🧰", "TUI Tools", "spotify_player, bluetui, aerc.", "install-tools", "SYSTEM"),
    ("✈️", "Telegram", "Telegram desktop client.", "install-telegram", "DESKTOP"),
    ("📁", "Yazi", "Terminal file manager.", "install-yazi", "SYSTEM"),
    ("📊", "Fastfetch", "System information tool.", "install-fastfetch", "SYSTEM"),
    ("🎵", "Spotify Player", "TUI Spotify client.", "install-spotify", "DESKTOP"),
    ("⚡", "Zoxide", "Smarter `cd` command.", "install-zoxide", "SYSTEM"),
]

def _wpctl_status():
    try:
        return subprocess.run(["wpctl", "status"], capture_output=True, text=True, timeout=2).stdout
    except Exception:
        return ""

def list_audio_sinks():
    """Parse `wpctl status` Sinks section: [(id, name, is_default)]."""
    sinks = []
    out = _wpctl_status()
    in_sinks = False
    for line in out.splitlines():
        if re.search(r"^\s*Sinks:", line):
            in_sinks = True
            continue
        if in_sinks and re.search(r"^\s*(Sources|Filters|Streams):", line):
            break
        if not in_sinks:
            continue
        m = re.search(r"(\*)?\s*(\d+)\.\s+(.+?)\s+\[vol:", line)
        if m:
            sinks.append({"id": m.group(2), "name": m.group(3).strip(), "default": m.group(1) == "*"})
    return sinks

def get_default_sink():
    for sink in list_audio_sinks():
        if sink["default"]:
            return sink
    return None

def get_default_source_muted():
    out = _wpctl_status()
    in_sources = False
    for line in out.splitlines():
        if re.search(r"^\s*Sources:", line):
            in_sources = True
            continue
        if in_sources and re.search(r"^\s*(Sinks|Filters|Streams):", line):
            break
        if in_sources and "*" in line:
            return "[MUTED]" in line
    return None

def toggle_default_sink():
    sinks = list_audio_sinks()
    if len(sinks) < 2:
        return None
    current_idx = next((i for i, s in enumerate(sinks) if s["default"]), 0)
    next_sink = sinks[(current_idx + 1) % len(sinks)]
    try:
        subprocess.run(["wpctl", "set-default", next_sink["id"]], timeout=2)
    except Exception:
        pass
    return next_sink

def open_audio_mixer():
    if shutil.which("wiremix"):
        run_cmd(["kitty", "--title", "wiremix", "-e", "wiremix"])
    elif shutil.which("pulsemixer"):
        run_cmd(["kitty", "--title", "pulsemixer", "-e", "pulsemixer"])
    else:
        run_cmd(["notify-send", "Audio Mixer", "Install wiremix or pulsemixer to manage audio devices."])

def is_recording():
    try:
        return subprocess.run(["pgrep", "-x", "wf-recorder"], capture_output=True, timeout=2).returncode == 0
    except Exception:
        return False

def toggle_recording():
    if is_recording():
        try:
            subprocess.run(["pkill", "-INT", "-x", "wf-recorder"], timeout=2)
        except Exception:
            pass
        return False
    if not shutil.which("wf-recorder"):
        run_cmd(["notify-send", "Screen Recording", "Install wf-recorder to record the screen."])
        return False
    out_dir = Path.home() / "Videos"
    out_dir.mkdir(parents=True, exist_ok=True)
    ts = subprocess.run(["date", "+%Y%m%d-%H%M%S"], capture_output=True, text=True, timeout=2).stdout.strip()
    out_file = out_dir / f"polyomino-recording-{ts}.mp4"
    run_cmd(["wf-recorder", "-f", str(out_file)])
    return True

def take_area_screenshot():
    if shutil.which("grim") and shutil.which("slurp"):
        run_cmd(["sh", "-c", 'grim -g "$(slurp)" "$HOME/Pictures/polyomino-$(date +%Y%m%d-%H%M%S).png"'])
    else:
        run_cmd(["notify-send", "Screenshot", "Install grim and slurp to capture the screen."])

def take_fullscreen_screenshot():
    if shutil.which("grim"):
        run_cmd(["sh", "-c", 'grim "$HOME/Pictures/polyomino-$(date +%Y%m%d-%H%M%S).png"'])
    else:
        run_cmd(["notify-send", "Screenshot", "Install grim to capture the screen."])

def list_scratchpad_windows():
    """Walk `swaymsg -t get_tree` for nodes under the __i3_scratch workspace."""
    windows = []
    try:
        out = subprocess.run(["swaymsg", "-t", "get_tree"], capture_output=True, text=True, timeout=2).stdout
        tree = json.loads(out)
    except Exception:
        return windows

    def find_scratch(node):
        if node.get("name") == "__i3_scratch":
            return node
        for child in node.get("nodes", []) + node.get("floating_nodes", []):
            found = find_scratch(child)
            if found:
                return found
        return None

    def collect_windows(node, acc):
        if node.get("app_id") or (node.get("window_properties") or {}).get("class"):
            name = node.get("name") or node.get("app_id") or "Unknown"
            acc.append({"con_id": node.get("id"), "name": name})
        for child in node.get("nodes", []) + node.get("floating_nodes", []):
            collect_windows(child, acc)

    scratch = find_scratch(tree)
    if scratch:
        collect_windows(scratch, windows)
    return windows

def toggle_scratchpad_window(con_id):
    try:
        subprocess.run(["swaymsg", f"[con_id={con_id}]", "scratchpad", "show"], timeout=2)
    except Exception:
        pass

class WelcomeWindow(Gtk.Window):
    def __init__(self):
        super().__init__(title="Polyomino Welcome Center")
        self.set_role("polyomino-welcome")
        self.set_default_size(860, 620)
        self.set_position(Gtk.WindowPosition.CENTER)

        self.settings = load_settings()

        # Enable an RGBA visual so the compositor's per-window blur
        # (see `blur enable` on app_id="polyomino-welcome" in sway config)
        # has real alpha to blur through, not an opaque surface.
        self.set_app_paintable(True)
        screen = Gdk.Screen.get_default()
        visual = screen.get_rgba_visual() if screen else None
        if visual is not None:
            self.set_visual(visual)

        # Load CSS Theme
        self.apply_css()

        # Main Layout Box
        main_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        main_box.get_style_context().add_class("main-window-box")
        self.add(main_box)

        # Header Section
        header = self.create_header()
        main_box.pack_start(header, False, False, 0)

        # Content Notebook (Tabs)
        notebook = Gtk.Notebook()
        notebook.set_tab_pos(Gtk.PositionType.TOP)
        notebook.get_style_context().add_class("content-notebook")
        main_box.pack_start(notebook, True, True, 0)
        self.notebook = notebook

        self.gc_all_flowboxes = []

        # Tab 1: Quick Start (Polyomino Grid)
        tab1 = self.create_quick_start_tab()
        notebook.append_page(tab1, self.create_tab_label("🚀", "Quick Start"))

        # Tab 2: Gaming Center (categorized bento layout)
        tab2 = self.create_gaming_tab()
        notebook.append_page(tab2, self.create_tab_label("🎮", "Gaming Center"))

        # Tab 3: Media & Quick System Utilities
        tab3 = self.create_media_tab()
        notebook.append_page(tab3, self.create_tab_label("🎧", "Media & Utilities"))

        # Tab 4: System & Tools (Polyomino Grid)
        tab4 = self.create_system_tab()
        notebook.append_page(tab4, self.create_tab_label("🛠️", "System & Tools"))

        # Global vim-modal search bar (filters bento cards on the active tab)
        self.vim_search_entry = Gtk.SearchEntry()
        self.vim_search_entry.set_placeholder_text("Search gamepads, tools, emulator configs, utilities…")
        self.vim_search_entry.get_style_context().add_class("gc-search")
        self.vim_search_entry.connect("search-changed", self.on_gc_search_changed)
        self.vim_search_revealer = Gtk.Revealer()
        self.vim_search_revealer.set_transition_type(Gtk.RevealerTransitionType.SLIDE_DOWN)
        self.vim_search_revealer.add(self.vim_search_entry)
        self.vim_search_revealer.set_reveal_child(False)
        main_box.pack_start(self.vim_search_revealer, False, False, 0)

        # Global vim-modal hint statusline
        self.vim_hint_label = Gtk.Label(xalign=0)
        self.vim_hint_label.get_style_context().add_class("gc-hint")
        self.vim_mode = "NORMAL"
        self.set_vim_hint_normal()
        hint_box = Gtk.Box()
        hint_box.get_style_context().add_class("gc-hint-box")
        hint_box.pack_start(self.vim_hint_label, True, True, 0)
        main_box.pack_start(hint_box, False, False, 0)

        # Footer Section
        footer = self.create_footer()
        main_box.pack_start(footer, False, False, 0)

        self.connect("key-press-event", self.on_window_keypress)

    def apply_css(self):
        css_provider = Gtk.CssProvider()
        p = DEFAULT_PALETTE

        custom_css = f"""
        * {{
            font-family: 'JetBrainsMono Nerd Font', 'JetBrains Mono', 'FiraCode Nerd Font', monospace;
            outline: none;
            box-shadow: none;
            -gtk-outline-radius: 0;
        }}
        *:focus {{
            outline: none;
            box-shadow: none;
        }}
        window,
        window.background,
        .background {{
            background-color: transparent;
            color: {p['text_primary']};
            border: none;
            box-shadow: none;
        }}

        /* Single frosted-glass tint layer — the compositor blurs the
           desktop behind it through this alpha; everything nested inside
           stays transparent so the tint isn't doubled up. */
        .main-window-box {{
            background-color: alpha({p['bg_base']}, 0.85);
            color: {p['text_primary']};
            border: none;
            box-shadow: none;
        }}

        notebook,
        notebook > stack,
        notebook > stack > *,
        scrolledwindow,
        scrolledwindow viewport,
        viewport {{
            background-color: transparent;
            color: {p['text_primary']};
            border: none;
            box-shadow: none;
        }}

        /* Header Bar */
        .header-box {{
            background-color: {p['bg_mantle']};
            border: none;
            padding: 16px 24px 0 24px;
        }}
        .header-icon {{
            color: {p['accent_violet']};
            font-size: 26px;
            font-weight: bold;
        }}
        .header-title {{
            font-size: 16px;
            font-weight: 700;
            color: {p['text_primary']};
        }}
        .header-subtitle {{
            font-size: 11px;
            color: {p['text_secondary']};
            font-weight: normal;
        }}
        .version-badge {{
            background-color: {p['bg_surface']};
            color: {p['accent_violet_glow']};
            border: 1px solid {p['border_subtle']};
            border-radius: 6px;
            padding: 4px 10px;
            font-size: 11px;
            font-weight: 600;
        }}

        /* Notebook & Tabs — clean underline style, no boxed borders */
        notebook, .content-notebook {{
            background-color: transparent;
            border: none;
            box-shadow: none;
        }}
        notebook > header {{
            background-color: {p['bg_mantle']};
            border: none;
            box-shadow: none;
            padding: 0 24px;
        }}
        notebook > header > tabs {{
            background-color: transparent;
            border: none;
            box-shadow: none;
        }}
        notebook tab {{
            background-color: transparent;
            background-image: none;
            color: {p['text_secondary']};
            opacity: 0.5;
            padding: 12px 4px;
            margin: 0 16px 0 0;
            border: none;
            border-bottom: 2px solid transparent;
            box-shadow: none;
            font-weight: 600;
            font-size: 13px;
            transition: opacity 120ms ease-in-out, color 120ms ease-in-out, border-color 120ms ease-in-out;
        }}
        notebook tab:hover {{
            background-color: transparent;
            color: {p['text_primary']};
            opacity: 0.75;
        }}
        notebook tab:checked {{
            background-color: transparent;
            color: #ffffff;
            opacity: 1;
            border: none;
            border-bottom: 2px solid {p['accent_violet']};
            box-shadow: none;
        }}
        notebook tab:focus {{
            outline: none;
            box-shadow: none;
        }}
        notebook tab label {{
            color: inherit;
            font-weight: 600;
        }}
        .tab-label-box {{
            padding: 0;
        }}

        /* Polyomino Grid & Interactive Tiles */
        .polyomino-grid {{
            background-color: transparent;
            padding: 8px;
        }}

        button.polyomino-tile {{
            background-color: {p['bg_surface']};
            background-image: none;
            border-radius: 8px;
            padding: 16px;
            box-shadow: none;
            text-shadow: none;
            transition: all 140ms ease-in-out;
        }}
        button.polyomino-tile:hover {{
            background-color: {p['bg_surface_hover']};
        }}
        button.polyomino-tile:active {{
            background-color: {p['bg_surface_active']};
        }}

        /* Distinct crisp borders per tile */
        button.tile-violet {{
            border: 1px solid {p['accent_violet']};
        }}
        button.tile-violet:hover {{
            border-color: {p['accent_violet_glow']};
            box-shadow: 0 0 14px rgba(139, 92, 246, 0.25);
        }}

        button.tile-amber {{
            border: 1px solid {p['accent_amber']};
        }}
        button.tile-amber:hover {{
            border-color: {p['accent_amber_glow']};
            box-shadow: 0 0 14px rgba(245, 158, 11, 0.25);
        }}

        button.tile-blue {{
            border: 1px solid {p['accent_blue']};
        }}
        button.tile-blue:hover {{
            border-color: {p['accent_blue_glow']};
            box-shadow: 0 0 14px rgba(59, 130, 246, 0.25);
        }}

        button.tile-teal {{
            border: 1px solid {p['accent_teal']};
        }}
        button.tile-teal:hover {{
            border-color: {p['accent_teal_glow']};
            box-shadow: 0 0 14px rgba(6, 182, 212, 0.25);
        }}

        /* Tile Typography & Components */
        .card-icon-large {{
            font-size: 28px;
            padding: 2px;
        }}
        .card-icon-medium {{
            font-size: 24px;
            padding: 2px;
        }}
        .card-icon-small {{
            font-size: 20px;
            padding: 2px;
        }}

        .tile-badge {{
            font-size: 10px;
            font-weight: 700;
            padding: 3px 8px;
            border-radius: 4px;
            background-color: rgba(139, 92, 246, 0.18);
            color: {p['accent_violet_glow']};
        }}
        .tile-badge-amber {{
            background-color: rgba(245, 158, 11, 0.18);
            color: {p['accent_amber_glow']};
        }}
        .tile-badge-blue {{
            background-color: rgba(59, 130, 246, 0.18);
            color: {p['accent_blue_glow']};
        }}
        .tile-badge-teal {{
            background-color: rgba(6, 182, 212, 0.18);
            color: {p['accent_teal_glow']};
        }}

        .tile-title-primary {{
            font-size: 16px;
            font-weight: 700;
            color: {p['text_primary']};
        }}
        .tile-title {{
            font-size: 14px;
            font-weight: 700;
            color: {p['text_primary']};
        }}
        .tile-desc {{
            font-size: 11px;
            color: {p['text_secondary']};
        }}

        .tile-action-hint-violet {{
            font-size: 11px;
            font-weight: 600;
            color: {p['accent_violet_glow']};
        }}
        .tile-action-hint-amber {{
            font-size: 11px;
            font-weight: 600;
            color: {p['accent_amber_glow']};
        }}
        .tile-action-hint-blue {{
            font-size: 11px;
            font-weight: 600;
            color: {p['accent_blue_glow']};
        }}
        .tile-action-hint-teal {{
            font-size: 11px;
            font-weight: 600;
            color: {p['accent_teal_glow']};
        }}

        /* Footer Section */
        .footer-box {{
            background-color: {p['bg_mantle']};
            border-top: 1px solid {p['border_subtle']};
            padding: 14px 24px;
        }}
        checkbutton {{
            color: {p['text_primary']};
            font-size: 12px;
            font-weight: 500;
        }}
        checkbutton check {{
            background-color: {p['bg_surface']};
            border: 1px solid {p['border_subtle']};
            border-radius: 4px;
            min-height: 16px;
            min-width: 16px;
            margin-right: 8px;
        }}
        checkbutton check:checked {{
            background-color: {p['accent_violet']};
            border-color: {p['accent_violet']};
            color: #ffffff;
        }}
        checkbutton check:hover {{
            border-color: {p['accent_violet']};
        }}

        .btn-action {{
            background-color: {p['bg_surface']};
            background-image: none;
            color: {p['text_primary']};
            border: 1px solid {p['border_subtle']};
            border-radius: 6px;
            padding: 8px 18px;
            font-weight: 600;
            font-size: 12px;
            transition: all 120ms ease-in-out;
        }}
        .btn-action:hover {{
            background-color: {p['bg_surface_hover']};
            border-color: {p['accent_blue']};
            color: {p['accent_blue_glow']};
        }}
        .btn-action:active {{
            background-color: {p['bg_surface_active']};
        }}

        .btn-accent {{
            background-color: {p['accent_violet']};
            background-image: none;
            color: #ffffff;
            border: 1px solid {p['accent_violet']};
            border-radius: 6px;
            padding: 8px 20px;
            font-weight: 600;
            font-size: 12px;
            transition: all 120ms ease-in-out;
        }}
        .btn-accent:hover {{
            background-color: #7c3aed;
            border-color: #7c3aed;
            color: #ffffff;
        }}
        .btn-accent:active {{
            background-color: #6d28d9;
        }}

        scrollbar {{
            background-color: transparent;
        }}
        scrollbar slider {{
            background-color: {p['border_subtle']};
            border-radius: 4px;
            min-width: 6px;
            min-height: 6px;
        }}
        scrollbar slider:hover {{
            background-color: {p['accent_violet']};
        }}

        /* Gaming Center — bento layout, sharp-cornered frosted glass */
        .gc-nav {{
            background-color: {p['bg_mantle']};
            border-right: 1px solid {p['border_subtle']};
            padding: 12px 6px;
        }}
        button.gc-nav-item {{
            background-color: transparent;
            background-image: none;
            border: 1px solid transparent;
            border-radius: 2px;
            padding: 10px;
            box-shadow: none;
            transition: all 120ms ease-in-out;
        }}
        button.gc-nav-item:hover {{
            background-color: {p['bg_surface_hover']};
            border-color: {p['border_subtle']};
        }}
        .gc-nav-active {{
            background-color: {p['bg_surface']};
            border-color: {p['accent_violet']};
        }}
        .gc-nav-number {{
            font-size: 10px;
            font-weight: 700;
            color: {p['text_secondary']};
            background-color: {p['bg_surface']};
            border-radius: 2px;
            padding: 1px 5px;
            min-width: 14px;
        }}
        .gc-nav-label {{
            font-size: 12px;
            font-weight: 600;
            color: {p['text_primary']};
        }}
        .gc-stack, .gc-flow {{
            background-color: transparent;
        }}
        button.gc-card {{
            background-color: rgba(15, 17, 26, 0.85);
            background-image: none;
            border: 1px solid {p['border_subtle']};
            border-radius: 2px;
            padding: 14px;
            box-shadow: none;
            transition: all 120ms ease-in-out;
        }}
        button.gc-card:hover {{
            border-color: {p['accent_blue']};
            background-color: rgba(21, 24, 36, 0.92);
        }}
        .gc-card-icon {{
            font-size: 20px;
        }}
        .gc-card-title {{
            font-size: 13px;
            font-weight: 700;
            color: {p['text_primary']};
        }}
        .gc-card-sub {{
            font-size: 11px;
            color: {p['text_secondary']};
        }}
        .gc-card-action {{
            font-size: 11px;
            font-weight: 600;
            color: {p['accent_blue_glow']};
        }}
        .gc-pill {{
            font-size: 9px;
            font-weight: 700;
            padding: 2px 7px;
            border-radius: 2px;
            background-color: rgba(59, 130, 246, 0.18);
            color: {p['accent_blue_glow']};
        }}
        .gc-pill-connected {{
            background-color: rgba(34, 197, 94, 0.18);
            color: #4ade80;
        }}
        .gc-pill-tool {{
            background-color: rgba(59, 130, 246, 0.18);
            color: {p['accent_blue_glow']};
        }}
        .gc-section-title {{
            font-size: 11px;
            font-weight: 700;
            letter-spacing: 1px;
            color: {p['text_secondary']};
            padding: 4px 4px 0 4px;
        }}
        entry.gc-search {{
            background-color: {p['bg_surface']};
            color: {p['text_primary']};
            border: 1px solid {p['accent_blue']};
            border-radius: 2px;
            padding: 8px 12px;
            margin: 0 16px;
        }}
        .gc-hint-box {{
            background-color: {p['bg_mantle']};
            border-top: 1px solid {p['border_subtle']};
            padding: 6px 16px;
        }}
        .gc-hint {{
            font-size: 10px;
            color: {p['text_secondary']};
        }}
        """

        try:
            css_provider.load_from_data(custom_css.encode('utf-8'))
            Gtk.StyleContext.add_provider_for_screen(
                Gdk.Screen.get_default(),
                css_provider,
                Gtk.STYLE_PROVIDER_PRIORITY_USER
            )
        except Exception as e:
            print(f"Warning: Failed to load custom CSS: {e}", file=sys.stderr)

    def create_tab_label(self, icon, text):
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        box.get_style_context().add_class("tab-label-box")
        lbl = Gtk.Label(label=f"{icon}  {text}")
        box.pack_start(lbl, True, True, 0)
        box.show_all()
        return box

    def create_header(self):
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=16)
        box.get_style_context().add_class("header-box")

        icon_label = Gtk.Label(label="⊞")
        icon_label.get_style_context().add_class("header-icon")
        box.pack_start(icon_label, False, False, 0)

        text_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
        title = Gtk.Label(label="POLYOMINO WELCOME CENTER", xalign=0)
        title.get_style_context().add_class("header-title")

        subtitle = Gtk.Label(label="Arch Linux · SwayFX CAD Desktop & Gaming Workstation", xalign=0)
        subtitle.get_style_context().add_class("header-subtitle")

        text_box.pack_start(title, False, False, 0)
        text_box.pack_start(subtitle, False, False, 0)
        box.pack_start(text_box, True, True, 0)

        # Version Badge (no brackets)
        badge = Gtk.Label(label="v0.1.0")
        badge.get_style_context().add_class("version-badge")
        box.pack_end(badge, False, False, 0)

        return box

    def create_primary_tile(self, icon, title, desc, tag, action_hint, accent_color, on_click_fn):
        """Creates a primary 2x2 polyomino tile."""
        button = Gtk.Button()
        button.set_relief(Gtk.ReliefStyle.NONE)
        button.get_style_context().add_class("polyomino-tile")
        button.get_style_context().add_class(f"tile-{accent_color}")
        button.connect("clicked", lambda b: on_click_fn())
        button.set_hexpand(True)
        button.set_vexpand(True)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        box.set_hexpand(True)
        box.set_vexpand(True)

        # Top Bar: Icon + Badge
        top_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        icon_lbl = Gtk.Label(label=icon)
        icon_lbl.get_style_context().add_class("card-icon-large")
        top_box.pack_start(icon_lbl, False, False, 0)

        if tag:
            badge = Gtk.Label(label=tag)
            badge.get_style_context().add_class("tile-badge")
            if accent_color != "violet":
                badge.get_style_context().add_class(f"tile-badge-{accent_color}")
            top_box.pack_end(badge, False, False, 0)

        box.pack_start(top_box, False, False, 0)

        # Title
        title_lbl = Gtk.Label(label=title, xalign=0)
        title_lbl.get_style_context().add_class("tile-title-primary")
        box.pack_start(title_lbl, False, False, 0)

        # Description
        desc_lbl = Gtk.Label(label=desc, xalign=0)
        desc_lbl.set_line_wrap(True)
        desc_lbl.get_style_context().add_class("tile-desc")
        box.pack_start(desc_lbl, True, True, 0)

        # Action hint at bottom
        hint_lbl = Gtk.Label(label=f"{action_hint}  →", xalign=0)
        hint_lbl.get_style_context().add_class(f"tile-action-hint-{accent_color}")
        box.pack_end(hint_lbl, False, False, 0)

        button.add(box)
        return button

    def create_horizontal_tile(self, icon, title, desc, tag, action_hint, accent_color, on_click_fn):
        """Creates a horizontal 2x1 polyomino tile."""
        button = Gtk.Button()
        button.set_relief(Gtk.ReliefStyle.NONE)
        button.get_style_context().add_class("polyomino-tile")
        button.get_style_context().add_class(f"tile-{accent_color}")
        button.connect("clicked", lambda b: on_click_fn())
        button.set_hexpand(True)
        button.set_vexpand(True)

        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=14)
        box.set_hexpand(True)
        box.set_vexpand(True)

        icon_lbl = Gtk.Label(label=icon)
        icon_lbl.get_style_context().add_class("card-icon-medium")
        box.pack_start(icon_lbl, False, False, 0)

        content_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        content_box.set_hexpand(True)

        top_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        title_lbl = Gtk.Label(label=title, xalign=0)
        title_lbl.get_style_context().add_class("tile-title")
        top_row.pack_start(title_lbl, False, False, 0)

        if tag:
            badge = Gtk.Label(label=tag)
            badge.get_style_context().add_class("tile-badge")
            if accent_color != "violet":
                badge.get_style_context().add_class(f"tile-badge-{accent_color}")
            top_row.pack_end(badge, False, False, 0)

        content_box.pack_start(top_row, False, False, 0)

        desc_lbl = Gtk.Label(label=desc, xalign=0)
        desc_lbl.set_line_wrap(True)
        desc_lbl.get_style_context().add_class("tile-desc")
        content_box.pack_start(desc_lbl, True, True, 0)

        hint_lbl = Gtk.Label(label=f"{action_hint}  →", xalign=0)
        hint_lbl.get_style_context().add_class(f"tile-action-hint-{accent_color}")
        content_box.pack_end(hint_lbl, False, False, 0)

        box.pack_start(content_box, True, True, 0)
        button.add(box)
        return button

    def create_square_tile(self, icon, title, desc, action_hint, accent_color, on_click_fn):
        """Creates a compact 1x1 polyomino tile."""
        button = Gtk.Button()
        button.set_relief(Gtk.ReliefStyle.NONE)
        button.get_style_context().add_class("polyomino-tile")
        button.get_style_context().add_class(f"tile-{accent_color}")
        button.connect("clicked", lambda b: on_click_fn())
        button.set_hexpand(True)
        button.set_vexpand(True)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        box.set_hexpand(True)
        box.set_vexpand(True)

        top_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        icon_lbl = Gtk.Label(label=icon)
        icon_lbl.get_style_context().add_class("card-icon-small")
        top_row.pack_start(icon_lbl, False, False, 0)

        title_lbl = Gtk.Label(label=title, xalign=0)
        title_lbl.get_style_context().add_class("tile-title")
        top_row.pack_start(title_lbl, True, True, 0)

        box.pack_start(top_row, False, False, 0)

        desc_lbl = Gtk.Label(label=desc, xalign=0)
        desc_lbl.set_line_wrap(True)
        desc_lbl.get_style_context().add_class("tile-desc")
        box.pack_start(desc_lbl, True, True, 0)

        hint_lbl = Gtk.Label(label=f"{action_hint}  →", xalign=0)
        hint_lbl.get_style_context().add_class(f"tile-action-hint-{accent_color}")
        box.pack_end(hint_lbl, False, False, 0)

        button.add(box)
        return button

    def build_grid_container(self, primary_tile, horiz_tile, sq1_tile, sq2_tile):
        """Arranges 4 polyomino blocks into an interlocking 4x2 grid."""
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_shadow_type(Gtk.ShadowType.NONE)

        grid = Gtk.Grid()
        grid.get_style_context().add_class("polyomino-grid")
        grid.set_column_spacing(12)
        grid.set_row_spacing(12)
        grid.set_column_homogeneous(True)
        grid.set_row_homogeneous(True)
        grid.set_margin_top(16)
        grid.set_margin_bottom(16)
        grid.set_margin_start(20)
        grid.set_margin_end(20)

        # Interlocking Polyomino Layout:
        # 1. Primary Block: col 0, row 0, span 2 cols, 2 rows (Tetromino)
        grid.attach(primary_tile, 0, 0, 2, 2)
        # 2. Horizontal Block: col 2, row 0, span 2 cols, 1 row (Domino)
        grid.attach(horiz_tile, 2, 0, 2, 1)
        # 3. Compact Square 1: col 2, row 1, span 1 col, 1 row (Monomino)
        grid.attach(sq1_tile, 2, 1, 1, 1)
        # 4. Compact Square 2: col 3, row 1, span 1 col, 1 row (Monomino)
        grid.attach(sq2_tile, 3, 1, 1, 1)

        scrolled.add(grid)
        return scrolled

    def create_quick_start_tab(self):
        primary = self.create_primary_tile(
            icon="🎨",
            title="Theme & Palette",
            desc="Switch between Matriz, Encruza, Caravela, and Aruanda flavor schemes with live synchronization across Sway, Waybar, Kitty, and Rofi.",
            tag="PRIMARY",
            action_hint="Select Theme",
            accent_color="violet",
            on_click_fn=lambda: run_cmd(["polyomino-theme-picker"])
        )

        horiz = self.create_horizontal_tile(
            icon="🖼️",
            title="Wallpapers",
            desc="Browse and apply curated high-resolution wallpapers matching your active color scheme.",
            tag="CURATED",
            action_hint="Pick Wallpaper",
            accent_color="amber",
            on_click_fn=lambda: run_cmd(["polyomino-wallpaper-picker"])
        )

        sq1 = self.create_square_tile(
            icon="⌨️",
            title="Keybindings",
            desc="SwayFX and desktop shortcut cheatsheet.",
            action_hint="Shortcuts",
            accent_color="blue",
            on_click_fn=lambda: run_cmd(["polyomino-whichkey"])
        )

        sq2 = self.create_square_tile(
            icon="🖥️",
            title="Display Layout",
            desc="Monitors, scaling, and refresh rates.",
            action_hint="Configure",
            accent_color="teal",
            on_click_fn=lambda: run_cmd(["wdisplays"])
        )

        return self.build_grid_container(primary, horiz, sq1, sq2)

    # ---------------------------------------------------------------
    # Gaming Center: bento card primitive
    # ---------------------------------------------------------------

    def create_gc_card(self, icon, title, subtext, action_label, on_click_fn, badge_text=None, badge_style="tool"):
        button = Gtk.Button()
        button.set_relief(Gtk.ReliefStyle.NONE)
        button.get_style_context().add_class("gc-card")
        button.connect("clicked", lambda b: on_click_fn())
        button.set_hexpand(True)
        button.gc_title = f"{title} {subtext}".lower()

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)

        top = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        icon_lbl = Gtk.Label(label=icon)
        icon_lbl.get_style_context().add_class("gc-card-icon")
        top.pack_start(icon_lbl, False, False, 0)

        title_lbl = Gtk.Label(label=title, xalign=0)
        title_lbl.get_style_context().add_class("gc-card-title")
        top.pack_start(title_lbl, True, True, 0)

        if badge_text:
            pill = Gtk.Label(label=badge_text)
            pill.get_style_context().add_class("gc-pill")
            pill.get_style_context().add_class(f"gc-pill-{badge_style}")
            top.pack_end(pill, False, False, 0)

        box.pack_start(top, False, False, 0)

        sub_lbl = Gtk.Label(label=subtext, xalign=0)
        sub_lbl.set_line_wrap(True)
        sub_lbl.get_style_context().add_class("gc-card-sub")
        box.pack_start(sub_lbl, True, True, 0)

        action_lbl = Gtk.Label(label=f"{action_label}", xalign=0)
        action_lbl.get_style_context().add_class("gc-card-action")
        box.pack_end(action_lbl, False, False, 0)

        button.add(box)
        return button

    def create_gc_nav_item(self, number, icon, label, page_name):
        btn = Gtk.Button()
        btn.set_relief(Gtk.ReliefStyle.NONE)
        btn.get_style_context().add_class("gc-nav-item")
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        num_lbl = Gtk.Label(label=str(number))
        num_lbl.get_style_context().add_class("gc-nav-number")
        box.pack_start(num_lbl, False, False, 0)
        icon_lbl = Gtk.Label(label=icon)
        box.pack_start(icon_lbl, False, False, 0)
        text_lbl = Gtk.Label(label=label, xalign=0)
        text_lbl.get_style_context().add_class("gc-nav-label")
        box.pack_start(text_lbl, True, True, 0)
        btn.add(box)
        btn.connect("clicked", lambda b: self.gc_stack.set_visible_child_name(page_name))
        return btn

    def build_gc_flow_page(self, page_name, cards):
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        flow = Gtk.FlowBox()
        flow.set_valign(Gtk.Align.START)
        flow.set_max_children_per_line(3)
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_row_spacing(12)
        flow.set_column_spacing(12)
        flow.set_homogeneous(True)
        flow.get_style_context().add_class("gc-flow")
        flow.set_margin_top(16)
        flow.set_margin_bottom(16)
        flow.set_margin_start(16)
        flow.set_margin_end(16)
        for card in cards:
            flow.add(card)
        flow.set_filter_func(self.gc_filter_func)
        self.gc_all_flowboxes.append(flow)
        scrolled.add(flow)
        return scrolled

    def build_gc_empty_page(self, title, desc):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        box.set_valign(Gtk.Align.CENTER)
        box.set_halign(Gtk.Align.CENTER)
        box.set_vexpand(True)
        title_lbl = Gtk.Label(label=title)
        title_lbl.get_style_context().add_class("tile-title")
        desc_lbl = Gtk.Label(label=desc)
        desc_lbl.get_style_context().add_class("tile-desc")
        box.pack_start(title_lbl, False, False, 0)
        box.pack_start(desc_lbl, False, False, 0)
        return box

    def gc_filter_func(self, child):
        if not hasattr(self, "vim_search_entry"):
            return True
        query = self.vim_search_entry.get_text().strip().lower()
        if not query:
            return True
        return query in getattr(child.get_child(), "gc_title", "")

    def on_gc_search_changed(self, entry):
        for fb in self.gc_all_flowboxes:
            fb.invalidate_filter()

    # ---------------------------------------------------------------
    # Gaming Center: category pages
    # ---------------------------------------------------------------

    def build_gc_tools_page(self):
        editor = resolve_editor()
        cards = [
            self.create_gc_card("🚀", "Game Mode Toggle",
                "Boost CPU governor, engage VRR, disable compositor blur, inhibit sleep during play.",
                "Toggle →", lambda: run_cmd(["polyomino", "gamemode", "toggle"]),
                badge_text="TOOL", badge_style="tool"),
            self.create_gc_card("📊", "MangoHud Overlay",
                "FPS / frametime / GPU overlay. Edit the active profile config.",
                "Configure →", lambda: run_cmd([editor, str(Path.home() / ".config" / "MangoHud" / "MangoHud.conf")], in_terminal=True),
                badge_text="TOOL", badge_style="tool"),
            self.create_gc_card("🍷", "Wine / Proton Manager",
                "Manage prefixes and compatibility layers via protontricks/winetricks.",
                "Manage →", lambda: run_cmd(["protontricks", "--gui"]) if shutil.which("protontricks")
                    else (run_cmd(["winetricks"]) if shutil.which("winetricks")
                    else run_cmd(["notify-send", "Wine/Proton", "Install protontricks or winetricks to manage prefixes."])),
                badge_text="TOOL", badge_style="tool"),
            self.create_gc_card("🩺", "GameMode Status",
                "Inspect current performance-mode state and active optimizations.",
                "View Status →", lambda: run_cmd(["polyomino", "gamemode", "status"], in_terminal=True),
                badge_text="STATUS", badge_style="tool"),
        ]
        return self.build_gc_flow_page("gc-tools", cards)

    def scan_installed_games(self):
        games = []
        seen = set()
        steam_roots = [Path.home() / ".steam" / "steam", Path.home() / ".local" / "share" / "Steam"]
        for root in steam_roots:
            apps_dir = root / "steamapps"
            if not apps_dir.exists():
                continue
            for acf in apps_dir.glob("appmanifest_*.acf"):
                content = _read_text(acf)
                m_name = re.search(r'"name"\s+"([^"]+)"', content)
                m_id = re.search(r'"appid"\s+"([^"]+)"', content)
                if m_name and m_id and m_id.group(1) not in seen:
                    seen.add(m_id.group(1))
                    games.append({"name": m_name.group(1), "kind": "Steam",
                                  "launch": ["steam", f"steam://rungameid/{m_id.group(1)}"]})

        games_file = CONFIG_DIR / "games.json"
        if games_file.exists():
            try:
                data = json.loads(_read_text(games_file) or "{}")
                for g in data.get("games", []):
                    games.append({"name": g.get("name", "Unknown"), "kind": g.get("kind", "Native"),
                                  "launch": g.get("launch", [])})
            except Exception:
                pass
        return games

    def build_gc_games_page(self):
        games = self.scan_installed_games()
        if not games:
            return self.build_gc_empty_page(
                "No games detected",
                "Steam titles auto-populate here. Add native/local binaries via ~/.config/polyomino/games.json")
        cards = []
        for g in games:
            launch = g["launch"]
            cards.append(self.create_gc_card(
                "🎮", g["name"], f"Source: {g['kind']}", "Launch →",
                (lambda cmd=launch: run_cmd(cmd)) if launch else (lambda: None),
                badge_text=g["kind"].upper(), badge_style="connected"))
        return self.build_gc_flow_page("gc-games", cards)

    def build_gc_emulators_page(self):
        cards = []
        for emu_id, label in EMULATOR_LABELS.items():
            binary = find_emulator_binary(emu_id)
            installed = binary is not None
            cards.append(self.create_gc_card(
                "🕹️", label,
                f"Backend: {binary}" if installed else "Not installed — see Installation / Setup.",
                "Launch →" if installed else "Install →",
                (lambda b=binary: run_cmd([b])) if installed
                    else (lambda eid=emu_id: run_cmd(["polyomino", "install-emulator", eid], in_terminal=True)),
                badge_text="INSTALLED" if installed else "MISSING",
                badge_style="connected" if installed else "tool"))
        return self.build_gc_flow_page("gc-emulators", cards)

    def _pick_file(self, title, patterns=None):
        dialog = Gtk.FileChooserDialog(
            title=title, parent=self, action=Gtk.FileChooserAction.OPEN)
        dialog.add_buttons(Gtk.STOCK_CANCEL, Gtk.ResponseType.CANCEL,
                            Gtk.STOCK_OPEN, Gtk.ResponseType.OK)
        if patterns:
            for pat_name, pat in patterns:
                f = Gtk.FileFilter()
                f.set_name(pat_name)
                f.add_pattern(pat)
                dialog.add_filter(f)
        path = None
        if dialog.run() == Gtk.ResponseType.OK:
            path = dialog.get_filename()
        dialog.destroy()
        return path

    def on_apply_patch_clicked(self):
        base_rom = self._pick_file("Select base ROM")
        if not base_rom:
            return
        patch_file = self._pick_file("Select IPS/BPS patch",
                                      [("IPS/BPS patches", "*.ips"), ("IPS/BPS patches", "*.bps")])
        if not patch_file:
            return
        apply_rom_patch(base_rom, patch_file)

    def on_quick_update_gym_clicked(self):
        base_rom = self._pick_file("Select base ROM to patch")
        if not base_rom:
            return
        quick_update_gym(base_rom)

    def build_gc_roms_page(self):
        cards = [
            self.create_gc_card(
                "🩹", "Apply IPS/BPS Patch",
                "Select a base ROM and a patch file; writes a -patched copy via flips.",
                "Apply Patch →", self.on_apply_patch_clicked,
                badge_text="TOOL", badge_style="tool"),
            self.create_gc_card(
                "⚡", "Quick Update Gym",
                "Auto-apply the newest .ips/.bps patch found in ~/Downloads to a base ROM.",
                "Auto-Patch →", self.on_quick_update_gym_clicked,
                badge_text="TOOL", badge_style="tool"),
            self.create_gc_card(
                "💾", "Snapshot Saves",
                "Archive emulator save/SRAM data into a timestamped tarball under ~/Games/Backups/.",
                "Snapshot →", snapshot_saves,
                badge_text="TOOL", badge_style="tool"),
        ]
        roms = list_roms()
        if not roms:
            cards.append(self.create_gc_card(
                "📂", "No ROMs Found",
                f"Drop ROMs into {ROMS_DIR}/<platform>/ (nes, snes, gb, gba, arcade, ...).",
                "—", lambda: None, badge_text="INFO", badge_style="tool"))
        else:
            for rom in roms:
                cards.append(self.create_gc_card(
                    "💽", rom["title"], f"Platform: {rom['platform']}", "Launch →",
                    (lambda p=rom["path"]: launch_rom(p)),
                    badge_text=rom["platform"].upper(), badge_style="connected"))
        return self.build_gc_flow_page("gc-roms", cards)

    def build_gc_install_page(self):
        cards = [self.create_gc_card(
            "📦", "Install All",
            f"Install the full competitive emulator suite ({len(EMULATOR_LABELS)} emulators) in one pass.",
            "Install All →", lambda: run_cmd(["polyomino", "install-emulators"], in_terminal=True),
            badge_text="BATCH", badge_style="tool")]
        for emu_id, label in EMULATOR_LABELS.items():
            installed = find_emulator_binary(emu_id) is not None
            cards.append(self.create_gc_card(
                "🧩", label, "Already installed." if installed else "Install via pacman/AUR (yay).",
                "Reinstall →" if installed else "Install →",
                lambda eid=emu_id: run_cmd(["polyomino", "install-emulator", eid], in_terminal=True),
                badge_text="READY" if installed else "TOOL",
                badge_style="connected" if installed else "tool"))
        return self.build_gc_flow_page("gc-install", cards)

    def launch_gamepad_tester(self, device_path=None):
        if shutil.which("jstest-gtk"):
            cmd = ["jstest-gtk"] + ([device_path] if device_path else [])
            run_cmd(cmd)
        elif shutil.which("evtest"):
            run_cmd(["evtest"], in_terminal=True)
        else:
            run_cmd(["notify-send", "Gamepad Tester", "Install jstest-gtk or evtest to use this tool."])

    def launch_latency_test(self):
        if shutil.which("evhz"):
            run_cmd(["evhz"], in_terminal=True)
        else:
            run_cmd(["kitty", "-e", "bash", "-c",
                     "echo 'evhz not found (AUR: evhz) — falling back to evtest for a manual poll-rate check.'; "
                     "command -v evtest >/dev/null && evtest || echo 'evtest also not found.'; "
                     "read -n 1 -s -r -p 'Press any key to close...'"])

    def launch_sdl_mapper(self):
        if shutil.which("antimicrox"):
            run_cmd(["antimicrox"])
        elif shutil.which("sdl2-jstest"):
            run_cmd(["sdl2-jstest", "--list"], in_terminal=True)
        else:
            run_cmd(["notify-send", "SDL Mapping",
                      "Install antimicrox (or sdl2-jstest) to generate/edit gamecontrollerdb mappings."])

    def on_toggle_steam_input(self):
        c = load_controller_settings()
        new_val = not c.get("steam_input_enabled", True)
        save_controller_setting("steam_input_enabled", new_val)
        run_cmd(["notify-send", "Steam Input",
                  f"Global controller mapping {'enabled' if new_val else 'disabled'}. Restart Steam to apply."])
        self.refresh_gc_controller_page()

    def refresh_gc_controller_page(self):
        old = self.gc_stack.get_child_by_name("gc-controller")
        if old is not None:
            self.gc_stack.remove(old)
        new_page = self.build_gc_controller_page()
        self.gc_stack.add_named(new_page, "gc-controller")
        new_page.show_all()
        self.gc_stack.set_visible_child_name("gc-controller")

    def build_gc_controller_page(self):
        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=18)
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=18)
        content.set_margin_top(16)
        content.set_margin_bottom(16)
        content.set_margin_start(16)
        content.set_margin_end(16)

        section1 = Gtk.Label(label="QUICK HARDWARE ACTIONS", xalign=0)
        section1.get_style_context().add_class("gc-section-title")
        content.pack_start(section1, False, False, 0)

        quick_flow = Gtk.FlowBox()
        quick_flow.set_valign(Gtk.Align.START)
        quick_flow.set_max_children_per_line(3)
        quick_flow.set_selection_mode(Gtk.SelectionMode.NONE)
        quick_flow.set_row_spacing(12)
        quick_flow.set_column_spacing(12)
        quick_flow.set_homogeneous(True)
        quick_flow.get_style_context().add_class("gc-flow")
        quick_flow.set_filter_func(self.gc_filter_func)

        steam_input_enabled = load_controller_settings().get("steam_input_enabled", True)
        quick_cards = [
            self.create_gc_card("󰂯", "Bluetooth Pairing", "Pair a wireless gamepad via bluetui.", "Pair New →",
                lambda: run_cmd(["kitty", "--title", "bluetui", "-e", "bluetui"]),
                badge_text="TOOL", badge_style="tool"),
            self.create_gc_card("󰊴", "Gamepad Tester & Calibrator", "Interactive axis/button test.", "Calibrate →",
                self.launch_gamepad_tester, badge_text="TOOL", badge_style="tool"),
            self.create_gc_card("󰍹", "Input Latency Diagnostic", "USB/Bluetooth polling-rate benchmark.", "Test Latency →",
                self.launch_latency_test, badge_text="TOOL", badge_style="tool"),
            self.create_gc_card("🎮", "Steam Input Toggle",
                f"Global mapping is currently {'ENABLED' if steam_input_enabled else 'DISABLED'}.", "Toggle →",
                self.on_toggle_steam_input,
                badge_text="ACTIVE" if steam_input_enabled else "OFF",
                badge_style="connected" if steam_input_enabled else "tool"),
            self.create_gc_card("🗺️", "SDL Mapping Utility", "Generate / edit SDL2 gamecontrollerdb mappings.", "Open Mapper →",
                self.launch_sdl_mapper, badge_text="TOOL", badge_style="tool"),
        ]
        for c in quick_cards:
            quick_flow.add(c)
        content.pack_start(quick_flow, False, False, 0)
        self.gc_all_flowboxes.append(quick_flow)

        section2 = Gtk.Label(label="CONNECTED DEVICES", xalign=0)
        section2.get_style_context().add_class("gc-section-title")
        content.pack_start(section2, False, False, 0)

        device_flow = Gtk.FlowBox()
        device_flow.set_valign(Gtk.Align.START)
        device_flow.set_max_children_per_line(3)
        device_flow.set_selection_mode(Gtk.SelectionMode.NONE)
        device_flow.set_row_spacing(12)
        device_flow.set_column_spacing(12)
        device_flow.set_homogeneous(True)
        device_flow.get_style_context().add_class("gc-flow")
        device_flow.set_filter_func(self.gc_filter_func)

        devices = list_gamepads()
        if devices:
            for dev in devices:
                battery_txt = f"Battery: {dev['battery']}%" if dev["battery"] is not None else "Battery: n/a"
                subtext = f"{battery_txt} · Backend: evdev · Connection: {dev['bus']}"
                device_flow.add(self.create_gc_card(
                    "󰊴", dev["name"], subtext, "Calibrate →",
                    (lambda p=dev["path"]: self.launch_gamepad_tester(p)),
                    badge_text="CONNECTED", badge_style="connected"))
        else:
            device_flow.add(self.create_gc_card(
                "󰋼", "No gamepads detected",
                "Connect a controller via USB or pair one via Bluetooth above.", "Rescan →",
                self.refresh_gc_controller_page, badge_text="INFO", badge_style="tool"))

        content.pack_start(device_flow, False, False, 0)
        self.gc_all_flowboxes.append(device_flow)

        scrolled.add(content)
        outer.pack_start(scrolled, True, True, 0)
        return outer

    def build_gc_updates_page(self):
        cards = [
            self.create_gc_card("📱", "Update Flatpaks", "Update all installed Flatpak runtimes & apps.", "Update →",
                lambda: run_cmd(["flatpak", "update", "-y"], in_terminal=True) if shutil.which("flatpak")
                    else run_cmd(["notify-send", "Flatpak", "flatpak is not installed."]),
                badge_text="RUNTIME", badge_style="tool"),
            self.create_gc_card("🍷", "Update Proton-GE", "Fetch the latest Proton-GE compatibility build.", "Update →",
                lambda: run_cmd(["protonup-qt"]) if shutil.which("protonup-qt")
                    else run_cmd(["notify-send", "Proton-GE", "Install protonup-qt to manage Proton-GE builds."]),
                badge_text="COMPAT", badge_style="tool"),
            self.create_gc_card("🧪", "Update Wine Prefixes", "Run winetricks maintenance on the default prefix.", "Update →",
                lambda: run_cmd(["winetricks"]) if shutil.which("winetricks")
                    else run_cmd(["notify-send", "Wine", "Install winetricks to manage prefixes."]),
                badge_text="COMPAT", badge_style="tool"),
            self.create_gc_card("🖥️", "Update Vulkan Runtimes", "Refresh Vulkan drivers & ICD loaders via the gaming installer.", "Update →",
                lambda: run_cmd(["polyomino", "install-gaming"], in_terminal=True),
                badge_text="RUNTIME", badge_style="tool"),
        ]
        return self.build_gc_flow_page("gc-updates", cards)

    # ---------------------------------------------------------------
    # Global vim-modal keyboard navigation (all tabs)
    # ---------------------------------------------------------------

    def set_vim_hint_normal(self):
        self.vim_hint_label.set_markup(
            "<b>[ NORMAL ]</b>  h/j/k/l Navigate  •  / Search  •  Enter Execute  •  Esc Close")

    def set_vim_hint_insert(self):
        self.vim_hint_label.set_markup("<b>[ INSERT ]</b>  Type to filter  •  Esc back to Normal")

    def gc_move_focus(self, direction):
        focused = self.get_focus()
        target = focused if focused is not None else self.notebook.get_nth_page(self.notebook.get_current_page())
        if target is not None:
            target.child_focus(direction)

    def on_window_keypress(self, widget, event):
        keyname = Gdk.keyval_name(event.keyval) or ""

        if self.vim_mode == "INSERT":
            if keyname == "Escape":
                self.vim_mode = "NORMAL"
                self.vim_search_revealer.set_reveal_child(False)
                self.notebook.grab_focus()
                self.set_vim_hint_normal()
                return True
            return False

        if keyname == "slash":
            self.vim_mode = "INSERT"
            self.vim_search_revealer.set_reveal_child(True)
            self.vim_search_entry.grab_focus()
            self.set_vim_hint_insert()
            return True
        elif keyname in ("h", "H"):
            self.gc_move_focus(Gtk.DirectionType.LEFT)
            return True
        elif keyname in ("l", "L"):
            self.gc_move_focus(Gtk.DirectionType.RIGHT)
            return True
        elif keyname in ("j", "J"):
            self.gc_move_focus(Gtk.DirectionType.DOWN)
            return True
        elif keyname in ("k", "K"):
            self.gc_move_focus(Gtk.DirectionType.UP)
            return True
        elif keyname in ("1", "2", "3", "4"):
            idx = int(keyname) - 1
            if idx < self.notebook.get_n_pages():
                self.notebook.set_current_page(idx)
            return True
        elif keyname == "Escape":
            self.close()
            return True
        return False

    def create_gaming_tab(self):
        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        outer.get_style_context().add_class("gc-root")

        body = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=0)
        body.set_hexpand(True)
        body.set_vexpand(True)

        nav_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        nav_box.get_style_context().add_class("gc-nav")
        nav_box.set_size_request(210, -1)

        categories = [
            (1, "🛠️", "Tools", "gc-tools"),
            (2, "🕹️", "Installed Games", "gc-games"),
            (3, "🎮", "Emulators (Launch)", "gc-emulators"),
            (4, "📦", "Installation / Setup", "gc-install"),
            (5, "🎛️", "Controller Configuration", "gc-controller"),
            (6, "⬆️", "Updates", "gc-updates"),
            (7, "💾", "ROM Direct-Boot", "gc-roms"),
        ]
        for number, icon, label, page in categories:
            nav_box.pack_start(self.create_gc_nav_item(number, icon, label, page), False, False, 0)
        body.pack_start(nav_box, False, False, 0)

        self.gc_stack = Gtk.Stack()
        self.gc_stack.set_transition_type(Gtk.StackTransitionType.CROSSFADE)
        self.gc_stack.set_transition_duration(120)
        self.gc_stack.get_style_context().add_class("gc-stack")
        self.gc_stack.set_hexpand(True)
        self.gc_stack.set_vexpand(True)

        self.gc_stack.add_named(self.build_gc_tools_page(), "gc-tools")
        self.gc_stack.add_named(self.build_gc_games_page(), "gc-games")
        self.gc_stack.add_named(self.build_gc_emulators_page(), "gc-emulators")
        self.gc_stack.add_named(self.build_gc_install_page(), "gc-install")
        self.gc_stack.add_named(self.build_gc_controller_page(), "gc-controller")
        self.gc_stack.add_named(self.build_gc_updates_page(), "gc-updates")
        self.gc_stack.add_named(self.build_gc_roms_page(), "gc-roms")
        self.gc_stack.set_visible_child_name("gc-tools")

        body.pack_start(self.gc_stack, True, True, 0)
        outer.pack_start(body, True, True, 0)

        return outer

    # ---------------------------------------------------------------
    # Media & Quick System Utilities
    # ---------------------------------------------------------------

    def build_media_audio_flow(self):
        flow = Gtk.FlowBox()
        flow.set_valign(Gtk.Align.START)
        flow.set_max_children_per_line(3)
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_row_spacing(12)
        flow.set_column_spacing(12)
        flow.set_homogeneous(True)
        flow.get_style_context().add_class("gc-flow")
        flow.set_filter_func(self.gc_filter_func)

        sink = get_default_sink()
        sink_name = sink["name"] if sink else "No sink detected"
        mic_muted = get_default_source_muted()
        mic_status = "Muted" if mic_muted else ("Live" if mic_muted is not None else "Unknown")
        flow.add(self.create_gc_card(
            "🔊", "Audio Output & Sink", f"Active: {sink_name} · Mic: {mic_status}", "Switch Sink →",
            self.on_toggle_sink, badge_text="ACTIVE", badge_style="connected"))
        flow.add(self.create_gc_card(
            "🎚️", "Audio Mixer (TUI)", "Open the full PipeWire mixer for per-app volume control.", "Open Mixer →",
            open_audio_mixer, badge_text="TOOL", badge_style="tool"))
        self.gc_all_flowboxes.append(flow)
        return flow

    def on_toggle_sink(self):
        toggle_default_sink()
        self.refresh_media_section("audio")

    def build_media_capture_flow(self):
        flow = Gtk.FlowBox()
        flow.set_valign(Gtk.Align.START)
        flow.set_max_children_per_line(3)
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_row_spacing(12)
        flow.set_column_spacing(12)
        flow.set_homogeneous(True)
        flow.get_style_context().add_class("gc-flow")
        flow.set_filter_func(self.gc_filter_func)

        recording = is_recording()
        flow.add(self.create_gc_card(
            "📐", "Area Screenshot", "Select a region with slurp and save to ~/Pictures.", "Capture →",
            take_area_screenshot, badge_text="TOOL", badge_style="tool"))
        flow.add(self.create_gc_card(
            "🖥️", "Fullscreen Grab", "Capture the entire active output to ~/Pictures.", "Capture →",
            take_fullscreen_screenshot, badge_text="TOOL", badge_style="tool"))
        flow.add(self.create_gc_card(
            "🎥", "Screen Recording", "Toggle wf-recorder capture to ~/Videos.",
            "Stop Recording →" if recording else "Start Recording →",
            self.on_toggle_recording,
            badge_text="REC" if recording else "IDLE",
            badge_style="connected" if recording else "tool"))
        self.gc_all_flowboxes.append(flow)
        return flow

    def on_toggle_recording(self):
        toggle_recording()
        self.refresh_media_section("capture")

    def build_media_scratchpad_flow(self):
        flow = Gtk.FlowBox()
        flow.set_valign(Gtk.Align.START)
        flow.set_max_children_per_line(3)
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_row_spacing(12)
        flow.set_column_spacing(12)
        flow.set_homogeneous(True)
        flow.get_style_context().add_class("gc-flow")
        flow.set_filter_func(self.gc_filter_func)

        windows = list_scratchpad_windows()
        if windows:
            for win in windows:
                flow.add(self.create_gc_card(
                    "🗂️", win["name"], "Sway scratchpad window.", "Focus / Toggle →",
                    (lambda cid=win["con_id"]: (toggle_scratchpad_window(cid), self.refresh_media_section("scratchpad"))),
                    badge_text="HIDDEN", badge_style="tool"))
        else:
            flow.add(self.create_gc_card(
                "🗂️", "No scratchpad windows", "Move a window to the scratchpad to see it here.", "Rescan →",
                lambda: self.refresh_media_section("scratchpad"), badge_text="INFO", badge_style="tool"))
        self.gc_all_flowboxes.append(flow)
        return flow

    def refresh_media_section(self, which):
        box_attr, builder = {
            "audio": ("media_audio_box", self.build_media_audio_flow),
            "capture": ("media_capture_box", self.build_media_capture_flow),
            "scratchpad": ("media_scratch_box", self.build_media_scratchpad_flow),
        }[which]
        container = getattr(self, box_attr)
        for child in container.get_children():
            if child in self.gc_all_flowboxes:
                self.gc_all_flowboxes.remove(child)
            container.remove(child)
        new_flow = builder()
        container.pack_start(new_flow, True, True, 0)
        new_flow.show_all()

    def create_media_tab(self):
        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)

        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=18)
        content.set_margin_top(16)
        content.set_margin_bottom(16)
        content.set_margin_start(16)
        content.set_margin_end(16)

        s1 = Gtk.Label(label="AUDIO OUTPUT & SINK SWITCHER", xalign=0)
        s1.get_style_context().add_class("gc-section-title")
        content.pack_start(s1, False, False, 0)
        self.media_audio_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.media_audio_box.pack_start(self.build_media_audio_flow(), True, True, 0)
        content.pack_start(self.media_audio_box, False, False, 0)

        s2 = Gtk.Label(label="SCREEN CAPTURE & RECORD", xalign=0)
        s2.get_style_context().add_class("gc-section-title")
        content.pack_start(s2, False, False, 0)
        self.media_capture_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.media_capture_box.pack_start(self.build_media_capture_flow(), True, True, 0)
        content.pack_start(self.media_capture_box, False, False, 0)

        s3 = Gtk.Label(label="WORKSPACE & SCRATCHPAD INSPECTOR", xalign=0)
        s3.get_style_context().add_class("gc-section-title")
        content.pack_start(s3, False, False, 0)
        self.media_scratch_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL)
        self.media_scratch_box.pack_start(self.build_media_scratchpad_flow(), True, True, 0)
        content.pack_start(self.media_scratch_box, False, False, 0)

        scrolled.add(content)
        outer.pack_start(scrolled, True, True, 0)
        return outer

    def create_system_tab(self):
        primary = self.create_primary_tile(
            icon="🩺",
            title="System Healthcheck",
            desc="Execute comprehensive diagnostics across dotfiles symlinks, system packages, GTK themes, Waybar configs, and shell environments.",
            tag="DIAGNOSTICS",
            action_hint="Run Healthcheck",
            accent_color="violet",
            on_click_fn=lambda: run_cmd(["polyomino", "healthcheck"], in_terminal=True)
        )

        horiz = self.create_horizontal_tile(
            icon="🔄",
            title="Update Dotfiles",
            desc="Pull latest enhancements from repository, recompile CLI binaries, and refresh desktop state.",
            tag="MAINTENANCE",
            action_hint="Update Now",
            accent_color="amber",
            on_click_fn=lambda: run_cmd(["polyomino", "update"], in_terminal=True)
        )

        sq1 = self.create_square_tile(
            icon="💾",
            title="Backup",
            desc="Create tarball config snapshot.",
            action_hint="Backup",
            accent_color="blue",
            on_click_fn=lambda: run_cmd(["polyomino", "backup"], in_terminal=True)
        )

        sq2 = self.create_square_tile(
            icon="⚙️",
            title="Edit Configs",
            desc="Open Sway, Kitty, or Zsh configs.",
            action_hint="Menu",
            accent_color="teal",
            on_click_fn=lambda: run_cmd(["polyomino", "menu"])
        )

        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=18)

        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_shadow_type(Gtk.ShadowType.NONE)
        content = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=18)

        grid = Gtk.Grid()
        grid.get_style_context().add_class("polyomino-grid")
        grid.set_column_spacing(12)
        grid.set_row_spacing(12)
        grid.set_column_homogeneous(True)
        grid.set_row_homogeneous(True)
        grid.set_margin_top(16)
        grid.set_margin_bottom(0)
        grid.set_margin_start(20)
        grid.set_margin_end(20)
        grid.attach(primary, 0, 0, 2, 2)
        grid.attach(horiz, 2, 0, 2, 1)
        grid.attach(sq1, 2, 1, 1, 1)
        grid.attach(sq2, 3, 1, 1, 1)
        content.pack_start(grid, False, False, 0)

        content.pack_start(self.build_install_dependencies_section(), False, False, 0)

        scrolled.add(content)
        outer.pack_start(scrolled, True, True, 0)
        return outer

    def install_section(self, subcommand):
        run_cmd(["polyomino", subcommand], in_terminal=True)

    def build_install_dependencies_section(self):
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        box.set_margin_start(20)
        box.set_margin_end(20)
        box.set_margin_bottom(16)

        title = Gtk.Label(label="INSTALL DEPENDENCIES", xalign=0)
        title.get_style_context().add_class("gc-section-title")
        box.pack_start(title, False, False, 0)

        flow = Gtk.FlowBox()
        flow.set_valign(Gtk.Align.START)
        flow.set_max_children_per_line(3)
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_row_spacing(12)
        flow.set_column_spacing(12)
        flow.set_homogeneous(True)
        flow.get_style_context().add_class("gc-flow")
        flow.set_filter_func(self.gc_filter_func)

        flow.add(self.create_gc_card(
            "📦", "Install All Dependencies",
            f"Install every section below ({len(INSTALL_SECTIONS)} tools) plus the emulator suite in one pass.",
            "Install All →", lambda: run_cmd(["polyomino", "full-install"], in_terminal=True),
            badge_text="BATCH", badge_style="connected"))

        for icon, title_text, desc, subcommand, badge in INSTALL_SECTIONS:
            flow.add(self.create_gc_card(
                icon, title_text, desc, "Install →",
                (lambda cmd=subcommand: self.install_section(cmd)),
                badge_text=badge, badge_style="tool"))

        self.gc_all_flowboxes.append(flow)
        box.pack_start(flow, False, False, 0)
        return box

    def create_footer(self):
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=16)
        box.get_style_context().add_class("footer-box")

        # Autostart Checkbox
        chk = Gtk.CheckButton(label="Show this welcome window on startup")
        chk.set_active(self.settings.get("autostart", True))
        chk.connect("toggled", self.on_autostart_toggled)
        box.pack_start(chk, False, False, 0)

        # Right-side action buttons
        btn_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)

        btn_menu = Gtk.Button(label="☰  Launcher")
        btn_menu.get_style_context().add_class("btn-action")
        btn_menu.connect("clicked", lambda b: run_cmd(["polyomino", "menu"]))
        btn_box.pack_start(btn_menu, False, False, 0)

        btn_close = Gtk.Button(label="✕  Close")
        btn_close.get_style_context().add_class("btn-accent")
        btn_close.connect("clicked", lambda b: self.close())
        btn_box.pack_start(btn_close, False, False, 0)

        box.pack_end(btn_box, False, False, 0)
        return box

    def on_autostart_toggled(self, chk):
        self.settings["autostart"] = chk.get_active()
        save_settings(self.settings)

def main():
    args = sys.argv[1:]

    # Toggle handling
    if "--toggle" in args:
        res = subprocess.run(["pgrep", "-f", "polyomino-welcome.py"], capture_output=True, text=True)
        pids = [p for p in res.stdout.strip().split() if p and int(p) != os.getpid()]
        if pids:
            for pid in pids:
                try: os.kill(int(pid), 15)
                except Exception: pass
            return 0

    # Autostart check
    if "--autostart" in args or "-a" in args:
        settings = load_settings()
        if not settings.get("autostart", True):
            return 0

    win = WelcomeWindow()
    win.connect("destroy", Gtk.main_quit)
    win.show_all()
    Gtk.main()
    return 0

if __name__ == "__main__":
    sys.exit(main())
