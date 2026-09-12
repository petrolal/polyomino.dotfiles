#!/usr/bin/env python3
"""
Polyomino Welcome Center
Similar to CachyOS Hello / Welcome app.
Provides quick start actions, gaming optimizations, system maintenance, and an autostart checkbox.
"""

import os
import sys
import json
import subprocess
from pathlib import Path

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
    "base": "#0F1117",
    "mantle": "#191C24",
    "crust": "#2B303C",
    "surface0": "#122232",
    "surface1": "#0E1C28",
    "surface2": "#1E3850",
    "overlay0": "#2E4057",
    "text": "#F8FAFC",
    "subtext0": "#94A3B8",
    "subtext1": "#B8C4D1",
    "accent": "#EBB434",
    "blue": "#EBB434",
    "teal": "#00D2D3",
    "green": "#10B981",
    "yellow": "#F59E0B",
    "peach": "#FFB347",
    "red": "#EF4444",
}

def load_palette():
    palette = dict(DEFAULT_PALETTE)
    
    # 1. Try reading tokens.css
    if TOKENS_FILE.exists():
        try:
            for line in TOKENS_FILE.read_text().splitlines():
                parts = line.strip().split()
                if len(parts) >= 3 and parts[0] == "@define-color":
                    name = parts[1]
                    val = parts[2].rstrip(";")
                    palette[name] = val
        except Exception:
            pass

    # 2. Try loading from palette conf if state specifies flavor
    elif THEME_STATE_FILE.exists():
        try:
            flavor = None
            for line in THEME_STATE_FILE.read_text().splitlines():
                if line.startswith("FLAVOR="):
                    flavor = line.split("=", 1)[1].strip()
                    break
            if flavor:
                candidate_paths = [
                    CONFIG_DIR / "themes" / "palettes" / f"{flavor}.conf",
                    Path.home() / "polyomino.dotfiles" / "themes" / "palettes" / f"{flavor}.conf",
                    Path("/etc/polyomino/themes/palettes") / f"{flavor}.conf",
                ]
                for p in candidate_paths:
                    if p.exists():
                        for pline in p.read_text().splitlines():
                            pline = pline.strip()
                            if pline and not pline.startswith("#") and "=" in pline:
                                k, v = pline.split("=", 1)
                                k = k.strip().lower()
                                v = v.strip().strip('"').strip("'")
                                if "#" in v:
                                    v = v.split("#")[0].strip()
                                palette[k] = v
                        break
        except Exception:
            pass

    if "accent" not in palette and "blue" in palette:
        palette["accent"] = palette["blue"]
    if "blue" not in palette and "accent" in palette:
        palette["blue"] = palette["accent"]

    return palette

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
        full_cmd = [term, "-e", "bash", "-c", " ".join(cmd_list) + "; echo ''; read -n 1 -s -r -p 'Press any key to close...'"]
    else:
        full_cmd = cmd_list
    subprocess.Popen(full_cmd, start_new_session=True)

class WelcomeWindow(Gtk.Window):
    def __init__(self):
        super().__init__(title="Polyomino Welcome Center")
        self.set_role("polyomino-welcome")
        self.set_default_size(840, 620)
        self.set_position(Gtk.WindowPosition.CENTER)

        self.palette = load_palette()
        self.settings = load_settings()

        # Load CSS Theme
        self.apply_css()

        # Main Layout
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

        # Tab 1: Quick Start
        tab1 = self.create_quick_start_tab()
        notebook.append_page(tab1, self.create_tab_label("🚀", "Quick Start"))

        # Tab 2: Gaming & Performance
        tab2 = self.create_gaming_tab()
        notebook.append_page(tab2, self.create_tab_label("🎮", "Gaming & Performance"))

        # Tab 3: System & Tools
        tab3 = self.create_system_tab()
        notebook.append_page(tab3, self.create_tab_label("🛠️", "System & Tools"))

        # Footer Section
        footer = self.create_footer()
        main_box.pack_start(footer, False, False, 0)

    def apply_css(self):
        css_provider = Gtk.CssProvider()
        p = self.palette

        custom_css = f"""
        * {{
            font-family: 'JetBrainsMono Nerd Font', 'JetBrains Mono', 'FiraCode Nerd Font', monospace;
        }}
        window,
        window.background,
        .background,
        .main-window-box,
        notebook,
        notebook > stack,
        notebook > stack > *,
        scrolledwindow,
        scrolledwindow viewport,
        viewport {{
            background-color: {p['base']};
            color: {p['text']};
            border: none;
        }}
        .header-box {{
            background-color: {p['mantle']};
            border-bottom: 1px solid {p['overlay0']};
            padding: 16px 24px;
        }}
        .header-icon {{
            color: {p['blue']};
            font-size: 24px;
            font-weight: bold;
        }}
        .header-title {{
            font-size: 17px;
            font-weight: bold;
            color: {p['text']};
        }}
        .header-subtitle {{
            font-size: 11px;
            color: {p['subtext0']};
            font-weight: normal;
        }}
        .version-badge {{
            background-color: {p['surface0']};
            color: {p['blue']};
            border: 1px solid {p['overlay0']};
            border-radius: 4px;
            padding: 4px 10px;
            font-size: 11px;
            font-weight: bold;
        }}
        notebook, .content-notebook {{
            background-color: {p['base']};
            border: none;
        }}
        notebook header {{
            background-color: {p['mantle']};
            border-bottom: 1px solid {p['overlay0']};
            padding: 0 12px;
        }}
        notebook tab {{
            background-color: transparent;
            color: {p['subtext0']};
            padding: 10px 18px;
            border: none;
            border-bottom: 2px solid transparent;
            font-weight: bold;
            font-size: 13px;
        }}
        notebook tab:hover {{
            background-color: {p['surface0']};
            color: {p['text']};
        }}
        notebook tab:checked {{
            background-color: {p['surface0']};
            color: {p['blue']};
            border-bottom: 2px solid {p['blue']};
        }}
        notebook tab label {{
            color: inherit;
            font-weight: bold;
        }}
        .tab-box {{
            padding: 4px;
        }}
        .action-card {{
            background-color: {p['mantle']};
            border: 1px solid {p['overlay0']};
            border-radius: 4px;
            padding: 12px 16px;
        }}
        .action-card:hover {{
            border-color: {p['blue']};
            background-color: {p['surface0']};
        }}
        .card-icon {{
            font-size: 22px;
            padding: 2px 6px;
        }}
        .card-title {{
            font-size: 13px;
            font-weight: bold;
            color: {p['text']};
        }}
        .card-desc {{
            font-size: 11px;
            color: {p['subtext0']};
        }}
        .btn-action {{
            background-color: {p['surface0']};
            color: {p['text']};
            border: 1px solid {p['overlay0']};
            border-radius: 4px;
            padding: 6px 14px;
            font-weight: bold;
            font-size: 12px;
        }}
        .btn-action:hover {{
            background-color: {p['blue']};
            color: {p['base']};
            border-color: {p['blue']};
        }}
        .btn-action:active {{
            background-color: {p['teal']};
            color: {p['base']};
            border-color: {p['teal']};
        }}
        .btn-accent {{
            background-color: {p['blue']};
            color: {p['base']};
            border: 1px solid {p['blue']};
            border-radius: 4px;
            padding: 6px 18px;
            font-weight: bold;
            font-size: 12px;
        }}
        .btn-accent:hover {{
            background-color: {p['teal']};
            border-color: {p['teal']};
            color: {p['base']};
        }}
        .btn-accent:active {{
            background-color: {p['text']};
            border-color: {p['text']};
            color: {p['base']};
        }}
        .footer-box {{
            background-color: {p['mantle']};
            border-top: 1px solid {p['overlay0']};
            padding: 12px 24px;
        }}
        checkbutton {{
            color: {p['text']};
            font-size: 12px;
            font-weight: normal;
        }}
        checkbutton check {{
            background-color: {p['surface0']};
            border: 1px solid {p['overlay0']};
            border-radius: 3px;
            min-height: 16px;
            min-width: 16px;
            margin-right: 8px;
        }}
        checkbutton check:checked {{
            background-color: {p['blue']};
            border-color: {p['blue']};
            color: {p['base']};
        }}
        checkbutton check:hover {{
            border-color: {p['blue']};
        }}
        scrollbar {{
            background-color: transparent;
        }}
        scrollbar slider {{
            background-color: {p['overlay0']};
            border-radius: 4px;
            min-width: 6px;
            min-height: 6px;
        }}
        scrollbar slider:hover {{
            background-color: {p['blue']};
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
        box.get_style_context().add_class("tab-box")
        
        lbl = Gtk.Label(label=f"[ {icon} {text} ]")
        box.pack_start(lbl, True, True, 0)
        box.show_all()
        return box

    def create_header(self):
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=16)
        box.get_style_context().add_class("header-box")

        icon_label = Gtk.Label(label="[ ⊞ ]")
        icon_label.get_style_context().add_class("header-icon")
        box.pack_start(icon_label, False, False, 0)

        text_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=3)
        title = Gtk.Label(label="POLYOMINO WELCOME CENTER", xalign=0)
        title.get_style_context().add_class("header-title")
        
        subtitle = Gtk.Label(label="Arch Linux · SwayFX CAD Desktop & Gaming Workstation", xalign=0)
        subtitle.get_style_context().add_class("header-subtitle")
        
        text_box.pack_start(title, False, False, 0)
        text_box.pack_start(subtitle, False, False, 0)
        box.pack_start(text_box, True, True, 0)

        # Version Badge
        badge = Gtk.Label(label="[ v0.1.0 ]")
        badge.get_style_context().add_class("version-badge")
        box.pack_end(badge, False, False, 0)

        return box

    def create_card(self, icon, title_text, desc_text, button_label, on_click_fn):
        card = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=14)
        card.get_style_context().add_class("action-card")

        icon_lbl = Gtk.Label(label=icon)
        icon_lbl.get_style_context().add_class("card-icon")
        card.pack_start(icon_lbl, False, False, 0)

        info_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=3)
        t_lbl = Gtk.Label(label=title_text, xalign=0)
        t_lbl.get_style_context().add_class("card-title")
        
        d_lbl = Gtk.Label(label=desc_text, xalign=0)
        d_lbl.get_style_context().add_class("card-desc")
        
        info_box.pack_start(t_lbl, False, False, 0)
        info_box.pack_start(d_lbl, False, False, 0)
        card.pack_start(info_box, True, True, 0)

        btn = Gtk.Button(label=f"[ {button_label} ]")
        btn.get_style_context().add_class("btn-action")
        btn.connect("clicked", lambda b: on_click_fn())
        card.pack_end(btn, False, False, 0)

        return card

    def create_quick_start_tab(self):
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_shadow_type(Gtk.ShadowType.NONE)
        
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        box.set_margin_top(14)
        box.set_margin_bottom(14)
        box.set_margin_start(18)
        box.set_margin_end(18)

        box.pack_start(self.create_card("🎨", "Theme & Palette", "Switch between Matriz, Encruza, Caravela, and Aruanda flavors", "Change Theme", lambda: run_cmd(["polyomino-theme-picker"])), False, False, 0)
        box.pack_start(self.create_card("🖼️", "Wallpapers", "Browse and select wallpapers matching the active theme flavor", "Pick Wallpaper", lambda: run_cmd(["polyomino-wallpaper-picker"])), False, False, 0)
        box.pack_start(self.create_card("⌨️", "Keybindings Cheatsheet", "View all Sway window management and desktop shortcuts", "View Shortcuts", lambda: run_cmd(["polyomino-whichkey"])), False, False, 0)
        box.pack_start(self.create_card("🖥️", "Display Layout", "Configure monitor positions, refresh rates, and scaling with wdisplays", "Configure Displays", lambda: run_cmd(["wdisplays"])), False, False, 0)

        scrolled.add(box)
        return scrolled

    def create_gaming_tab(self):
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_shadow_type(Gtk.ShadowType.NONE)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        box.set_margin_top(14)
        box.set_margin_bottom(14)
        box.set_margin_start(18)
        box.set_margin_end(18)

        box.pack_start(self.create_card("🚀", "Game Mode Toggle", "Boost CPU to performance, enable VRR, disable blur, and inhibit sleep", "Toggle Game Mode", lambda: run_cmd(["polyomino", "gamemode", "toggle"])), False, False, 0)
        box.pack_start(self.create_card("🕹️", "Install Gaming Stack", "Install GameMode, Gamescope, MangoHud, Vulkan tools, and Steam", "Install Tools", lambda: run_cmd(["polyomino", "install-gaming"], in_terminal=True)), False, False, 0)
        box.pack_start(self.create_card("🎮", "Launch Steam", "Launch Steam with NVIDIA PRIME offloading and game mode integration", "Launch Steam", lambda: run_cmd(["steam"])), False, False, 0)
        box.pack_start(self.create_card("📊", "MangoHud Overlay", "Configure GPU/CPU performance and FPS monitoring overlay", "MangoHud Info", lambda: run_cmd(["kitty", "-e", "man", "mangohud"])), False, False, 0)

        scrolled.add(box)
        return scrolled

    def create_system_tab(self):
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_shadow_type(Gtk.ShadowType.NONE)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        box.set_margin_top(14)
        box.set_margin_bottom(14)
        box.set_margin_start(18)
        box.set_margin_end(18)

        box.pack_start(self.create_card("🩺", "System Healthcheck", "Run automated diagnostics for dotfiles symlinks and dependencies", "Run Check", lambda: run_cmd(["polyomino", "healthcheck"], in_terminal=True)), False, False, 0)
        box.pack_start(self.create_card("🔄", "Update Dotfiles", "Pull latest updates from GitHub and redeploy configurations", "Update Now", lambda: run_cmd(["polyomino", "update"], in_terminal=True)), False, False, 0)
        box.pack_start(self.create_card("💾", "Backup Dotfiles", "Create a timestamped tarball backup of your live configurations", "Create Backup", lambda: run_cmd(["polyomino", "backup"], in_terminal=True)), False, False, 0)
        box.pack_start(self.create_card("⚙️", "Edit Configs", "Open and customize Sway, Waybar, Kitty, or Zsh configuration files", "Open Config Menu", lambda: run_cmd(["polyomino", "menu"])), False, False, 0)

        scrolled.add(box)
        return scrolled

    def create_footer(self):
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=16)
        box.get_style_context().add_class("footer-box")

        # Autostart Checkbox
        chk = Gtk.CheckButton(label="Show this welcome window on startup")
        chk.set_active(self.settings.get("autostart", True))
        chk.connect("toggled", self.on_autostart_toggled)
        box.pack_start(chk, False, False, 0)

        # Right-side buttons
        btn_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        
        btn_menu = Gtk.Button(label="[ ☰ Launcher ]")
        btn_menu.get_style_context().add_class("btn-action")
        btn_menu.connect("clicked", lambda b: run_cmd(["polyomino", "menu"]))
        btn_box.pack_start(btn_menu, False, False, 0)

        btn_close = Gtk.Button(label="[ ✕ Close ]")
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
