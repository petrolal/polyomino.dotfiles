#!/usr/bin/env python3
"""
Polyomino Terminal Screen Saver:
Falling Horizontal Cigarettes with drifting smoke, Glowing Candles with warm flames,
Golden Euro coins with jackpot bursts, Java coffee cups, and Kotlin geometric prisms.
Features discreet drifting clouds and a clean dot-matrix background grid.
Theme-aware (adapts dynamically to active Polyomino palette).
Exits cleanly on any keypress.
"""

import os
import sys
import time
import random
import signal
import tty
import termios
import select
from pathlib import Path

# --- Theme Detection ---
def get_theme_colors():
    colors = {
        "base": (15, 17, 23),
        "mantle": (25, 28, 36),
        "text": (248, 250, 252),
        "accent": (235, 180, 52),
        "red": (239, 68, 68),
        "green": (16, 185, 129),
        "yellow": (245, 158, 11),
        "blue": (59, 130, 246),
        "smoke": (150, 160, 175),
        "coffee": (180, 100, 40),
        "ash": (100, 105, 115),
        "white": (255, 255, 255),
        "orange": (249, 115, 22),
        "cherry": (255, 75, 43),
    }
    tokens_file = Path.home() / ".config" / "polyomino" / "theme" / "tokens.css"
    if tokens_file.exists():
        try:
            for line in tokens_file.read_text().splitlines():
                line = line.strip()
                if line.startswith("@define-color"):
                    parts = line.split()
                    if len(parts) >= 3:
                        name = parts[1].strip()
                        hex_val = parts[2].rstrip(";").lstrip("#")
                        if len(hex_val) == 6:
                            r = int(hex_val[0:2], 16)
                            g = int(hex_val[2:4], 16)
                            b = int(hex_val[4:6], 16)
                            colors[name] = (r, g, b)
        except Exception:
            pass
    return colors

COLORS = get_theme_colors()

# Compact horizontal cigarette with rising Braille smoke plume, amber filter, and burning ember (15x6)
CIGARETTE_ART_BRAILLE = [
    "          ⠙⢿⣿⠟",
    "           ⠹⣿⠟ ",
    "            ⣸⠟  ",
    "⢀⣤⡄ ⣤⣤⣤⣤⣤⣤⣤⣤ ⣄⢁",
    "⢸⣿⡇ ⣿⣿⣿⣿⣿⣿⣿⣿ ⣿⢸",
    "⠈⠛⠃ ⠛⠛⠛⠛⠛⠛⠛⠛ ⠋⠈",
]

# Atmospheric candle with flickering flame, molten pool, and dripping wax
CANDLE_FLAME_FRAMES = [
    ("  (    ", "  )\\   "),
    ("   )   ", "  |(   "),
    ("    )  ", "   /(  "),
    ("  ( )  ", "  )|   "),
]
CANDLE_BODY = [
    "  {_}  ",
    " .-;-. ",
    "|'-=-'|",
    "|     |",
    "|     |",
    "|     |",
    "|     |",
    "'.___.'",
]

# Compact Sun / Oracle Java Duke coffee cup generated from ~/Downloads/java.png (14x7)
JAVA_ART_SMALL = [
    "        ==    ",
    "     ====     ",
    "    == ==     ",
    "    == ==    +",
    "  +++++++++ ++",
    "++ +++++++    ",
    "+++++++++++++ ",
]

# Compact JetBrains Kotlin logo with truecolor gradient generated from ~/Downloads/kotlin.png (10x5)
KOTLIN_COLORS_SMALL = [
    [(123, 167, 224), (123, 157, 229), (135, 151, 236), (170, 141, 195), (217, 140, 111), (238, 140, 71), (236, 134, 63), (236, 134, 61), (240, 169, 119), (249, 225, 209)],
    [(84, 186, 229), (113, 164, 205), (184, 141, 140), (230, 136, 79), (244, 137, 58), (242, 140, 63), (240, 164, 111), (247, 216, 195), None, None],
    [(142, 147, 168), (186, 124, 119), (210, 122, 95), (212, 128, 97), (197, 132, 123), (215, 185, 194), None, None, None, None],
    [(161, 109, 144), (162, 112, 138), (156, 132, 156), (140, 149, 196), (131, 152, 231), (140, 145, 236), (174, 164, 233), (222, 216, 243), None, None],
    [(119, 127, 180), (105, 157, 202), (100, 172, 228), (110, 166, 230), (128, 159, 223), (138, 152, 223), (141, 138, 226), (147, 134, 228), (179, 169, 234), None],
]

# Compact stylized Euro sign (€) (12x7)
EURO_ART = [
    "   .d8888b. ",
    "  d88P  Y88b",
    "============",
    "  888       ",
    "============",
    "  Y88b. d88P",
    '   "Y8888P" ',
]

def fg(col):
    if isinstance(col, str):
        return col
    return f"\033[38;2;{col[0]};{col[1]};{col[2]}m"

def bold(col):
    if isinstance(col, str):
        return f"\033[1m{col}"
    return f"\033[1;38;2;{col[0]};{col[1]};{col[2]}m"

RESET = "\033[0m"

# --- Particle / Impact Classes ---
class Particle:
    def __init__(self, x, y, char, color, vx=0.0, vy=0.0, life=10):
        self.x = float(x)
        self.y = float(y)
        self.char = char
        self.color = color
        self.vx = vx
        self.vy = vy
        self.life = life
        self.max_life = life

    def update(self):
        self.x += self.vx
        self.y += self.vy
        self.life -= 1
        return self.life > 0

class ImpactAnimation:
    def __init__(self, x, y, kind):
        self.x = int(x)
        self.y = int(y)
        self.kind = kind
        self.frame = 0
        self.particles = []
        self.finished = False

        # Soft, calm settling dust (2-3 tiny subtle dots, very short-lived)
        col = COLORS["smoke"] if kind == "cigarette" else COLORS["ash"]
        for _ in range(3):
            vx = random.uniform(-0.25, 0.25)
            vy = random.uniform(-0.25, -0.05)
            if kind == "cigarette":
                offset_x = random.randint(2, 12)
            elif kind == "candle":
                offset_x = random.randint(1, 5)
            else:
                offset_x = 4
            self.particles.append(Particle(self.x + offset_x, self.y, "·", col, vx, vy, life=random.randint(4, 7)))

    def update(self):
        self.frame += 1
        alive_particles = []
        for p in self.particles:
            if p.update():
                alive_particles.append(p)
        self.particles = alive_particles
        if self.frame > 8 or not self.particles:
            self.finished = True
        return not self.finished

    def draw(self, canvas, w, h):
        # Clean, quiet settling dust
        for p in self.particles:
            px = int(p.x)
            py = int(p.y)
            if 0 <= px < w and 0 <= py < h:
                canvas[py][px] = (p.char, p.color)

# --- Discreet Cloud System ---
CLOUD_PRESETS = [
    [
        "   .---.   ",
        " .-(  ~  )-.",
        "(___.__.__) ",
    ],
    [
        "  .--.  ",
        " (  · ) ",
        "(___.__)",
    ],
    [
        "   .------.   ",
        " .-(  ..  )-. ",
        "(___.__.__.__)",
    ],
    [
        "  .---.  ",
        " (     ) ",
        "(___.__) ",
    ],
]

class Cloud:
    def __init__(self, x, y, shape_idx=None):
        self.x = float(x)
        self.y = int(y)
        self.shape_idx = shape_idx if shape_idx is not None else random.randint(0, len(CLOUD_PRESETS) - 1)
        self.shape = CLOUD_PRESETS[self.shape_idx]
        self.w = max(len(row) for row in self.shape)
        self.h = len(self.shape)
        self.vx = random.uniform(0.04, 0.08)

    def update(self, max_w):
        self.x += self.vx
        if self.x > max_w + 2:
            self.x = -float(self.w) - random.randint(2, 10)
            self.y = random.choice([1, 2])
            self.vx = random.uniform(0.04, 0.08)
            self.shape = random.choice(CLOUD_PRESETS)
            self.w = max(len(row) for row in self.shape)
            self.h = len(self.shape)

    def draw(self, canvas, w, h):
        col_rim = (175, 190, 210)    # Soft slate-white cloud rim
        col_base = COLORS["smoke"]    # Soft smoke cloud base
        ix = int(self.x)
        for r_idx, row in enumerate(self.shape):
            ty = self.y + r_idx
            if not (0 <= ty < h):
                continue
            first = None
            last = None
            for c_idx, ch in enumerate(row):
                if ch != " ":
                    if first is None:
                        first = c_idx
                    last = c_idx
            if first is None:
                continue
            for c_idx in range(first, last + 1):
                tx = ix + c_idx
                if 0 <= tx < w:
                    ch = row[c_idx]
                    col = col_base if r_idx == self.h - 1 else (col_rim if ch in (".", "-", "'", "`", "^") else col_base)
                    canvas[ty][tx] = (ch, col)

# --- Abstract Theme-Aware Heap System ---
class HeapCell:
    def __init__(self, char, color, kind):
        self.char = char
        self.color = color
        self.kind = kind
        self.age = 0

class AbstractHeap:
    def __init__(self, w, max_height=8):
        self.w = w
        self.max_height = max_height
        self.columns = [[] for _ in range(w)]

    def resize(self, new_w):
        if new_w > len(self.columns):
            self.columns.extend([[] for _ in range(new_w - len(self.columns))])
        elif new_w < len(self.columns):
            self.columns = self.columns[:new_w]
        self.w = new_w

    def get_surface_y(self, x, span_w, floor_y):
        start = max(0, int(x))
        end = min(self.w, int(x + span_w))
        if start >= end:
            return floor_y
        max_h = max((len(self.columns[c]) for c in range(start, end)), default=0)
        return floor_y - max_h

    def deposit(self, x, span_w, kind):
        cx = int(x + span_w / 2)
        radius = max(3, int(span_w // 2) + 2)
        start = max(0, cx - radius)
        end = min(self.w, cx + radius)

        # Deposit abstract matter in an undulating mound
        for c in range(start, end):
            dist = abs(c - cx)
            if dist <= radius // 3:
                count = 2
            elif dist <= (radius * 2) // 3:
                count = 1 if random.random() < 0.85 else 0
            else:
                count = 1 if random.random() < 0.4 else 0

            for _ in range(count):
                cell = self._make_cell(c, kind)
                self.columns[c].append(cell)

        self.settle()

    def settle(self):
        # Sandpile avalanche: allow tall columns to tumble into adjacent lower columns
        for _ in range(3):
            changed = False
            for c in range(self.w):
                if c > 0 and len(self.columns[c]) - len(self.columns[c - 1]) >= 2:
                    cell = self.columns[c].pop()
                    self.columns[c - 1].append(cell)
                    changed = True
                if c < self.w - 1 and len(self.columns[c]) - len(self.columns[c + 1]) >= 2:
                    cell = self.columns[c].pop()
                    self.columns[c + 1].append(cell)
                    changed = True
            if not changed:
                break

        # Cap height gracefully by dissolving bottom bedrock (compression)
        for c in range(self.w):
            while len(self.columns[c]) > self.max_height:
                self.columns[c].pop(0)

    def _make_cell(self, col, kind):
        accent = COLORS["accent"]
        ash = COLORS["ash"]
        yellow = COLORS["yellow"]
        text = COLORS["text"]

        if kind == "cigarette":
            # Abstract ash compaction & smoldering carbon
            char = random.choice(["█", "▓", "▒", "░", "■", "▪", "·", "*", "~"])
            col = random.choice([ash, accent, COLORS["orange"], (110, 100, 95)])
        elif kind == "euro":
            # Abstract gilded currency lattice & bullion runes
            char = random.choice(["█", "▓", "■", "▰", "═", "◆", "✦", "€", "▫"])
            col = random.choice([bold(accent), yellow, accent, (255, 215, 0), text])
        elif kind == "java":
            # Abstract espresso density & ceramic facets
            char = random.choice(["▓", "▒", "░", "▅", "▃", "≈", "▲", "•", "■"])
            col = random.choice([accent, COLORS["coffee"], (135, 80, 40), COLORS["white"]])
        elif kind == "candle":
            # Abstract melted wax pools, drips & golden wick glow
            char = random.choice(["░", "▒", "▄", "▅", "~", "•", "▪", "🕯", ")"])
            col = random.choice([accent, (250, 240, 220), yellow, COLORS["white"]])
        else:  # kotlin
            # Abstract prismatic crystalline geode
            char = random.choice(["▲", "▼", "◆", "◇", "◈", "❖", "◤", "◢", "█", "⬢"])
            kotlin_palette = [accent, (127, 82, 255), (248, 143, 56), (84, 186, 229), text]
            col = random.choice(kotlin_palette)
        return HeapCell(char, col, kind)

    def update(self, smoke_list, floor_y):
        for c in range(self.w):
            col_cells = self.columns[c]
            for idx, cell in enumerate(col_cells):
                cell.age += 1
                if cell.kind == "cigarette" and cell.color in (COLORS["cherry"], COLORS["orange"]):
                    if cell.age > 150:
                        cell.color = COLORS["ash"]

            # Surface cell can emit a subtle wisp of smoke if it is a smoldering cigarette
            if col_cells and col_cells[-1].kind == "cigarette" and random.random() < 0.025:
                top_y = floor_y - len(col_cells)
                smoke_list.append(Particle(
                    c,
                    top_y,
                    random.choice(["~", "◦", "°", "˙"]),
                    COLORS["smoke"],
                    vx=random.uniform(-0.1, 0.1),
                    vy=random.uniform(-0.35, -0.15),
                    life=random.randint(10, 16)
                ))

    def draw(self, canvas, floor_y, h):
        base = COLORS["base"]
        accent = COLORS["accent"]
        bedrock_col = (
            max(20, int(base[0] * 0.6 + accent[0] * 0.3)),
            max(22, int(base[1] * 0.6 + accent[1] * 0.3)),
            max(28, int(base[2] * 0.6 + accent[2] * 0.3)),
        )

        for c in range(self.w):
            col_cells = self.columns[c]
            total_h = len(col_cells)
            for idx, cell in enumerate(col_cells):
                py = floor_y - idx
                if not (0 <= py < h):
                    continue
                if idx == 0 and total_h >= 4:
                    draw_col = bedrock_col
                elif idx == total_h - 1 and random.random() < 0.08:
                    draw_col = bold(cell.color)
                else:
                    draw_col = cell.color
                canvas[py][c] = (cell.char, draw_col)


# --- Falling Objects ---
class FallingEntity:
    def __init__(self, x, kind, floor_y):
        self.x = x
        self.kind = kind
        self.floor_y = floor_y
        self.smoke_particles = []
        if self.kind == "java":
            self.w = 14
            self.h = 7
            self.vy = random.uniform(0.32, 0.50)
        elif self.kind == "kotlin":
            self.w = 10
            self.h = 5
            self.vy = random.uniform(0.35, 0.55)
        elif self.kind == "euro":
            self.w = 12
            self.h = 7
            self.vy = random.uniform(0.35, 0.55)
        elif self.kind == "candle":
            self.w = 7
            self.h = 10
            self.vy = random.uniform(0.30, 0.46)
        else:  # cigarette (compact horizontal braille)
            self.w = 15
            self.h = 6
            self.vy = random.uniform(0.32, 0.48)
        self.y = -float(self.h)

    def update(self, heap):
        self.y += self.vy

        # Generate ongoing smoke/steam/sparkle trails while falling
        if self.kind == "cigarette":
            if random.random() < 0.75:
                vx = random.uniform(-0.15, 0.15)
                vy = random.uniform(-0.45, -0.15)
                char = random.choice(["~", "(", "◦", "°", ")"])
                if random.random() < 0.30:
                    spark_col = random.choice([COLORS["cherry"], COLORS["orange"], COLORS["yellow"]])
                    self.smoke_particles.append(Particle(self.x + 13 + random.randint(0, 1), self.y + 4, "·", spark_col, vx * 0.5, vy * 0.5, life=10))
                else:
                    self.smoke_particles.append(Particle(self.x + 10 + random.randint(0, 3), self.y, char, COLORS["smoke"], vx, vy, life=14))

        elif self.kind == "euro":
            if random.random() < 0.75:
                vx = random.uniform(-0.15, 0.15)
                vy = random.uniform(-0.35, -0.1)
                char = random.choice(["✦", "✧", "⋆", "•", "€"])
                col = random.choice([COLORS["yellow"], COLORS["accent"]])
                self.smoke_particles.append(Particle(self.x + random.randint(1, 10), self.y + random.randint(0, 3), char, col, vx, vy, life=10))

        elif self.kind == "candle":
            if random.random() < 0.65:
                vx = random.uniform(-0.10, 0.10)
                vy = random.uniform(-0.35, -0.12)
                if random.random() < 0.40:
                    char = random.choice(["✦", "·", "°", "⋆", "•"])
                    col = random.choice([COLORS["yellow"], COLORS["orange"], (255, 245, 190)])
                    self.smoke_particles.append(Particle(self.x + random.randint(2, 4), self.y - 0.5, char, col, vx, vy, life=10))
                else:
                    char = random.choice(["~", "(", "◦", ")"])
                    self.smoke_particles.append(Particle(self.x + random.randint(2, 4), self.y - 1, char, COLORS["smoke"], vx, vy, life=14))

        elif self.kind == "java":
            if random.random() < 0.85:
                vx = random.uniform(-0.2, 0.2)
                vy = random.uniform(-0.5, -0.2)
                char = random.choice(["~", "(", ")", "=", "-"])
                col = random.choice([COLORS["cherry"], COLORS["orange"], COLORS["smoke"]])
                self.smoke_particles.append(Particle(self.x + 6, self.y, char, col, vx, vy, life=14))

        # Update existing smoke particles
        alive_smoke = []
        for p in self.smoke_particles:
            if p.update():
                alive_smoke.append(p)
        self.smoke_particles = alive_smoke

        # Check impact against rising heap surface
        surface_y = heap.get_surface_y(self.x, self.w, self.floor_y)
        if self.y + self.h >= surface_y:
            return False, surface_y  # Hit heap surface
        return True, surface_y

    def draw(self, canvas, w, h):
        # Draw smoke particles first
        for p in self.smoke_particles:
            px = int(p.x)
            py = int(p.y)
            if 0 <= px < w and 0 <= py < h:
                canvas[py][px] = (p.char, p.color)

        iy = int(self.y)
        ix = int(self.x)

        if self.kind == "cigarette":
            flicker_phase = int(time.time() * 12)
            flicker_colors = [COLORS["cherry"], COLORS["orange"], COLORS["yellow"]]
            amber = (235, 160, 60)

            for row_idx, line in enumerate(CIGARETTE_ART_BRAILLE):
                target_y = iy + row_idx
                if 0 <= target_y < h:
                    for col_idx, ch in enumerate(line):
                        if ch == " ":
                            continue
                        target_x = ix + col_idx
                        if 0 <= target_x < w:
                            if row_idx <= 2:
                                col = COLORS["smoke"]
                            elif col_idx <= 2:
                                col = amber
                            elif col_idx <= 11:
                                col = COLORS["white"]
                            else:
                                col = bold(flicker_colors[(flicker_phase + col_idx) % 3])
                            canvas[target_y][target_x] = (ch, col)

        elif self.kind == "euro":
            # Euro currency sign with golden crossbars and accent curve
            for row_idx, line in enumerate(EURO_ART):
                target_y = iy + row_idx
                if 0 <= target_y < h:
                    for col_idx, ch in enumerate(line):
                        if ch == " ":
                            continue
                        target_x = ix + col_idx
                        if 0 <= target_x < w:
                            if ch == "=":
                                col = bold(COLORS["yellow"])
                            else:
                                col = bold(COLORS["accent"])
                            canvas[target_y][target_x] = (ch, col)

        elif self.kind == "java":
            # Compact Oracle/Sun Java coffee cup and steam logo
            for row_idx, line in enumerate(JAVA_ART_SMALL):
                target_y = iy + row_idx
                if 0 <= target_y < h:
                    for col_idx, ch in enumerate(line):
                        if ch == " ":
                            continue
                        target_x = ix + col_idx
                        if 0 <= target_x < w:
                            if row_idx <= 1:
                                col = COLORS["cherry"]
                            elif row_idx <= 3:
                                col = bold(COLORS["blue"]) if ch == "+" else COLORS["orange"]
                            elif row_idx == 4:
                                col = bold(COLORS["blue"])
                            elif row_idx == 5:
                                col = COLORS["accent"] if col_idx < 4 else bold(COLORS["blue"])
                            else:
                                col = COLORS["accent"]
                            canvas[target_y][target_x] = (ch, col)

        elif self.kind == "kotlin":
            # Compact JetBrains Kotlin logo generated from ~/Downloads/kotlin.png with truecolor gradient
            for row_idx, row in enumerate(KOTLIN_COLORS_SMALL):
                target_y = iy + row_idx
                if 0 <= target_y < h:
                    for col_idx, rgb in enumerate(row):
                        if rgb is None:
                            continue
                        target_x = ix + col_idx
                        if 0 <= target_x < w:
                            canvas[target_y][target_x] = ("█", rgb)

        elif self.kind == "candle":
            # Atmospheric candle with flickering flame, molten pool, and dripping wax
            flame_phase = int(time.time() * 7 + self.x) % len(CANDLE_FLAME_FRAMES)
            f0, f1 = CANDLE_FLAME_FRAMES[flame_phase]
            candle_lines = [f0, f1] + CANDLE_BODY

            # Wax drip animations sliding down the candle body (rows 5 to 8)
            drip_phase_left = int((time.time() * 2.2 + self.x * 2) % 9)
            drip_phase_right = int((time.time() * 1.8 + self.x * 3 + 4) % 11)

            wax_color = (235, 230, 218)
            wax_highlight = (255, 250, 240)
            wax_drip = (255, 245, 215)
            wick_color = (120, 105, 95)
            molten_glow = bold(COLORS["yellow"])

            flame_glows = [
                (255, 245, 190),
                bold(COLORS["yellow"]),
                COLORS["orange"],
                bold(COLORS["cherry"]),
            ]
            tip_col = flame_glows[flame_phase]

            for row_idx, line in enumerate(candle_lines):
                target_y = iy + row_idx
                if 0 <= target_y < h:
                    for col_idx, ch in enumerate(line):
                        if ch == " ":
                            # Dynamic wax droplet dripping down the side walls
                            if 5 <= row_idx <= 8:
                                if col_idx == 1 and (row_idx - 5) == drip_phase_left:
                                    target_x = ix + col_idx
                                    if 0 <= target_x < w:
                                        canvas[target_y][target_x] = (":", wax_drip)
                                    continue
                                elif col_idx == 5 and (row_idx - 5) == drip_phase_right:
                                    target_x = ix + col_idx
                                    if 0 <= target_x < w:
                                        canvas[target_y][target_x] = (".", wax_drip)
                                    continue
                            continue

                        target_x = ix + col_idx
                        if 0 <= target_x < w:
                            if row_idx == 0:
                                # Top flickering flame tip
                                col = tip_col
                            elif row_idx == 1:
                                # Flame base & wick
                                if ch in ("\\", "/", "|"):
                                    col = wick_color
                                else:
                                    col = bold(COLORS["yellow"]) if flame_phase % 2 == 0 else COLORS["orange"]
                            elif row_idx == 2:
                                # Molten wax pool {_}
                                col = molten_glow if ch == "_" else wax_color
                            elif row_idx == 3:
                                # Upper rim .-;-.
                                col = wick_color if ch == ";" else wax_highlight
                            elif row_idx == 4:
                                # Wax fringe |'-=-'|
                                if ch in ("-", "="):
                                    col = wax_highlight
                                elif ch == "'":
                                    col = wax_drip
                                else:
                                    col = wax_color
                            elif row_idx == 9:
                                # Pedestal base '.___.'
                                col = (210, 205, 195)
                            else:
                                # Candle body side walls |
                                col = wax_color
                            canvas[target_y][target_x] = (ch, col)

# --- Main Simulation Loop ---
def run_screensaver():
    # Hide cursor
    sys.stdout.write("\033[?25l\033[2J")
    sys.stdout.flush()

    # Save tty settings for non-blocking key read
    is_tty = sys.stdin.isatty()
    old_settings = None
    fd = None
    if is_tty:
        try:
            fd = sys.stdin.fileno()
            old_settings = termios.tcgetattr(fd)
            tty.setcbreak(fd)
        except Exception:
            is_tty = False

    def cleanup(*_):
        if is_tty and old_settings is not None and fd is not None:
            try:
                termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
            except Exception:
                pass
        sys.stdout.write("\033[?25h\033[0m\033[2J\033[H")
        sys.stdout.flush()
        sys.exit(0)

    signal.signal(signal.SIGINT, cleanup)
    signal.signal(signal.SIGTERM, cleanup)

    try:
        entities = []
        impacts = []
        heap_smoke = []
        clouds = []
        kinds = ["cigarette", "euro", "java", "kotlin", "candle"]
        next_spawn = 0
        heap = None

        while True:
            # Check for any user keypress to exit immediately
            if is_tty:
                rlist, _, _ = select.select([sys.stdin], [], [], 0.035)
                if rlist:
                    break
            else:
                time.sleep(0.035)
                rlist, _, _ = select.select([sys.stdin], [], [], 0)
                if rlist:
                    break

            # Get terminal bounds
            try:
                term_cols, term_rows = os.get_terminal_size()
            except Exception:
                term_cols, term_rows = 80, 24

            w = max(40, term_cols)
            h = max(16, term_rows)
            floor_y = h - 1

            # Ensure abstract heap matches terminal geometry
            max_heap_h = min(9, max(4, h // 3))
            if heap is None:
                heap = AbstractHeap(w, max_height=max_heap_h)
            else:
                heap.resize(w)
                heap.max_height = max_heap_h

            # Subtle, clean drafting dot grid for a minimalist modern background
            bg_dot_color = (
                max(25, int(COLORS["base"][0] * 0.5 + COLORS["smoke"][0] * 0.22)),
                max(28, int(COLORS["base"][1] * 0.5 + COLORS["smoke"][1] * 0.22)),
                max(38, int(COLORS["base"][2] * 0.5 + COLORS["smoke"][2] * 0.22)),
            )

            # Create canvas: clean dot grid pattern aligned to terminal cell aspect
            canvas = []
            for y in range(h):
                row = []
                for x in range(w):
                    # Clean dot pattern every 4 cols and 2 rows (aligned to square terminal cell aspect)
                    if (x % 4 == 2) and (y % 2 == 1) and (1 <= y <= floor_y):
                        row.append(("·", bg_dot_color))
                    else:
                        row.append((" ", COLORS["text"]))
                canvas.append(row)

            # Clean discreet header
            header_text = "[ ⊞ ] POLYOMINO KINETIC SCREENSAVER — PRESS ANY KEY TO RESUME [ ⊞ ]"
            if len(header_text) < w:
                hx = (w - len(header_text)) // 2
                for i, ch in enumerate(header_text):
                    canvas[0][hx + i] = (ch, COLORS["smoke"])

            # Update and draw discreet clouds drifting gently across the top of the screen
            target_clouds = max(2, min(5, w // 28))
            if not clouds:
                for i in range(target_clouds):
                    init_x = i * (w // target_clouds) + random.randint(2, 8)
                    clouds.append(Cloud(init_x, random.choice([1, 2]), i % len(CLOUD_PRESETS)))
            elif len(clouds) < target_clouds:
                clouds.append(Cloud(-15 - random.randint(0, 8), random.choice([1, 2])))

            for c in clouds:
                c.update(w)
                c.draw(canvas, w, h)

            # Update and draw abstract rising heap (accumulating theme debris)
            heap.update(heap_smoke, floor_y)
            heap.draw(canvas, floor_y, h)

            # Update and draw heap smoke (wisps from smoldering cigarette peaks)
            active_hsmoke = []
            for sp in heap_smoke:
                if sp.update():
                    active_hsmoke.append(sp)
                    px = int(sp.x)
                    py = int(sp.y)
                    if 0 <= px < w and 0 <= py < h:
                        canvas[py][px] = (sp.char, sp.color)
            heap_smoke = active_hsmoke

            # Spawn new falling pieces periodically
            now = time.time()
            if now >= next_spawn and len(entities) < max(3, w // 18):
                kind = random.choice(kinds)
                if kind == "cigarette":
                    max_x = max(2, w - 17)
                    spawn_x = random.randint(1, max_x)
                elif kind == "java":
                    max_x = max(2, w - 16)
                    spawn_x = random.randint(1, max_x)
                elif kind == "kotlin":
                    max_x = max(2, w - 12)
                    spawn_x = random.randint(2, max_x)
                elif kind == "euro":
                    max_x = max(2, w - 14)
                    spawn_x = random.randint(2, max_x)
                else:  # candle
                    max_x = max(2, w - 9)
                    spawn_x = random.randint(2, max_x)
                entities.append(FallingEntity(spawn_x, kind, floor_y))
                next_spawn = now + random.uniform(0.7, 1.4)

            # Update entities against the rising heap surface
            active_entities = []
            for e in entities:
                alive, surface_y = e.update(heap)
                if alive:
                    active_entities.append(e)
                    e.draw(canvas, w, h)
                else:
                    # Impact triggered directly on top of the rising heap!
                    impacts.append(ImpactAnimation(e.x, surface_y, e.kind))
                    heap.deposit(e.x, e.w, e.kind)
            entities = active_entities

            # Update and draw impacts
            active_impacts = []
            for imp in impacts:
                if imp.update():
                    active_impacts.append(imp)
                imp.draw(canvas, w, h)
            impacts = active_impacts

            # Render canvas to terminal using ANSI buffering
            output = ["\033[H"]
            curr_col = None
            for y in range(h):
                row_str = []
                for x in range(w):
                    ch, col = canvas[y][x]
                    if col != curr_col:
                        row_str.append(fg(col))
                        curr_col = col
                    row_str.append(ch)
                output.append("".join(row_str) + "\n")

            sys.stdout.write("".join(output))
            sys.stdout.flush()

    finally:
        cleanup()

if __name__ == "__main__":
    run_screensaver()
