#!/usr/bin/env python3
"""
Polyomino tile menu — a generic GTK bento-card grid picker used by every
Wayland/SwayFX menu (App Launcher, Which-Key cheatsheet, Theme Selector,
Wallpaper picker, Projects picker) in place of a plain wofi dmenu list, in
the same visual language as the Welcome Center.

Reads a JSON array of tiles from stdin:
    [{"id": "...", "icon": "...", "title": "...", "desc": "...",
      "accent": "violet|blue|teal|green|yellow|peach|red|mauve|sapphire|...",
      "badge": "...", "variant": "card|square"}, ...]

Includes a dynamic, Vim-inspired footer hint bar that switches between
[NORMAL] and [INSERT] modes dynamically when searching/filtering vs. navigating.
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path

import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk, Gdk, GLib, Pango

GLib.set_prgname("polyomino-tilemenu")

CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "polyomino"
TOKENS_FILE = CONFIG_DIR / "theme" / "tokens.css"

DEFAULT_PALETTE = {
    "base": "#0D1117", "mantle": "#161B22", "crust": "#010409",
    "surface0": "#1C2128", "surface1": "#21262D", "surface2": "#2D333B",
    "overlay0": "#30363D", "text": "#F0F6FC", "subtext0": "#8B949E",
    "subtext1": "#C9D1D9", "accent": "#8B5CF6", "blue": "#8B5CF6",
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
        button.tile-{name} {{ border: 1px solid alpha({hexval}, 0.4); }}
        button.tile-{name}:hover, button.tile-{name}:focus {{
            border: 1px solid {hexval};
            background-color: alpha(#FFFFFF, 0.05);
        }}
        button.tile-{name}:active {{ background-color: alpha({hexval}, 0.12); }}
        .tile-badge-{name} {{
            background-color: alpha({hexval}, 0.15);
            border: 1px solid alpha({hexval}, 0.4);
            color: {hexval};
        }}
        .tile-icon-{name} {{ color: {hexval}; }}
        """)

    return f"""
    * {{
        font-family: 'JetBrainsMono Nerd Font', 'JetBrains Mono', monospace;
        outline: none;
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
        background-color: alpha({p['base']}, 0.95);
        border: 1px solid alpha({p['accent']}, 0.4);
        border-radius: 8px;
    }}
    .menu-header-box {{
        padding: 12px 16px 6px 16px;
    }}
    .menu-title {{
        font-size: 13px;
        font-weight: 700;
        letter-spacing: 0.5px;
        color: {p['subtext0']};
    }}
    .search-input {{
        background-color: {p['mantle']};
        color: {p['text']};
        border: 1px solid alpha({p['accent']}, 0.4);
        border-radius: 6px;
        padding: 6px 10px;
        margin: 4px 14px 10px 14px;
        font-size: 12px;
    }}
    .search-input:focus {{
        border: 1px solid {p['accent']};
    }}
    button.polyomino-tile {{
        background-color: {p['surface0']};
        background-image: none;
        border-radius: 6px;
        padding: 8px 12px;
        box-shadow: none;
        text-shadow: none;
        transition: all 100ms ease-in-out;
    }}
    button.polyomino-tile.tile-card {{
        min-height: 34px;
    }}
    button.polyomino-tile.tile-square {{
        min-height: 38px;
        padding: 8px 10px;
    }}
    .tile-icon {{
        font-size: 15px;
    }}
    .tile-square .tile-icon {{
        font-size: 22px;
    }}
    .tile-badge {{
        font-size: 10px;
        font-weight: 700;
        padding: 3px 8px;
        border-radius: 4px;
    }}
    .tile-title {{
        font-size: 12px;
        font-weight: 700;
        color: {p['text']};
    }}
    .tile-square .tile-title {{
        font-size: 12px;
    }}
    .tile-desc {{
        font-size: 9px;
        color: {p['subtext0']};
    }}
    .footer-bar {{
        background-color: {p['mantle']};
        border-top: 1px solid alpha({p['accent']}, 0.3);
        border-radius: 0px 0px 8px 8px;
        padding: 6px 14px;
    }}
    .mode-badge-normal {{
        background-color: #a855f7;
        color: {p['base']};
        font-size: 10px;
        font-weight: 700;
        border-radius: 4px;
        padding: 2px 6px;
    }}
    .mode-badge-insert {{
        background-color: #22d3ee;
        color: {p['base']};
        font-size: 10px;
        font-weight: 700;
        border-radius: 4px;
        padding: 2px 6px;
    }}
    .footer-hints {{
        font-size: 10px;
        color: {p['subtext0']};
    }}
    scrollbar {{ background-color: transparent; }}
    scrollbar slider {{
        background-color: {p['overlay0']};
        border-radius: 4px;
        min-width: 6px;
        min-height: 6px;
    }}
    scrollbar slider:hover {{ background-color: {p['accent']}; }}
    {''.join(tile_rules)}
    """


class TileMenu(Gtk.Window):
    def __init__(self, title, tiles, columns, width, height, info_only):
        super().__init__(title=title)
        self.set_role("polyomino-tilemenu")
        self.set_decorated(False)
        self.set_default_size(width, height)
        self.set_position(Gtk.WindowPosition.CENTER)
        self.result = None
        self.info_only = info_only
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

        # Header
        header_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=0)
        header_box.get_style_context().add_class("menu-header-box")
        header = Gtk.Label(label=title, xalign=0)
        header.get_style_context().add_class("menu-title")
        header_box.pack_start(header, True, True, 0)
        outer.pack_start(header_box, False, False, 0)

        # Search bar
        self.search_entry = Gtk.SearchEntry()
        self.search_entry.set_placeholder_text("Search...")
        self.search_entry.get_style_context().add_class("search-input")
        self.search_entry.connect("search-changed", self.on_search_changed)
        self.search_entry.connect("focus-in-event", self.on_search_focus_in)
        self.search_entry.connect("focus-out-event", self.on_search_focus_out)
        outer.pack_start(self.search_entry, False, False, 0)

        # Flow grid
        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_shadow_type(Gtk.ShadowType.NONE)

        self.flow = Gtk.FlowBox()
        self.flow.set_valign(Gtk.Align.START)
        self.flow.set_max_children_per_line(columns)
        self.flow.set_min_children_per_line(1)
        self.flow.set_selection_mode(Gtk.SelectionMode.NONE)
        self.flow.set_row_spacing(9)
        self.flow.set_column_spacing(9)
        self.flow.set_homogeneous(True)
        self.flow.set_margin_start(14)
        self.flow.set_margin_end(14)
        self.flow.set_margin_bottom(10)
        self.flow.set_filter_func(self.filter_tile)

        self.first_tile = None
        self.tile_widgets = []
        for t in tiles:
            tile = self.make_tile(t)
            tile._tile_data = t
            self.tile_widgets.append(tile)
            if self.first_tile is None:
                self.first_tile = tile
            self.flow.add(tile)

        scrolled.add(self.flow)
        outer.pack_start(scrolled, True, True, 0)

        # Vim-inspired Footer Status Bar
        self.footer_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        self.footer_box.get_style_context().add_class("footer-bar")

        self.mode_badge = Gtk.Label(label="[NORMAL]")
        self.mode_badge.get_style_context().add_class("mode-badge-normal")
        self.footer_box.pack_start(self.mode_badge, False, False, 0)

        self.mode_hints = Gtk.Label(label="h/j/k/l Navigate  •  / or i Search  •  Enter Select  •  Esc/q Quit", xalign=0)
        self.mode_hints.get_style_context().add_class("footer-hints")
        self.footer_box.pack_start(self.mode_hints, True, True, 0)

        outer.pack_start(self.footer_box, False, False, 0)

        self.connect("key-press-event", self.on_key)
        self.connect("destroy", lambda w: Gtk.main_quit())
        self.connect("map-event", self.on_map)

    def set_normal_mode(self):
        ctx = self.mode_badge.get_style_context()
        ctx.remove_class("mode-badge-insert")
        ctx.add_class("mode-badge-normal")
        self.mode_badge.set_text("[NORMAL]")
        self.mode_hints.set_text("h/j/k/l Navigate  •  / or i Search  •  Enter Select  •  Esc/q Quit")

    def set_insert_mode(self):
        ctx = self.mode_badge.get_style_context()
        ctx.remove_class("mode-badge-normal")
        ctx.add_class("mode-badge-insert")
        self.mode_badge.set_text("[INSERT]")
        self.mode_hints.set_text("Type to filter  •  Enter Launch  •  Esc Normal Mode")

    def on_search_focus_in(self, widget, event):
        self.set_insert_mode()
        return False

    def on_search_focus_out(self, widget, event):
        self.set_normal_mode()
        return False

    def on_search_changed(self, entry):
        self.flow.invalidate_filter()

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

    def make_tile(self, t):
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

        icon_text = t.get("icon", "")

        if variant == "square":
            box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
            box.set_valign(Gtk.Align.CENTER)

            if icon_text:
                icon = Gtk.Label(label=icon_text)
                icon.set_valign(Gtk.Align.CENTER)
                icon.get_style_context().add_class("tile-icon")
                icon.get_style_context().add_class(f"tile-icon-{accent}")
                box.pack_start(icon, False, False, 0)

            text_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=1)
            text_box.set_valign(Gtk.Align.CENTER)

            title_lbl = Gtk.Label(label=t.get("title", ""), xalign=0)
            title_lbl.set_ellipsize(Pango.EllipsizeMode.END)
            title_lbl.set_hexpand(True)
            title_lbl.get_style_context().add_class("tile-title")
            text_box.pack_start(title_lbl, False, False, 0)

            if t.get("desc"):
                desc_lbl = Gtk.Label(label=t["desc"], xalign=0)
                desc_lbl.set_ellipsize(Pango.EllipsizeMode.END)
                desc_lbl.get_style_context().add_class("tile-desc")
                text_box.pack_start(desc_lbl, False, False, 0)

            box.pack_start(text_box, True, True, 0)
        else:
            box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
            box.set_valign(Gtk.Align.CENTER)

            if icon_text:
                icon = Gtk.Label(label=icon_text)
                icon.set_valign(Gtk.Align.CENTER)
                icon.get_style_context().add_class("tile-icon")
                icon.get_style_context().add_class(f"tile-icon-{accent}")
                box.pack_start(icon, False, False, 0)

            title_lbl = Gtk.Label(label=t.get("title", ""), xalign=0)
            title_lbl.set_ellipsize(Pango.EllipsizeMode.END)
            title_lbl.set_hexpand(True)
            title_lbl.set_valign(Gtk.Align.CENTER)
            title_lbl.get_style_context().add_class("tile-title")
            box.pack_start(title_lbl, True, True, 0)

            if t.get("badge"):
                badge = Gtk.Label(label=t["badge"])
                badge.set_valign(Gtk.Align.CENTER)
                badge.get_style_context().add_class("tile-badge")
                badge.get_style_context().add_class(f"tile-badge-{accent}")
                box.pack_end(badge, False, False, 0)

        btn.add(box)
        tid = t.get("id")
        btn.connect("clicked", lambda b: self.select(tid))
        return btn

    def select(self, tid):
        if not self.info_only:
            self.result = tid
        self.close()

    def on_key(self, widget, event):
        focused = self.get_focus()
        is_searching = focused == self.search_entry

        if event.keyval == Gdk.KEY_Escape:
            if is_searching:
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
            # Vim navigation in Normal mode
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
    parser = argparse.ArgumentParser()
    parser.add_argument("--title", default="[ ⊞ ] polyomino")
    parser.add_argument("--columns", type=int, default=3)
    parser.add_argument("--width", type=int, default=620)
    parser.add_argument("--height", type=int, default=440)
    parser.add_argument("--info", action="store_true", help="display only, no selection is returned")
    args = parser.parse_args()

    try:
        tiles = json.load(sys.stdin)
    except Exception as e:
        print(f"invalid tiles JSON on stdin: {e}", file=sys.stderr)
        return 1

    win = TileMenu(args.title, tiles, args.columns, args.width, args.height, args.info)
    win.show_all()
    Gtk.main()

    if win.result:
        print(win.result)
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
