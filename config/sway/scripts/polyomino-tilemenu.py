#!/usr/bin/env python3
"""
Polyomino tile menu — a generic GTK grid picker used by the wofi-replacement
pickers (polyomino menu / theme-picker / wallpaper-picker / whichkey).

Reads a JSON array of tiles from stdin:
    [{"id": "...", "icon": "...", "title": "...", "desc": "...",
      "accent": "violet|blue|teal|green|yellow|peach|red|mauve|sapphire|...",
      "badge": "..."}, ...]

Prints the selected tile's id to stdout and exits 0. Prints nothing and
exits 1 if the window is closed/cancelled (Esc). With --info, tiles are
inert (no selection is made; any click or Esc just closes the window).
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path

import gi
gi.require_version('Gtk', '3.0')
from gi.repository import Gtk, Gdk, GLib

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
        button.tile-{name} {{ border: 1px solid {hexval}; }}
        button.tile-{name}:hover {{ border-color: {hexval}; background-color: {p['surface1']}; }}
        button.tile-{name}:active {{ background-color: {p['surface2']}; }}
        .tile-badge-{name} {{ background-color: alpha({hexval}, 0.18); color: {hexval}; }}
        .tile-icon-{name} {{ color: {hexval}; }}
        """)

    return f"""
    * {{
        font-family: 'JetBrainsMono Nerd Font', 'JetBrains Mono', monospace;
    }}
    window, window.background, .background {{
        background-color: transparent;
        color: {p['text']};
        border: none;
    }}
    scrolledwindow, scrolledwindow viewport, viewport, flowboxchild {{
        background-color: transparent;
        color: {p['text']};
        border: none;
    }}
    .menu-window {{
        background-color: alpha({p['base']}, 0.72);
        border: 1px solid {p['accent']};
    }}
    .menu-title {{
        font-size: 13px;
        font-weight: 700;
        letter-spacing: 0.5px;
        color: {p['subtext0']};
    }}
    button.polyomino-tile {{
        background-color: {p['surface0']};
        background-image: none;
        border-radius: 6px;
        padding: 12px;
        box-shadow: none;
        text-shadow: none;
        transition: all 120ms ease-in-out;
    }}
    .tile-icon {{
        font-size: 20px;
    }}
    .tile-badge {{
        font-size: 9px;
        font-weight: 700;
        padding: 2px 7px;
        border-radius: 4px;
    }}
    .tile-title {{
        font-size: 13px;
        font-weight: 700;
        color: {p['text']};
    }}
    .tile-desc {{
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

        self.set_app_paintable(True)
        screen = Gdk.Screen.get_default()
        visual = screen.get_rgba_visual() if screen else None
        if visual is not None:
            self.set_visual(visual)

        self.apply_css()

        outer = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        outer.get_style_context().add_class("menu-window")
        self.add(outer)

        header = Gtk.Label(label=title, xalign=0)
        header.get_style_context().add_class("menu-title")
        header.set_margin_top(14)
        header.set_margin_start(18)
        header.set_margin_bottom(6)
        outer.pack_start(header, False, False, 0)

        scrolled = Gtk.ScrolledWindow()
        scrolled.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        scrolled.set_shadow_type(Gtk.ShadowType.NONE)

        flow = Gtk.FlowBox()
        flow.set_valign(Gtk.Align.START)
        flow.set_max_children_per_line(columns)
        flow.set_min_children_per_line(1)
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_row_spacing(10)
        flow.set_column_spacing(10)
        flow.set_homogeneous(True)
        flow.set_margin_start(14)
        flow.set_margin_end(14)
        flow.set_margin_bottom(14)

        for t in tiles:
            flow.add(self.make_tile(t))

        scrolled.add(flow)
        outer.pack_start(scrolled, True, True, 0)

        self.connect("key-press-event", self.on_key)
        self.connect("destroy", lambda w: Gtk.main_quit())

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
        btn = Gtk.Button()
        btn.set_relief(Gtk.ReliefStyle.NONE)
        btn.get_style_context().add_class("polyomino-tile")
        btn.get_style_context().add_class(f"tile-{accent}")

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)

        top = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        icon = Gtk.Label(label=t.get("icon", "▢"))
        icon.get_style_context().add_class("tile-icon")
        icon.get_style_context().add_class(f"tile-icon-{accent}")
        top.pack_start(icon, False, False, 0)

        if t.get("badge"):
            badge = Gtk.Label(label=t["badge"])
            badge.get_style_context().add_class("tile-badge")
            badge.get_style_context().add_class(f"tile-badge-{accent}")
            top.pack_end(badge, False, False, 0)

        box.pack_start(top, False, False, 0)

        title_lbl = Gtk.Label(label=t.get("title", ""), xalign=0)
        title_lbl.set_line_wrap(True)
        title_lbl.get_style_context().add_class("tile-title")
        box.pack_start(title_lbl, False, False, 0)

        if t.get("desc"):
            desc_lbl = Gtk.Label(label=t["desc"], xalign=0)
            desc_lbl.set_line_wrap(True)
            desc_lbl.get_style_context().add_class("tile-desc")
            box.pack_start(desc_lbl, False, False, 0)

        btn.add(box)
        tid = t.get("id")
        btn.connect("clicked", lambda b: self.select(tid))
        return btn

    def select(self, tid):
        if not self.info_only:
            self.result = tid
        self.close()

    def on_key(self, widget, event):
        if event.keyval == Gdk.KEY_Escape:
            self.result = None
            self.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--title", default="[ ⊞ ] polyomino")
    parser.add_argument("--columns", type=int, default=3)
    parser.add_argument("--width", type=int, default=620)
    parser.add_argument("--height", type=int, default=420)
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
