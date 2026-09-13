#!/usr/bin/env python3
"""
Polyomino Welcome Center
Asymmetrical Polyomino Grid layout for quick start actions, gaming optimizations, and system maintenance.
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
        full_cmd = [term, "-e", "bash", "-c", " ".join(cmd_list) + "; echo ''; read -n 1 -s -r -p 'Press any key to close...'"]
    else:
        full_cmd = cmd_list
    subprocess.Popen(full_cmd, start_new_session=True)

class WelcomeWindow(Gtk.Window):
    def __init__(self):
        super().__init__(title="Polyomino Welcome Center")
        self.set_role("polyomino-welcome")
        self.set_default_size(860, 620)
        self.set_position(Gtk.WindowPosition.CENTER)

        self.settings = load_settings()

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

        # Tab 1: Quick Start (Polyomino Grid)
        tab1 = self.create_quick_start_tab()
        notebook.append_page(tab1, self.create_tab_label("🚀", "Quick Start"))

        # Tab 2: Gaming & Performance (Polyomino Grid)
        tab2 = self.create_gaming_tab()
        notebook.append_page(tab2, self.create_tab_label("🎮", "Gaming & Performance"))

        # Tab 3: System & Tools (Polyomino Grid)
        tab3 = self.create_system_tab()
        notebook.append_page(tab3, self.create_tab_label("🛠️", "System & Tools"))

        # Footer Section
        footer = self.create_footer()
        main_box.pack_start(footer, False, False, 0)

    def apply_css(self):
        css_provider = Gtk.CssProvider()
        p = DEFAULT_PALETTE

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
            background-color: {p['bg_base']};
            color: {p['text_primary']};
            border: none;
        }}

        /* Header Bar */
        .header-box {{
            background-color: {p['bg_mantle']};
            border-bottom: 1px solid {p['border_subtle']};
            padding: 16px 24px;
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

        /* Notebook & Tabs */
        notebook, .content-notebook {{
            background-color: {p['bg_base']};
            border: none;
        }}
        notebook header {{
            background-color: {p['bg_mantle']};
            border-bottom: 1px solid {p['border_subtle']};
            padding: 0 16px;
        }}
        notebook tab {{
            background-color: transparent;
            color: {p['text_secondary']};
            padding: 10px 20px;
            border: none;
            border-bottom: 2px solid transparent;
            font-weight: 600;
            font-size: 13px;
            transition: all 120ms ease-in-out;
        }}
        notebook tab:hover {{
            background-color: {p['bg_surface']};
            color: {p['text_primary']};
        }}
        notebook tab:checked {{
            background-color: {p['bg_surface']};
            color: {p['text_primary']};
            border-bottom: 2px solid {p['accent_violet']};
        }}
        notebook tab label {{
            color: inherit;
            font-weight: 600;
        }}
        .tab-label-box {{
            padding: 2px 4px;
        }}

        /* Polyomino Grid & Interactive Tiles */
        .polyomino-grid {{
            background-color: {p['bg_base']};
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

    def create_gaming_tab(self):
        primary = self.create_primary_tile(
            icon="🚀",
            title="Game Mode Toggle",
            desc="Boost CPU governor to performance, engage VRR/FreeSync, disable compositor blur, and inhibit display sleep during active gaming sessions.",
            tag="PERFORMANCE",
            action_hint="Toggle Mode",
            accent_color="violet",
            on_click_fn=lambda: run_cmd(["polyomino", "gamemode", "toggle"])
        )

        horiz = self.create_horizontal_tile(
            icon="🕹️",
            title="Install Gaming Stack",
            desc="Automate installation of Feral GameMode, Gamescope, MangoHud, Vulkan drivers, and Steam.",
            tag="SETUP",
            action_hint="Install Stack",
            accent_color="amber",
            on_click_fn=lambda: run_cmd(["polyomino", "install-gaming"], in_terminal=True)
        )

        sq1 = self.create_square_tile(
            icon="🎮",
            title="Steam Client",
            desc="Launch with GPU offload.",
            action_hint="Launch",
            accent_color="blue",
            on_click_fn=lambda: run_cmd(["steam"])
        )

        sq2 = self.create_square_tile(
            icon="📊",
            title="MangoHud",
            desc="FPS and GPU overlay docs.",
            action_hint="Manual",
            accent_color="teal",
            on_click_fn=lambda: run_cmd(["kitty", "-e", "man", "mangohud"])
        )

        return self.build_grid_container(primary, horiz, sq1, sq2)

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

        return self.build_grid_container(primary, horiz, sq1, sq2)

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
