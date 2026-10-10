#!/usr/bin/env python3
"""Generates the PokerSI adaptive launcher icon as vector drawables.

A pixel-art ace card with the PokerSI robot on it (antenna poking out over
the top edge), on a posterized swirl in cabinet blue, purple and hot pink.
Writes three layers, all on the 108x108dp adaptive-icon canvas:

  res/drawable/ic_launcher_background.xml  swirl
  res/drawable/ic_launcher_foreground.xml  card + robot (inside the 66dp safe zone)
  res/drawable/ic_launcher_monochrome.xml  one-color silhouette for themed icons

and res/mipmap-anydpi/ic_launcher.xml tying them together.

Every pixel is a whole 3dp square, merged into one <path> per color (runs
of pixels per row), so the drawable stays small and crisp.

Usage: python3 tools/icon/gen_icon.py
"""
import math
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
GRID = 54  # 54 x 2dp = 108dp
PX = 2

INK = "#FF130F21"
CREAM = "#FFFFF3D6"
CREAM_SHADE = "#FFE6D2A8"
WHITE = "#FFFFFFF4"
RED = "#FFE8263E"
METAL = "#FFD3DBF0"
METAL_SHADE = "#FF8D97BC"
VISOR = "#FF141834"
YELLOW = "#FFFFF04A"
PINK = "#FFFF3D9A"
GOLD = "#FFFFC22E"
SHADOW = "#66000000"

LEGEND = {
    "o": INK, "c": CREAM, "s": CREAM_SHADE, "w": WHITE, "r": RED, "m": METAL,
    "n": METAL_SHADE, "v": VISOR, "y": YELLOW, "p": PINK, "g": GOLD, "x": SHADOW,
}


def card_art():
    """The foreground: a pixel ace card with the SI robot on it."""
    g = [["." for _ in range(GRID)] for _ in range(GRID)]

    def put(x, y, c):
        if 0 <= x < GRID and 0 <= y < GRID:
            g[y][x] = c

    def rect(x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                put(xx, yy, c)

    def stamp(rows, x, y, flip=False):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != ".":
                    if flip:
                        put(x + len(row) - 1 - i, y + len(rows) - 1 - j, ch)
                    else:
                        put(x + i, y + j, ch)

    # Card: 20 x 26 pixels centred (fits the 66dp safe circle), rounded
    # pixel corners, bevel and a hard shadow.
    cw, ch = 20, 26
    cx, cy = (GRID - cw) // 2, (GRID - ch) // 2 + 1
    rect(cx + 1, cy + 1, cw, ch, "x")
    rect(cx, cy, cw, ch, "o")
    rect(cx + 1, cy + 1, cw - 2, ch - 2, "c")
    for (x, y) in [(cx, cy), (cx + cw - 1, cy), (cx, cy + ch - 1), (cx + cw - 1, cy + ch - 1)]:
        put(x, y, ".")
    put(cx + cw, cy + 1, ".")
    put(cx + 1, cy + ch, ".")
    for (x, y) in [(cx + 1, cy + 1), (cx + cw - 2, cy + 1), (cx + 1, cy + ch - 2), (cx + cw - 2, cy + ch - 2)]:
        put(x, y, "o")
    rect(cx + 2, cy + 1, cw - 4, 1, "w")
    rect(cx + 1, cy + 2, 1, ch - 4, "w")
    rect(cx + 2, cy + ch - 2, cw - 4, 1, "s")
    rect(cx + cw - 2, cy + 2, 1, ch - 4, "s")

    # Corner indices: a red A over a heart, top-left and rotated bottom-right.
    a = [".r.", "r.r", "rrr", "r.r", "r.r"]
    heart = ["r.r", "rrr", ".r."]
    stamp(a, cx + 2, cy + 2)
    stamp(heart, cx + 2, cy + 8)
    stamp(a, cx + cw - 5, cy + ch - 7, flip=True)
    stamp(heart, cx + cw - 5, cy + ch - 11, flip=True)

    # The robot: an 8x7 head (plus ink outline) centred on the card.
    hw, hh = 8, 7
    hx = cx + (cw - hw) // 2
    hy = cy + 6
    rect(hx - 1, hy - 1, hw + 2, hh + 2, "o")
    rect(hx, hy, hw, hh, "m")
    rect(hx, hy + hh - 1, hw, 1, "n")
    rect(hx + hw - 1, hy, 1, hh, "n")
    # Visor with two LED eyes (white glint top-left).
    rect(hx + 1, hy + 1, hw - 2, 3, "v")
    rect(hx + 1, hy + 1, 2, 2, "y")
    rect(hx + hw - 3, hy + 1, 2, 2, "y")
    put(hx + 1, hy + 1, "w")
    put(hx + hw - 3, hy + 1, "w")
    # Grin and pink cheeks.
    put(hx + 1, hy + 4, "o"); put(hx + hw - 2, hy + 4, "o")
    rect(hx + 2, hy + 5, hw - 4, 1, "o")
    put(hx, hy + 4, "p"); put(hx + hw - 1, hy + 4, "p")
    # Antenna: stalk from the head up through the card's top edge, pink ball.
    mid = hx + hw // 2
    for y in range(cy - 4, hy - 1):
        put(mid - 1, y, "o")
        put(mid, y, "n")
    put(mid - 1, cy, "o"); put(mid, cy, "o")
    rect(mid - 2, cy - 8, 4, 4, "o")
    rect(mid - 1, cy - 7, 2, 2, "p")
    put(mid - 1, cy - 7, "w")
    put(mid - 2, cy - 8, "."); put(mid + 1, cy - 8, "."); put(mid - 2, cy - 5, "."); put(mid + 1, cy - 5, ".")
    # "SI" plaque under the head.
    py = hy + hh + 2
    rect(hx - 1, py, hw + 2, 7, "o")
    stamp(["ggg", "g..", "ggg", "..g", "ggg"], hx + 1, py + 1)
    stamp(["ggg", ".g.", ".g.", ".g.", "ggg"], hx + 5, py + 1)
    return ["".join(r) for r in g]


def swirl_background():
    """A posterized swirl (same idea as the in-app SwirlBackground)."""
    a = (0x0A, 0x12, 0x50)
    b = (0x5B, 0x2C, 0xD8)
    c = (0xE5, 0x26, 0x7E)
    rows = []
    cells = {}
    for y in range(GRID):
        row = []
        for x in range(GRID):
            ux = (x + 0.5 - GRID / 2) / GRID * 2
            uy = (y + 0.5 - GRID / 2) / GRID * 2
            r = math.hypot(ux, uy)
            ang = math.atan2(uy, ux) + 0.8 + (1.3 - r) * 2.6
            qx, qy = math.cos(ang) * r * 3, math.sin(ang) * r * 3
            for i in range(4):
                qx, qy = qx + math.sin(qy * 1.25 + i) * 0.6, qy + math.cos(qx * 1.05 + i * 1.7) * 0.6
            v = 0.5 + 0.5 * math.sin(qx * 1.15 + qy * 0.85)
            w = 0.5 + 0.5 * math.cos(math.hypot(qx, qy) * 1.35)
            m = min(1, max(0, v * 0.62 + w * 0.38 - r * 0.1))
            d = [[0, 0.5], [0.75, 0.25]][x % 2][y % 2]
            k = min(1.0, max(0.0, math.floor(m * 5 + d) / 4))
            if k < 0.5:
                f, c0, c1 = k * 2, a, b
            else:
                f, c0, c1 = (k - 0.5) * 2, b, c
            col = tuple(round(c0[i] + (c1[i] - c0[i]) * f) for i in range(3))
            key = "#FF%02X%02X%02X" % col
            ch = cells.setdefault(key, chr(ord("A") + len(cells)))
            row.append(ch)
        rows.append("".join(row))
    legend = {v: k for k, v in cells.items()}
    return rows, legend


def paths(rows, legend, mono=None):
    """One <path> per color: each horizontal run of a color becomes a rect."""
    by_color = {}
    for y, row in enumerate(rows):
        x = 0
        while x < len(row):
            ch = row[x]
            if ch == ".":
                x += 1
                continue
            start = x
            while x < len(row) and row[x] == ch:
                x += 1
            color = mono if mono else legend[ch]
            if mono and ch == "x":
                continue  # no shadow in the silhouette
            by_color.setdefault(color, []).append(
                "M%d,%dh%dv%dh-%dz" % (start * PX, y * PX, (x - start) * PX, PX, (x - start) * PX)
            )
    out = []
    for color, segs in by_color.items():
        # Keep each path short (lint's VectorPath limit is 800 chars).
        chunk = ""
        for seg in segs + [None]:
            if seg is None or len(chunk) + len(seg) > 780:
                if chunk:
                    out.append('    <path\n        android:fillColor="%s"\n        android:pathData="%s" />' % (color, chunk))
                chunk = ""
            if seg is not None:
                chunk += seg
    return "\n".join(out)


def vector(body):
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by tools/icon/gen_icon.py; edit the script, not this file. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n    android:height="108dp"\n'
        '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
        + body
        + "\n</vector>\n"
    )


def monochrome_rows(rows):
    """Line-art silhouette: every ink outline becomes a gap, so the card,
    robot head, visor and plaque read as separate shapes in one color."""
    cut = set("oxv")
    return ["".join("." if ch in cut else ch for ch in row) for row in rows]


def main():
    fg = card_art()
    bg, bg_legend = swirl_background()
    drawable = os.path.join(RES, "drawable")
    mipmap = os.path.join(RES, "mipmap-anydpi")
    os.makedirs(drawable, exist_ok=True)
    os.makedirs(mipmap, exist_ok=True)
    with open(os.path.join(drawable, "ic_launcher_background.xml"), "w") as f:
        f.write(vector(paths(bg, bg_legend)))
    with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w") as f:
        f.write(vector(paths(fg, LEGEND)))
    with open(os.path.join(drawable, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(vector(paths(monochrome_rows(fg), LEGEND, mono="#FFFFFFFF")))
    with open(os.path.join(mipmap, "ic_launcher.xml"), "w") as f:
        f.write(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            "<!-- Generated by tools/icon/gen_icon.py. -->\n"
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
            "</adaptive-icon>\n"
        )
    for row in fg:
        print(row)


if __name__ == "__main__":
    main()
