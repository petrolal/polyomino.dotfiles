#!/usr/bin/env python3
"""
Polyomino Unified GTK3 Bento Menu & Application Launcher
Strictly replicates the visual architecture, typography, and card anatomy of the Polyomino Welcome Center GUI.

Modes:
  1. Desktop App Launcher (--drun): Scans installed .desktop applications via Gio.AppInfo,
     categorizes them (DEV, GAME, SYS, MEDIA, NET, APP), and launches the selection.
  2. Generic Tile Menu (reads JSON tiles from stdin): Used for Which-Key cheatsheet,
     Theme Picker, Wallpaper Picker, Main Menu, and Config Editor.

Includes:
  - 4-tier Welcome Center layout (Header banner, 0px search bar, Bento card grid, Vim footer).
  - Dynamic [ NORMAL ] / [ INSERT ] Vim navigation modes.
  - Native GIcon / Papirus icon theme rendering.
  - SwayFX glass blur and depth shadow compatibility.
"""

import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk, Gdk, GLib, Gio, Pango

GLib.set_prgname("polyomino-tilemenu")
GLib.set_application_name("Polyomino Bento Menu")

CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "polyomino"
TOKENS_FILE = CONFIG_DIR / "theme" / "tokens.css"

DEFAULT_PALETTE = {
    "base": "#0D1117", "mantle": "#161B22", "crust": "#010409",
    "surface0": "#1C2128", "surface1": "#21262D", "surface2": "#2D333B",
    "overlay0": "#30363D", "text": "#F0F6FC", "subtext0": "#8B949E",
    "subtext1": "#C9D1D9", "accent": "#8B5CF6", "blue": "#3B82F6",
    "teal": "#06B6D4", "green": "#10B981", "yellow": "#F59E0B",
    "peach": "#D97706", "maroon": "#D97706", "red": "#EF4444",
    "mauve": "#8B5CF6", "pink": "#C084FC", "lavender": "#A78BFA",
    "sapphire": "#3B82F6", "sky": "#22D3EE",
}

ACCENT_NAMES = [
    "accent", "blue", "teal", "green", "yellow", "peach",
    "red", "mauve", "sapphire", "pink", "lavender", "sky", "subtext0",
]


def load_palette():
    palette = dict(DEFAULT_PALETTE)
    if TOKENS_FILE.exists():
        try:
            for name, hexval in re.findall(r"@define-color\s+(\w+)\s+(#[0-9A-Fa-f]{3,8});", TOKENS_FILE.read_text()):
                palette[name] = hexval
        except Exception:
            pass
    return palette


def build_css(p):
    tile_rules = []
    for name in ACCENT_NAMES:
        hexval = p.get(name, p["accent"])
        tile_rules.append(f"""
        button.tile-{name} {{
            border: 1px solid alpha({hexval}, 0.35);
        }}
        button.tile-{name}:hover, button.tile-{name}:focus {{
            border: 1px solid {hexval};
            background-color: alpha(#FFFFFF, 0.05);
            box-shadow: 0 0 12px alpha({hexval}, 0.25);
        }}
        button.tile-{name}:active {{
            background-color: alpha({hexval}, 0.15);
        }}
        .tile-badge-{name} {{
            background-color: alpha({hexval}, 0.18);
            border: 1px solid alpha({hexval}, 0.35);
            color: {hexval};
        }}
        .tile-icon-{name} {{
            color: {hexval};
        }}
        .tile-action-hint-{name} {{
            color: {hexval};
        }}
        """)

    return f"""
    * {{
        font-family: 'JetBrainsMono Nerd Font', 'JetBrains Mono', monospace;
        outline: none;
        box-shadow: none;
        -gtk-outline-radius: 0;
    }}
    window, window.background, .background {{
        background-color: transparent;
        color: {p['text']};
        border: none;
        border-radius: 0px;
    }}
    scrolledwindow, scrolledwindow viewport, viewport, flowboxchild {{
        background-color: transparent;
        color: {p['text']};
        border: none;
    }}
    .menu-window {{
        background-color: alpha({p['base']}, 0.88);
        border: 1px solid alpha({p['accent']}, 0.35);
        border-radius: 0px;
    }}

    /* 1. Header Row */
    .menu-header-box {{
        padding: 14px 16px 10px 16px;
        background-color: {p['mantle']};
        border-bottom: 1px solid alpha({p['accent']}, 0.2);
    }}
    .header-glyph-box {{
        background-color: {p['surface0']};
        border: 1px solid alpha({p['accent']}, 0.35);
        border-radius: 2px;
        padding: 4px 8px;
    }}
    .header-glyph {{
        font-size: 13px;
        font-weight: 700;
        color: {p['accent']};
    }}
    .header-title {{
        font-size: 12px;
        font-weight: 700;
        letter-spacing: 0.5px;
        color: {p['text']};
    }}
    .header-subtitle {{
        font-size: 9px;
        color: {p['subtext0']};
    }}
    .header-badge {{
        font-size: 9px;
        font-weight: 700;
        padding: 3px 8px;
        border-radius: 2px;
        background-color: alpha({p['accent']}, 0.15);
        border: 1px solid alpha({p['accent']}, 0.35);
        color: {p.get('lavender', p['accent'])};
    }}

    /* 2. Search / Filter Input Bar */
    .search-input-box {{
        margin: 10px 16px 8px 16px;
        background-color: {p['mantle']};
        border: 1px solid alpha({p['accent']}, 0.35);
        border-radius: 0px;
        padding: 4px 8px;
    }}
    .search-prompt {{
        font-size: 11px;
        font-weight: 700;
        color: {p['accent']};
        padding: 2px 6px;
    }}
    .search-entry {{
        background-color: transparent;
        color: {p['text']};
        border: none;
        box-shadow: none;
        font-size: 11px;
    }}
    .search-entry:focus {{
        outline: none;
        border: none;
        box-shadow: none;
    }}

    /* 3. Bento Card Anatomy */
    button.polyomino-tile {{
        background-color: {p['surface0']};
        background-image: none;
        border-radius: 2px;
        padding: 10px 12px;
        box-shadow: none;
        text-shadow: none;
        transition: all 120ms ease-in-out;
    }}
    button.polyomino-tile.tile-card {{
        min-height: 48px;
    }}
    button.polyomino-tile.tile-square {{
        min-height: 44px;
        padding: 8px 10px;
    }}
    .tile-icon {{
        font-size: 16px;
    }}
    .tile-badge {{
        font-size: 9px;
        font-weight: 700;
        padding: 2px 6px;
        border-radius: 2px;
    }}
    .tile-title {{
        font-size: 11px;
        font-weight: 700;
        color: {p['text']};
    }}
    .tile-desc {{
        font-size: 9px;
        color: {p['subtext0']};
    }}
    .tile-action-hint {{
        font-size: 9px;
        font-weight: 600;
        color: alpha({p['accent']}, 0.75);
    }}

    /* 4. Bottom Status & Vim Mode Bar */
    .footer-bar {{
        background-color: {p['mantle']};
        border-top: 1px solid alpha({p['accent']}, 0.3);
        border-radius: 0px;
        padding: 6px 14px;
    }}
    .mode-badge-normal {{
        background-color: {p['accent']};
        color: {p['base']};
        font-size: 9px;
        font-weight: 700;
        border-radius: 2px;
        padding: 2px 6px;
    }}
    .mode-badge-insert {{
        background-color: {p.get('teal', '#06B6D4')};
        color: {p['base']};
        font-size: 9px;
        font-weight: 700;
        border-radius: 2px;
        padding: 2px 6px;
    }}
    .footer-hints {{
        font-size: 9px;
        color: {p['subtext0']};
    }}
    .footer-close-badge {{
        font-size: 9px;
        font-weight: 700;
        background-color: {p['surface0']};
        border: 1px solid alpha({p['accent']}, 0.35);
        color: {p['subtext0']};
        border-radius: 2px;
        padding: 2px 6px;
    }}

    scrollbar {{ background-color: transparent; }}
    scrollbar slider {{
        background-color: {p['overlay0']};
        border-radius: 2px;
        min-width: 5px;
        min-height: 5px;
    }}
    scrollbar slider:hover {{ background-color: {p['accent']}; }}
    {''.join(tile_rules)}
    """


def get_installed_desktop_apps():
    """Discovers installed applications from desktop files via Gio.AppInfo."""
    apps = []
    seen = set()

    for app in Gio.AppInfo.get_all():
        try:
            if not app.should_show():
                continue
            name = app.get_display_name() or app.get_name()
            if not name or name in seen:
                continue
            seen.add(name)

            desc = app.get_description() or ""
            gicon = app.get_icon()
            icon_name = gicon.to_string() if gicon else ""
            cats = app.get_categories() or ""

            # Categorize into Welcome Center Bento Accent colors
            accent = "accent"
            badge = "APP"
            if any(k in cats for k in ["Development", "IDE", "TextEditor", "TerminalEmulator"]):
                accent = "teal"
                badge = "DEV"
            elif any(k in cats for k in ["Game", "Games", "Emulator"]):
                accent = "yellow"
                badge = "GAME"
            elif any(k in cats for k in ["AudioVideo", "Audio", "Video", "Player", "Music"]):
                accent = "pink"
                badge = "MEDIA"
            elif any(k in cats for k in ["System", "Settings", "Utility", "Monitor"]):
                accent = "blue"
                badge = "SYS"
            elif any(k in cats for k in ["Network", "WebBrowser", "Email", "Chat"]):
                accent = "sky"
                badge = "NET"

            apps.append({
                "id": app.get_id() or name,
                "title": name,
                "desc": desc,
                "icon": "",
                "icon_name": icon_name,
                "gicon": gicon,
                "accent": accent,
                "badge": badge,
                "variant": "card",
                "action_hint": "Launch",
                "_app_info": app,
            })
        except Exception:
            continue

    apps.sort(key=lambda x: x["title"].lower())
    return apps


class BentoTileMenu(Gtk.Window):
    def __init__(self, title_text, subtitle_text, badge_text, tiles, columns, width, height, info_only, is_drun):
        super().__init__(title=title_text)
        self.set_role("polyomino-tilemenu")
        self.set_decorated(False)
        self.set_default_size(width, height)
        self.set_position(Gtk.WindowPosition.CENTER)
        self.result = None
        self.info_only = info_only
        self.is_drun = is_drun
        self.tiles_data = tiles
        self.columns = columns

        self.set_app_paintable(True)
        screen = Gdk.Screen.get_default()
        visual = screen.get_rgba_visual() if screen else None
        if visual is not None:
            self.set_visual(visual)

        self.apply_css()

        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        outer.get_style_context().add_class("menu-window")
        self.add(outer)

        # 1. Header Row
        header_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        header_box.get_style_context().add_class("menu-header-box")

        left_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        glyph_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=0)
        glyph_box.get_style_context().add_class("header-glyph-box")
        glyph_lbl = Gtk.Label(label="[ ⮽ ]")
        glyph_lbl.get_style_context().add_class("header-glyph")
        glyph_box.pack_start(glyph_lbl, False, False, 0)
        left_box.pack_start(glyph_box, False, False, 0)

        text_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=1)
        text_box.set_valign(Gtk.Align.CENTER)
        title_lbl = Gtk.Label(label=title_text, xalign=0)
        title_lbl.get_style_context().add_class("header-title")
        subtitle_lbl = Gtk.Label(label=subtitle_text, xalign=0)
        subtitle_lbl.get_style_context().add_class("header-subtitle")
        text_box.pack_start(title_lbl, False, False, 0)
        text_box.pack_start(subtitle_lbl, False, False, 0)
        left_box.pack_start(text_box, True, True, 0)

        header_box.pack_start(left_box, True, True, 0)

        if badge_text:
            badge_lbl = Gtk.Label(label=badge_text)
            badge_lbl.set_valign(Gtk.Align.CENTER)
            badge_lbl.get_style_context().add_class("header-badge")
            header_box.pack_end(badge_lbl, False, False, 0)

        outer.pack_start(header_box, False, False, 0)

        # 2. Search / Filter Input Bar
        search_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        search_box.get_style_context().add_class("search-input-box")

        prompt_lbl = Gtk.Label(label="❯")
        prompt_lbl.get_style_context().add_class("search-prompt")
        search_box.pack_start(prompt_lbl, False, False, 0)

        self.search_entry = Gtk.Entry()
        placeholder = "Search applications... (Press / for Insert Mode)" if is_drun else "Search shortcuts & actions... (Press / for Insert Mode)"
        self.search_entry.set_placeholder_text(placeholder)
        self.search_entry.get_style_context().add_class("search-entry")
        self.search_entry.connect("changed", self.on_search_changed)
        self.search_entry.connect("focus-in-event", self.on_search_focus_in)
        self.search_entry.connect("focus-out-event", self.on_search_focus_out)
        self.search_entry.connect("activate", self.on_search_activated)
        search_box.pack_start(self.search_entry, True, True, 0)

        outer.pack_start(search_box, False, False, 0)

        # 3. Content Grid (Welcome Center Bento Card Style)
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_shadow_type(Gtk.ShadowType.NONE)

        self.flow = Gtk.FlowBox()
        self.flow.set_valign(Gtk.Align.START)
        self.flow.set_max_children_per_line(columns)
        self.flow.set_min_children_per_line(1)
        self.flow.set_selection_mode(Gtk.SelectionMode.NONE)
        self.flow.set_row_spacing(8)
        self.flow.set_column_spacing(8)
        self.flow.set_homogeneous(True)
        self.flow.set_margin_start(16)
        self.flow.set_margin_end(16)
        self.flow.set_margin_bottom(10)
        self.flow.set_margin_top(4)
        self.flow.set_filter_func(self.filter_tile)

        self.first_tile = None
        self.tile_widgets = []
        for t in tiles:
            tile = self.make_bento_tile(t)
            tile._tile_data = t
            self.tile_widgets.append(tile)
            if self.first_tile is None:
                self.first_tile = tile
            self.flow.add(tile)

        scrolled.add(self.flow)
        outer.pack_start(scrolled, True, True, 0)

        # 4. Bottom Status & Vim Mode Bar
        self.footer_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        self.footer_box.get_style_context().add_class("footer-bar")

        self.mode_badge = Gtk.Label(label="[ NORMAL ]")
        self.mode_badge.get_style_context().add_class("mode-badge-normal")
        self.footer_box.pack_start(self.mode_badge, False, False, 0)

        self.mode_hints = Gtk.Label(label="h/j/k/l Navigate  •  / Search  •  Enter Launch", xalign=0)
        self.mode_hints.get_style_context().add_class("footer-hints")
        self.footer_box.pack_start(self.mode_hints, True, True, 0)

        close_pill = Gtk.Label(label="[ Esc Close ]")
        close_pill.get_style_context().add_class("footer-close-badge")
        self.footer_box.pack_end(close_pill, False, False, 0)

        outer.pack_start(self.footer_box, False, False, 0)

        self.connect("key-press-event", self.on_key)
        self.connect("destroy", lambda w: Gtk.main_quit())
        self.connect("map-event", self.on_map)

    def set_normal_mode(self):
        ctx = self.mode_badge.get_style_context()
        ctx.remove_class("mode-badge-insert")
        ctx.add_class("mode-badge-normal")
        self.mode_badge.set_text("[ NORMAL ]")
        self.mode_hints.set_text("h/j/k/l Navigate  •  / Search  •  Enter Launch")

    def set_insert_mode(self):
        ctx = self.mode_badge.get_style_context()
        ctx.remove_class("mode-badge-normal")
        ctx.add_class("mode-badge-insert")
        self.mode_badge.set_text("[ INSERT ]")
        self.mode_hints.set_text("Type to filter  •  Enter Launch  •  Esc Normal Mode")

    def on_search_focus_in(self, widget, event):
        self.set_insert_mode()
        return False

    def on_search_focus_out(self, widget, event):
        self.set_normal_mode()
        return False

    def on_search_changed(self, entry):
        self.flow.invalidate_filter()

    def on_search_activated(self, entry):
        # When pressing enter in search entry, activate first visible tile
        for child in self.flow.get_children():
            if child.get_child_visible():
                btn = child.get_child()
                if btn:
                    btn.clicked()
                    return

    def filter_tile(self, child):
        query = self.search_entry.get_text().strip().lower()
        if not query:
            return True
        btn = child.get_child()
        data = getattr(btn, "_tile_data", {})
        title = data.get("title", "").lower()
        desc = data.get("desc", "").lower()
        badge = data.get("badge", "").lower()
        tid = data.get("id", "").lower()
        return query in title or query in desc or query in badge or query in tid

    def on_map(self, widget, event):
        if self.first_tile is not None:
            self.first_tile.grab_focus()

    def apply_css(self):
        provider = Gtk.CssProvider()
        try:
            provider.load_from_data(build_css(load_palette()).encode("utf-8"))
            Gtk.StyleContext.add_provider_for_screen(
                Gdk.Screen.get_default(), provider, Gtk.STYLE_PROVIDER_PRIORITY_USER
            )
        except Exception as e:
            print(f"Warning: failed to load tile menu CSS: {e}", file=sys.stderr)

    def make_bento_tile(self, t):
        accent = t.get("accent", "accent")
        variant = t.get("variant", "card")
        btn = Gtk.Button()
        btn.set_relief(Gtk.ReliefStyle.NONE)
        btn.set_can_focus(True)
        btn.get_style_context().add_class("polyomino-tile")
        btn.get_style_context().add_class(f"tile-{accent}")
        if variant == "square":
            btn.get_style_context().add_class("tile-square")
        else:
            btn.get_style_context().add_class("tile-card")

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        box.set_hexpand(True)
        box.set_vexpand(True)

        # Top Row: Icon + Upper-Right Status Pill Badge
        top_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)

        gicon = t.get("gicon")
        icon_name = t.get("icon_name", "")
        icon_text = t.get("icon", "")

        if gicon:
            img = Gtk.Image.new_from_gicon(gicon, Gtk.IconSize.DND)
            top_row.pack_start(img, False, False, 0)
        elif icon_name:
            theme = Gtk.IconTheme.get_default()
            if theme.has_icon(icon_name):
                img = Gtk.Image.new_from_icon_name(icon_name, Gtk.IconSize.DND)
                top_row.pack_start(img, False, False, 0)
            elif icon_text:
                icon_lbl = Gtk.Label(label=icon_text)
                icon_lbl.get_style_context().add_class("tile-icon")
                icon_lbl.get_style_context().add_class(f"tile-icon-{accent}")
                top_row.pack_start(icon_lbl, False, False, 0)
        elif icon_text:
            icon_lbl = Gtk.Label(label=icon_text)
            icon_lbl.get_style_context().add_class("tile-icon")
            icon_lbl.get_style_context().add_class(f"tile-icon-{accent}")
            top_row.pack_start(icon_lbl, False, False, 0)

        if t.get("badge"):
            badge = Gtk.Label(label=t["badge"])
            badge.set_valign(Gtk.Align.CENTER)
            badge.get_style_context().add_class("tile-badge")
            badge.get_style_context().add_class(f"tile-badge-{accent}")
            top_row.pack_end(badge, False, False, 0)

        box.pack_start(top_row, False, False, 0)

        # Center Row: Bold Item Title in Monospace
        title_lbl = Gtk.Label(label=t.get("title", ""), xalign=0)
        title_lbl.set_ellipsize(Pango.EllipsizeMode.END)
        title_lbl.set_hexpand(True)
        title_lbl.get_style_context().add_class("tile-title")
        box.pack_start(title_lbl, False, False, 0)

        if t.get("desc"):
            desc_lbl = Gtk.Label(label=t["desc"], xalign=0)
            desc_lbl.set_ellipsize(Pango.EllipsizeMode.END)
            desc_lbl.get_style_context().add_class("tile-desc")
            box.pack_start(desc_lbl, True, True, 0)

        # Bottom Row: Subtle Action Arrow
        action_hint = t.get("action_hint", "Launch" if self.is_drun else "Execute")
        hint_lbl = Gtk.Label(label=f"{action_hint}  →", xalign=0)
        hint_lbl.get_style_context().add_class("tile-action-hint")
        hint_lbl.get_style_context().add_class(f"tile-action-hint-{accent}")
        box.pack_end(hint_lbl, False, False, 0)

        btn.add(box)
        btn.connect("clicked", lambda b: self.select_item(t))
        return btn

    def select_item(self, tile_data):
        tid = tile_data.get("id")
        app_info = tile_data.get("_app_info")

        if self.is_drun and app_info:
            try:
                app_info.launch([], None)
            except Exception:
                cmd = app_info.get_commandline()
                if cmd:
                    subprocess.Popen(cmd, shell=True, start_new_session=True)
            self.close()
            return

        if not self.info_only:
            self.result = tid
        self.close()

    def on_key(self, widget, event):
        focused = self.get_focus()
        is_searching = focused == self.search_entry

        if event.keyval == Gdk.KEY_Escape:
            if is_searching and self.search_entry.get_text():
                self.search_entry.set_text("")
                if self.first_tile:
                    self.first_tile.grab_focus()
                self.set_normal_mode()
                return True
            else:
                self.result = None
                self.close()
                return True

        if not is_searching:
            if event.keyval in (Gdk.KEY_slash, Gdk.KEY_i):
                self.search_entry.grab_focus()
                self.set_insert_mode()
                return True
            elif event.keyval == Gdk.KEY_q:
                self.result = None
                self.close()
                return True
            elif event.keyval == Gdk.KEY_j:
                self.flow.child_focus(Gtk.DirectionType.DOWN)
                return True
            elif event.keyval == Gdk.KEY_k:
                self.flow.child_focus(Gtk.DirectionType.UP)
                return True
            elif event.keyval == Gdk.KEY_h:
                self.flow.child_focus(Gtk.DirectionType.LEFT)
                return True
            elif event.keyval == Gdk.KEY_l:
                self.flow.child_focus(Gtk.DirectionType.RIGHT)
                return True

        return False


def main():
    parser = argparse.ArgumentParser(description="Polyomino Bento Menu & Application Launcher")
    parser.add_argument("--drun", action="store_true", help="Launch desktop application grid")
    parser.add_argument("--title", default="POLYOMINO // APP LAUNCHER")
    parser.add_argument("--subtitle", default="Arch Linux · SwayFX")
    parser.add_argument("--badge", default="DRAWER")
    parser.add_argument("--columns", type=int, default=3)
    parser.add_argument("--width", type=int, default=980)
    parser.add_argument("--height", type=int, default=560)
    parser.add_argument("--info", action="store_true", help="Display only mode")
    args = parser.parse_args()

    # Toggle behavior: if already running, close existing and exit
    try:
        current_pid = str(os.getpid())
        check_pattern = "polyomino-tilemenu.*--drun" if args.drun else "polyomino-tilemenu"
        check_res = subprocess.run(["pgrep", "-f", check_pattern], capture_output=True, text=True)
        if check_res.returncode == 0:
            pids = [p for p in check_res.stdout.strip().split() if p and p != current_pid]
            if pids:
                for pid in pids:
                    try:
                        subprocess.run(["kill", pid], check=False)
                    except Exception:
                        pass
                return 0
    except Exception:
        pass

    if args.drun:
        tiles = get_installed_desktop_apps()
        title = "POLYOMINO // APP LAUNCHER"
        subtitle = "Arch Linux · SwayFX"
        badge = "DRAWER"
        columns = 3
    else:
        try:
            tiles = json.load(sys.stdin)
        except Exception:
            tiles = []
        title = args.title
        subtitle = args.subtitle
        badge = args.badge
        columns = args.columns

    win = BentoTileMenu(
        title_text=title,
        subtitle_text=subtitle,
        badge_text=badge,
        tiles=tiles,
        columns=columns,
        width=args.width,
        height=args.height,
        info_only=args.info,
        is_drun=args.drun
    )
    win.show_all()
    Gtk.main()

    if win.result:
        print(win.result)
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
