#!/usr/bin/env python3
"""Generates the PokerSI adaptive launcher icon as vector drawables.

A big red casino chip with the PokerSI robot's face on its inlay (a heart
on the antenna, one eye winking: it has a hand) on lamp-lit green felt.
A round chip fills the round safe zone better than any other poker
shape, and its white edge spots read on every wallpaper.

Writes three layers, all on the 108x108dp adaptive-icon canvas:

  res/drawable/ic_launcher_background.xml  felt
  res/drawable/ic_launcher_foreground.xml  chip + robot (inside the 66dp safe zone)
  res/drawable/ic_launcher_monochrome.xml  one-color cut-out for themed icons

and res/mipmap-anydpi/ic_launcher.xml tying them together.

The art is a 72x72 pixel grid (1.5dp pixels: the launcher shows 48 of
them, the safe circle is 44 across). Every pixel is a whole square,
merged into one <path> per color (runs of pixels per row), so the drawable
stays small and crisp at any size. Light comes from the top left, as in
the rest of the app's pixel art.

Usage: python3 tools/icon/gen_icon.py [--preview out.png]
  --preview also writes a PNG preview (needs ImageMagick) of the icon
  under circle, squircle and themed masks at large and launcher sizes.
"""
import math
import os
import subprocess
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
GRID = 72  # 72 x 1.5dp = 108dp
PX = 1.5
C = 35.5   # centre of the grid, in pixel coordinates (pixel centres are i + 0.5)

# A limited palette: ink, two felt-shadow tones, chip reds, cream inlay, robot metal.
COLORS = {
    "o": "#FF130F21",  # ink outline
    "x": "#8C021710",  # drop shadow on the felt
    "R": "#FFE8263E",  # chip red
    "r": "#FFA3101C",  # chip red, shade
    "h": "#FFFF6A6A",  # chip red, lit
    "W": "#FFFFF3D6",  # edge spot / inlay cream
    "w": "#FFFFFFF4",  # edge spot lit
    "s": "#FFE6C79A",  # cream shade
    "g": "#FFFFC22E",  # gold
    "G": "#FFB9780C",  # gold, shade
    "m": "#FFD3DBF0",  # metal
    "n": "#FF8D97BC",  # metal, shade
    "M": "#FFFFFFFF",  # metal glint
    "v": "#FF141834",  # visor
    "y": "#FFFFF04A",  # LED eyes
    "Y": "#FFFFB21E",  # LED eyes, lower half
    "p": "#FFFF3D9A",  # pink cheeks / heart
    "P": "#FFA81563",  # heart, shade
}


class Grid:
    def __init__(self):
        self.g = [["." for _ in range(GRID)] for _ in range(GRID)]

    def put(self, x, y, c):
        if 0 <= x < GRID and 0 <= y < GRID:
            self.g[y][x] = c

    def get(self, x, y):
        return self.g[y][x] if 0 <= x < GRID and 0 <= y < GRID else "."

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, c)

    def stamp(self, rows, x, y):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch != ".":
                    self.put(x + i, y + j, ch)

    def outline(self, c, keep=None):
        """1px 4-connected outline around every opaque pixel."""
        src = [row[:] for row in self.g]
        for y in range(GRID):
            for x in range(GRID):
                if src[y][x] != ".":
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < GRID and 0 <= yy < GRID and src[yy][xx] != "." and (keep is None or src[yy][xx] in keep):
                        self.g[y][x] = c
                        break

    def rows(self):
        return ["".join(r) for r in self.g]


def ink_around(g, mono, cells):
    """A 1px ink outline around a set of cells, drawn over whatever is there."""
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in cells:
                g.put(x + dx, y + dy, "o"); mono.put(x + dx, y + dy, ".")


def chip_art():
    """The foreground (chip, robot inlay, hard shadow) and its themed-icon cut-out."""
    g = Grid()
    mono = Grid()  # '#' = ink of the themed icon
    cx, cy = C, C + 2  # sit low: the antenna's heart needs the room above
    R = 19.1           # outer radius of the chip body (outline outside it)
    GROOVE, RING, INLAY = 14.3, 13.3, 12.1
    spots = 8
    for y in range(GRID):
        for x in range(GRID):
            px, py = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(px, py)
            if d > R:
                continue
            ang = (math.degrees(math.atan2(py, px)) + 360 + 8 - 22.5) % 360
            # Lighting: a dot product with a top-left light, strongest at the rim.
            lit = (-px - py) / (math.sqrt(2) * max(d, 0.001))
            outer = d > R - 2.2
            in_spot = d > GROOVE and (ang % (360 / spots)) < 16
            if d > GROOVE:
                if in_spot:
                    c = "w" if lit > 0.5 and outer else ("s" if lit < -0.5 and outer else "W")
                else:
                    c = "h" if lit > 0.55 and outer else ("r" if lit < -0.4 and outer else "R")
                mono.put(x, y, "." if in_spot else "#")
            elif d > RING:
                c = "r"  # groove between the rim and the inlay ring
                mono.put(x, y, ".")
            elif d > INLAY:
                c = "g" if lit > -0.2 else "G"
                mono.put(x, y, "#")
            else:
                c = "s" if (px + py) > 2 and d > INLAY - 1.6 else "W"
                mono.put(x, y, ".")
            g.put(x, y, c)

    # The robot: a big head filling the inlay.
    hw, hh = 17, 13
    hx = int(cx - hw / 2 + 0.5)
    hy = int(cy - hh / 2 + 1.5)
    # Ear bolts.
    g.rect(hx - 1, hy + 4, 1, 4, "n"); mono.rect(hx - 1, hy + 4, 1, 4, "#")
    g.rect(hx + hw, hy + 4, 1, 4, "n"); mono.rect(hx + hw, hy + 4, 1, 4, "#")
    g.rect(hx, hy, hw, hh, "m")
    mono.rect(hx, hy, hw, hh, "#")
    for (x, y) in ((hx, hy), (hx + hw - 1, hy), (hx, hy + hh - 1), (hx + hw - 1, hy + hh - 1)):
        g.put(x, y, "W"); mono.put(x, y, ".")
    g.rect(hx + 1, hy + hh - 1, hw - 2, 1, "n")
    g.rect(hx + hw - 1, hy + 1, 1, hh - 2, "n")
    g.rect(hx + 1, hy, hw - 2, 1, "M")
    g.rect(hx, hy + 1, 1, hh - 2, "M")
    # Visor.
    g.rect(hx + 2, hy + 2, hw - 4, 6, "v"); mono.rect(hx + 2, hy + 2, hw - 4, 6, ".")
    for (x, y) in ((hx + 2, hy + 2), (hx + hw - 3, hy + 2), (hx + 2, hy + 7), (hx + hw - 3, hy + 7)):
        g.put(x, y, "m"); mono.put(x, y, "#")
    # Left eye open (a 3x4 LED with a lit corner), right eye winking (a ^ arc).
    g.rect(hx + 4, hy + 3, 3, 4, "y"); mono.rect(hx + 4, hy + 3, 3, 4, "#")
    g.rect(hx + 4, hy + 6, 3, 1, "Y")
    g.put(hx + 4, hy + 3, "M")
    for (x, y) in ((hx + 9, hy + 6), (hx + 10, hy + 5), (hx + 11, hy + 4), (hx + 12, hy + 5), (hx + 13, hy + 6)):
        g.put(x, y, "y"); mono.put(x, y, "#")
    # Cheeks and a lopsided grin.
    g.put(hx + 2, hy + 9, "p"); g.put(hx + hw - 3, hy + 9, "p")
    for (x, y, w) in ((hx + 4, hy + 9, 1), (hx + 5, hy + 10, 7), (hx + 12, hy + 9, 1), (hx + 13, hy + 8, 1)):
        g.rect(x, y, w, 1, "o"); mono.rect(x, y, w, 1, ".")
    # Antenna: a stalk up out of the head, over the rim, to an outlined heart.
    mid = hx + hw // 2
    top = hy - 9
    heart = [
        ".pp.pp.",
        "pMpppPP",
        "pppppPP",
        ".pppPP.",
        "..pPP..",
        "...P...",
    ]
    hgx, hgy = mid - 3, top - 3
    heart_cells = {(hgx + i, hgy + j) for j, row in enumerate(heart) for i, ch in enumerate(row) if ch != "."}
    stalk = {(x, y) for y in range(hgy + len(heart), hy) for x in (mid - 1, mid)}
    head = {(x, y) for y in range(hy, hy + hh) for x in range(hx, hx + hw)}
    ears = {(x, y) for y in range(hy + 4, hy + 8) for x in (hx - 1, hx + hw)}
    ink_around(g, mono, heart_cells | stalk | head | ears)
    for (x, y) in stalk:
        g.put(x, y, "n" if x == mid else "m"); mono.put(x, y, "#")
    g.stamp(heart, hgx, hgy)
    mono.stamp(["".join("#" if ch != "." else "." for ch in r) for r in heart], hgx, hgy)

    # A glint on the lit rim: a four-point sparkle.
    sx, sy = int(cx - 12), int(cy - 13)
    for (dx, dy, c) in ((0, 0, "M"), (1, 0, "w"), (-1, 0, "w"), (0, 1, "w"), (0, -1, "w"), (2, 0, "W"), (-2, 0, "W"), (0, 2, "W"), (0, -2, "W")):
        g.put(sx + dx, sy + dy, c)
    g.outline("o")
    # Hard drop shadow down-right (felt shows through, so it is translucent).
    for y in range(GRID - 1, -1, -1):
        for x in range(GRID - 1, -1, -1):
            if g.get(x, y) == "." and g.get(x - 1, y - 2) not in (".", "x"):
                g.put(x, y, "x")
    return g.rows(), mono.rows()


FELT_GRID = 36  # the felt is drawn in 3dp blocks: two art pixels, as in the app


def felt_background():
    """Lamp-lit green felt like the app's FeltBackground: the Table tones and
    their midpoints, twill-dithered, in blocks twice the art's pixel size."""
    base = ["#FF021C15", "#FF053324", "#FF0A4C34", "#FF106444", "#FF187C53"]
    tones = [base[0]]
    for a, b in zip(base, base[1:]):
        mid = "#FF" + "".join("%02X" % ((int(a[i:i + 2], 16) + int(b[i:i + 2], 16)) // 2) for i in (3, 5, 7))
        tones += [mid, b]
    twill = [(4 * ((x + y) % 4) + (0, 2, 1, 3)[x] + 0.5) / 16 for y in range(4) for x in range(4)]
    rows = []
    for y in range(FELT_GRID):
        row = []
        for x in range(FELT_GRID):
            dx = (x + 0.5) / FELT_GRID - 0.5
            dy = (y + 0.5) / FELT_GRID - 0.40
            lamp = max(0.0, 1 - (math.hypot(dx, dy) / 0.55) ** 2)
            v = min(1.0, max(0.0, 0.06 + lamp * 0.92)) * (len(tones) - 1)
            k = math.floor(v)
            if v - k > twill[(y % 4) * 4 + x % 4]:
                k += 1
            row.append(chr(ord("A") + min(k, len(tones) - 1)))
        rows.append("".join(row))
    legend = {chr(ord("A") + i): t for i, t in enumerate(tones)}
    return rows, legend


def fmt(v):
    return ("%.1f" % v).rstrip("0").rstrip(".")


def paths(rows, legend, mono=None, px=PX):
    """One <path> per color: horizontal runs of a color, merged downwards
    with identical runs on the rows below into rectangles (fewer, larger
    shapes: shorter paths and no hairline seams between rows)."""
    runs = {}  # (color, x, width) -> list of rows
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
            runs.setdefault((color, start, x - start), []).append(y)
    by_color = {}
    for (color, start, w), ys in sorted(runs.items(), key=lambda kv: (kv[1][0], kv[0][1])):
        top = prev = ys[0]
        for y in ys[1:] + [None]:
            if y is not None and y == prev + 1:
                prev = y
                continue
            by_color.setdefault(color, []).append((top, start, w, prev - top + 1))
            if y is not None:
                top = prev = y
    out = []
    for color, rects in by_color.items():
        segs = ["M%s,%sh%sv%sh-%sz" % (fmt(x * px), fmt(y * px), fmt(w * px), fmt(h * px), fmt(w * px))
                for (y, x, w, h) in sorted(rects)]
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


def preview(fg, bg, mono, path):
    """A PNG contact sheet: circle, squircle and themed masks, large and at 48dp-ish."""
    with tempfile.TemporaryDirectory() as t:
        def ppm(name, rows_list, legend_list):
            p = os.path.join(t, name + ".ppm")
            with open(p, "w") as f:
                f.write("P3\n%d %d\n255\n" % (GRID, GRID))
                for y in range(GRID):
                    for x in range(GRID):
                        col = None
                        for rows, legend in zip(rows_list, legend_list):
                            ch = rows[y][x]
                            if ch == ".":
                                continue
                            argb = legend[ch]
                            a = int(argb[1:3], 16) / 255
                            rgb = [int(argb[3 + i * 2:5 + i * 2], 16) for i in range(3)]
                            col = rgb if col is None else [int(col[i] * (1 - a) + rgb[i] * a) for i in range(3)]
                        f.write("%d %d %d\n" % tuple(col or (40, 48, 68)))
            return p
        full = ppm("full", [bg[0], fg], [bg[1], COLORS])
        themed = ppm("themed", [mono], [{"#": "#FFC9D6FF"}])
        conv = "magick" if subprocess.run(["which", "magick"], capture_output=True).returncode == 0 else "convert"
        cells = []
        for src, name in ((full, "full"), (themed, "themed")):
            # The launcher shows the middle 48 of 72 pixels.
            crop = os.path.join(t, name + "-crop.png")
            subprocess.run([conv, src, "-crop", "48x48+12+12", "+repage", crop], check=True)
            for size, shape in ((288, "circle"), (288, "squircle"), (96, "circle"), (64, "squircle"), (48, "circle")):
                if name == "themed" and shape == "squircle" and size == 288:
                    continue
                out = os.path.join(t, "%s-%d-%s.png" % (name, size, shape))
                r = size // 2 if shape == "circle" else size // 4
                subprocess.run([
                    conv, crop, "-filter", "point" if size % 48 == 0 else "triangle", "-resize", "%dx%d" % (size, size),
                    "(", "-size", "%dx%d" % (size, size), "xc:black", "-fill", "white",
                    "-draw", "roundrectangle 0,0 %d,%d %d,%d" % (size - 1, size - 1, r, r), ")",
                    "-alpha", "off", "-compose", "CopyOpacity", "-composite", out,
                ], check=True)
                cells.append(out)
        rows = []
        for i, part in enumerate((cells[:5], cells[5:])):
            row = os.path.join(t, "row%d.png" % i)
            subprocess.run([conv, "-background", "#5A6F8C", *part, "-gravity", "center",
                            "-bordercolor", "#5A6F8C", "-border", "12", "+append", row], check=True)
            rows.append(row)
        subprocess.run([conv, "-background", "#5A6F8C", *rows, "-gravity", "west", "-append", path], check=True)


def main():
    fg, mono = chip_art()
    bg, bg_legend = felt_background()
    if "--preview" in sys.argv:
        k = GRID // FELT_GRID
        bg_px = ["".join(ch * k for ch in row) for row in bg for _ in range(k)]
        preview(fg, (bg_px, bg_legend), mono, sys.argv[sys.argv.index("--preview") + 1])
    drawable = os.path.join(RES, "drawable")
    mipmap = os.path.join(RES, "mipmap-anydpi")
    os.makedirs(drawable, exist_ok=True)
    os.makedirs(mipmap, exist_ok=True)
    with open(os.path.join(drawable, "ic_launcher_background.xml"), "w") as f:
        f.write(vector(paths(bg, bg_legend, px=108 / FELT_GRID)))
    with open(os.path.join(drawable, "ic_launcher_foreground.xml"), "w") as f:
        f.write(vector(paths(fg, COLORS)))
    with open(os.path.join(drawable, "ic_launcher_monochrome.xml"), "w") as f:
        f.write(vector(paths(mono, {}, mono="#FFFFFFFF")))
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
    worst = max(math.hypot(x + 0.5 - 36, y + 0.5 - 36) for y, row in enumerate(fg) for x, ch in enumerate(row) if ch not in ".x")
    print("foreground reaches %.1f of the 22-pixel safe radius" % worst)


if __name__ == "__main__":
    main()
