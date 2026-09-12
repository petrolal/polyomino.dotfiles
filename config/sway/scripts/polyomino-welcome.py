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

CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "polyomino"
SETTINGS_FILE = CONFIG_DIR / "welcome.json"
TOKENS_FILE = CONFIG_DIR / "theme" / "tokens.css"

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

        self.settings = load_settings()

        # Load CSS Theme
        self.apply_css()

        # Main Layout
        main_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        self.add(main_box)

        # Header Section
        header = self.create_header()
        main_box.pack_start(header, False, False, 0)

        # Content Notebook (Tabs)
        notebook = Gtk.Notebook()
        notebook.set_tab_pos(Gtk.PositionType.TOP)
        main_box.pack_start(notebook, True, True, 0)

        # Tab 1: Quick Start
        tab1 = self.create_quick_start_tab()
        notebook.append_page(tab1, Gtk.Label(label="  🚀 Quick Start  "))

        # Tab 2: Gaming & Performance
        tab2 = self.create_gaming_tab()
        notebook.append_page(tab2, Gtk.Label(label="  🎮 Gaming & Performance  "))

        # Tab 3: System & Tools
        tab3 = self.create_system_tab()
        notebook.append_page(tab3, Gtk.Label(label="  🛠️ System & Tools  "))

        # Footer Section
        footer = self.create_footer()
        main_box.pack_start(footer, False, False, 0)

    def apply_css(self):
        css_provider = Gtk.CssProvider()
        
        # Base colors
        base_color = "#0F1117"
        mantle_color = "#191C24"
        text_color = "#F8FAFC"
        accent_color = "#EBB434"
        subtext_color = "#94A3B8"
        green_color = "#10B981"
        blue_color = "#3B82F6"

        if TOKENS_FILE.exists():
            try:
                for line in TOKENS_FILE.read_text().splitlines():
                    parts = line.strip().split()
                    if len(parts) >= 3 and parts[0] == "@define-color":
                        name = parts[1]
                        val = parts[2].rstrip(";")
                        if name == "base": base_color = val
                        elif name == "mantle": mantle_color = val
                        elif name == "text": text_color = val
                        elif name == "accent": accent_color = val
                        elif name == "green": green_color = val
                        elif name == "blue": blue_color = val
            except Exception:
                pass

        custom_css = f"""
        window {{
            background-color: {base_color};
            color: {text_color};
            font-family: 'JetBrainsMono Nerd Font', 'JetBrains Mono', sans-serif;
        }}
        .header-box {{
            background-color: {mantle_color};
            border-bottom: 2px solid {accent_color};
            padding: 18px 24px;
        }}
        .header-title {{
            font-size: 20px;
            font-weight: bold;
            color: {accent_color};
        }}
        .header-subtitle {{
            font-size: 12px;
            color: {subtext_color};
        }}
        notebook {{
            background-color: {base_color};
            border: none;
        }}
        notebook tab {{
            background-color: {mantle_color};
            color: {subtext_color};
            padding: 10px 18px;
            border: none;
            font-weight: bold;
            font-size: 13px;
        }}
        notebook tab:checked {{
            background-color: {base_color};
            color: {accent_color};
            border-bottom: 2px solid {accent_color};
        }}
        .action-card {{
            background-color: {mantle_color};
            border: 1px solid #282C37;
            border-radius: 6px;
            padding: 14px 18px;
            transition: all 0.2s ease;
        }}
        .action-card:hover {{
            border-color: {accent_color};
            background-color: #20242E;
        }}
        .card-title {{
            font-size: 14px;
            font-weight: bold;
            color: {text_color};
        }}
        .card-desc {{
            font-size: 12px;
            color: {subtext_color};
        }}
        .btn-action {{
            background-color: #242936;
            color: {text_color};
            border: 1px solid #3B4252;
            border-radius: 4px;
            padding: 6px 14px;
            font-weight: bold;
        }}
        .btn-action:hover {{
            background-color: {accent_color};
            color: {base_color};
            border-color: {accent_color};
        }}
        .btn-accent {{
            background-color: {accent_color};
            color: {base_color};
            border: 1px solid {accent_color};
            border-radius: 4px;
            padding: 8px 18px;
            font-weight: bold;
        }}
        .btn-accent:hover {{
            background-color: #F59E0B;
        }}
        .footer-box {{
            background-color: {mantle_color};
            border-top: 1px solid #282C37;
            padding: 14px 24px;
        }}
        checkbutton {{
            color: {text_color};
            font-size: 13px;
        }}
        """

        css_provider.load_from_data(custom_css.encode('utf-8'))
        Gtk.StyleContext.add_provider_for_screen(
            Gdk.Screen.get_default(),
            css_provider,
            Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
        )

    def create_header(self):
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=16)
        box.get_style_context().add_class("header-box")

        icon_label = Gtk.Label(label="⊞")
        icon_label.set_markup("<span font='28'>⊞</span>")
        box.pack_start(icon_label, False, False, 0)

        text_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        title = Gtk.Label(label="POLYOMINO WELCOME CENTER", xalign=0)
        title.get_style_context().add_class("header-title")
        
        subtitle = Gtk.Label(label="Arch Linux · SwayFX CAD Desktop & Gaming Workstation", xalign=0)
        subtitle.get_style_context().add_class("header-subtitle")
        
        text_box.pack_start(title, False, False, 0)
        text_box.pack_start(subtitle, False, False, 0)
        box.pack_start(text_box, True, True, 0)

        # Version Badge
        badge = Gtk.Label()
        badge.set_markup("<span background='#242936' foreground='#EBB434' font='10' weight='bold'>  v0.1.0  </span>")
        box.pack_end(badge, False, False, 0)

        return box

    def create_card(self, icon, title_text, desc_text, button_label, on_click_fn):
        card = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=14)
        card.get_style_context().add_class("action-card")

        icon_lbl = Gtk.Label()
        icon_lbl.set_markup(f"<span font='20'>{icon}</span>")
        card.pack_start(icon_lbl, False, False, 0)

        info_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=3)
        t_lbl = Gtk.Label(label=title_text, xalign=0)
        t_lbl.get_style_context().add_class("card-title")
        
        d_lbl = Gtk.Label(label=desc_text, xalign=0)
        d_lbl.get_style_context().add_class("card-desc")
        
        info_box.pack_start(t_lbl, False, False, 0)
        info_box.pack_start(d_lbl, False, False, 0)
        card.pack_start(info_box, True, True, 0)

        btn = Gtk.Button(label=button_label)
        btn.get_style_context().add_class("btn-action")
        btn.connect("clicked", lambda b: on_click_fn())
        card.pack_end(btn, False, False, 0)

        return card

    def create_quick_start_tab(self):
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        box.set_margin_top(16)
        box.set_margin_bottom(16)
        box.set_margin_start(20)
        box.set_margin_end(20)

        box.pack_start(self.create_card("🎨", "Theme & Palette", "Switch between Matriz, Encruza, Caravela, and Aruanda flavors", "Change Theme", lambda: run_cmd(["polyomino-theme-picker"])), False, False, 0)
        box.pack_start(self.create_card("🖼️", "Wallpapers", "Browse and select wallpapers matching the active theme flavor", "Pick Wallpaper", lambda: run_cmd(["polyomino-wallpaper-picker"])), False, False, 0)
        box.pack_start(self.create_card("⌨️", "Keybindings Cheatsheet", "View all Sway window management and desktop shortcuts", "View Shortcuts", lambda: run_cmd(["polyomino-whichkey"])), False, False, 0)
        box.pack_start(self.create_card("🖥️", "Display Layout", "Configure monitor positions, refresh rates, and scaling with wdisplays", "Configure Displays", lambda: run_cmd(["wdisplays"])), False, False, 0)

        scrolled.add(box)
        return scrolled

    def create_gaming_tab(self):
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        box.set_margin_top(16)
        box.set_margin_bottom(16)
        box.set_margin_start(20)
        box.set_margin_end(20)

        box.pack_start(self.create_card("🚀", "Game Mode Toggle", "Boost CPU to performance, enable VRR, disable blur, and inhibit sleep", "Toggle Game Mode", lambda: run_cmd(["polyomino", "gamemode", "toggle"])), False, False, 0)
        box.pack_start(self.create_card("🕹️", "Install Gaming Stack", "Install GameMode, Gamescope, MangoHud, Vulkan tools, and Steam", "Install Tools", lambda: run_cmd(["polyomino", "install-gaming"], in_terminal=True)), False, False, 0)
        box.pack_start(self.create_card("🎮", "Launch Steam", "Launch Steam with NVIDIA PRIME offloading and game mode integration", "Launch Steam", lambda: run_cmd(["steam"])), False, False, 0)
        box.pack_start(self.create_card("📊", "MangoHud Overlay", "Configure GPU/CPU performance and FPS monitoring overlay", "MangoHud Info", lambda: run_cmd(["kitty", "-e", "man", "mangohud"])), False, False, 0)

        scrolled.add(box)
        return scrolled

    def create_system_tab(self):
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        box.set_margin_top(16)
        box.set_margin_bottom(16)
        box.set_margin_start(20)
        box.set_margin_end(20)

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
        
        btn_menu = Gtk.Button(label="Open Launcher")
        btn_menu.get_style_context().add_class("btn-action")
        btn_menu.connect("clicked", lambda b: run_cmd(["polyomino", "menu"]))
        btn_box.pack_start(btn_menu, False, False, 0)

        btn_close = Gtk.Button(label="Close")
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
