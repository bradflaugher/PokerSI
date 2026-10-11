#!/usr/bin/env python3
"""Turns the raw JVM renders into the captioned Google Play images.

    tools/screenshots/caption.py phone|tab7|tab10|feature|icon|docs|all

shoot.py renders the raw captures and then runs `all`; run this alone to
change only the captions. It reads build/screenshots/<class>/<scene>.png
(written by StoreScreenshotTest, one file per ScreenshotScenes scene) and
writes:

  phone|tab7|tab10  fastlane/metadata/android/en-US/images/<class dir>/N_scene.png,
                    the size of the capture (exact 9:16, so Play accepts them)
  feature           featureGraphic.png, 1024x500 (drawn whole by the test)
  icon              icon.png (512x512, Play) and docs/screenshots/icon.png (192)
  docs              the uncaptioned README shots in docs/screenshots/

Every image is the cabinet's own look: dark green felt with a gold pinline,
a Press Start 2P headline in video-poker yellow with a hard red shadow and
a gold glow, a VT323 subline in card cream, a strip of marquee bulbs, and
the capture in a rounded bezel with a drop shadow. The fonts are the app's
(app/src/main/res/font). Needs ImageMagick (7's `magick`, or 6's `convert`);
pngquant and optipng shrink the PNGs when they are installed.
"""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RAW = REPO / "build/screenshots"
IMAGES = REPO / "fastlane/metadata/android/en-US/images"
DOCS = REPO / "docs/screenshots"
FONTS = REPO / "app/src/main/res/font"
ARCADE = FONTS / "press_start_2p.ttf"
TERMINAL = FONTS / "vt323.ttf"

if shutil.which("magick"):
    CONVERT = [shutil.which("magick")]
    IDENTIFY = [shutil.which("magick"), "identify"]
elif shutil.which("convert") and shutil.which("identify"):
    CONVERT = [shutil.which("convert")]
    IDENTIFY = [shutil.which("identify")]
else:
    sys.exit("needs ImageMagick (magick, or convert and identify)")
PNGQUANT = shutil.which("pngquant")
OPTIPNG = shutil.which("optipng")

# The app's palette (ui/pixel/PixelTheme.kt): gold, cream, red.
GOLD = "#FFC22E"
GOLD_DARK = "#B9780C"
YELLOW = "#FFF04A"
CREAM = "#FFF3D6"
RED = "#E8263E"
RED_DARK = "#A3101C"
INK = "#130F21"

# Store order: raw scene, headline (one or two lines of at most 13
# characters, Press Start 2P is wide) and subline. Each shows a different
# feature. Scene names are ScreenshotScenes.all's.
CAPTIONS = {
    "phone": [
        ("table_holdem", "POKER VS.\nA TINY AI", "Every rival is an AI running on your phone"),
        ("table_draw", "HOLD AND\nDRAW", "Five-Card Draw, with real cabinet HOLD buttons"),
        ("picker", "SIX GAMES,\nONE CABINET", "Hold'em, Omaha, Draw, Stud, Short Deck, Pineapple"),
        ("table_showdown", "FULL HOUSE!", "...and the trash talk to go with it"),
        ("run_boss_table", "BEAT THE\nBOSS", "Poker Tour: rising blinds, bosses with a twist"),
        ("run_choose", "PICK YOUR\nNEXT TABLE", "Low stakes, high stakes, or the boss"),
        ("models", "BRING YOUR\nOWN BRAIN", "Download a tiny model, or load your own GGUF"),
        ("title", "INSERT\nCOIN", "No ads. No account. No real money."),
    ],
    "tablet": [
        ("table_holdem", "POKER VS.\nA TINY AI", "Every rival is an AI running on your tablet"),
        ("picker", "SIX GAMES,\nONE CABINET", "Hold'em, Omaha, Draw, Stud, Short Deck, Pineapple"),
        ("table_draw", "HOLD AND\nDRAW", "Five-Card Draw, with real cabinet HOLD buttons"),
        ("table_showdown", "FULL HOUSE!", "...and the trash talk to go with it"),
        ("run_boss_table", "BEAT THE\nBOSS", "Poker Tour: rising blinds, bosses with a twist"),
        ("models", "BRING YOUR\nOWN BRAIN", "Download a tiny model, or load your own GGUF"),
    ],
}

# README shots (uncaptioned): docs/screenshots/<name>.png from <class>/<scene>.png.
README_SHOTS = {
    "title": ("phone", "title"),
    "picker": ("phone", "picker"),
    "table-holdem": ("phone", "table_holdem"),
    "draw": ("phone", "table_draw"),
    "showdown": ("phone", "table_showdown"),
    "boss": ("phone", "run_boss_table"),
    "tour": ("phone", "run_choose"),
    "models": ("phone", "models"),
    "tablet": ("tab10", "table_holdem"),
}

DIRS = {"phone": "phoneScreenshots", "tab7": "sevenInchScreenshots", "tab10": "tenInchScreenshots"}


def magick(*args):
    subprocess.run([*CONVERT, *map(str, args)], check=True)


def literal(text):
    """[text] for a label: operand, with ImageMagick's escapes neutralised."""
    text = text.replace("\\", "\\\\").replace("%", "%%")
    return "\\" + text if text.startswith("@") else text


def size_of(path):
    out = subprocess.run([*IDENTIFY, "-format", "%w %h", str(path)], check=True, capture_output=True).stdout
    return tuple(map(int, out.split()))


def text_width(font, size, text):
    out = subprocess.run(
        [*CONVERT, "-font", str(font), "-pointsize", str(size), f"label:{literal(text)}", "-format", "%w", "info:"],
        check=True, capture_output=True,
    ).stdout
    return int(out)


def raw_capture(cls, scene):
    path = RAW / cls / f"{scene}.png"
    if not path.is_file():
        sys.exit(f"missing {path.relative_to(REPO)}: run tools/screenshots/shoot.py (is '{scene}' still in ScreenshotScenes.all?)")
    return path


# The app's table felt (ui/pixel/FeltBackground.kt: FeltColors.Table and FeltArt).
FELT_TONES = ["#021C15", "#053324", "#0A4C34", "#106444", "#187C53"]


def _hash(x, y):
    n = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return (n & 0xFFFF) / 65536


def felt(tmp, w, h, u, pinline=True):
    """The app's pixel felt (the same lamp, rail shadow, lint and twill
    dither as FeltArt), in hard blocks, with a gold pinline inset from the edge."""
    block = max(2, round(8 * u))
    bw, bh = -(-w // block), -(-h // block)
    rgb = [tuple(int(t[i:i + 2], 16) for i in (1, 3, 5)) for t in FELT_TONES]
    tones = [rgb[0]]
    for i in range(1, len(rgb)):
        tones += [tuple((a + b) // 2 for a, b in zip(rgb[i - 1], rgb[i])), rgb[i]]
    steps = len(tones) - 1
    twill = [(4 * ((x + y) % 4) + (0, 2, 1, 3)[x] + 0.5) / 16 for y in range(4) for x in range(4)]
    pixels = bytearray()
    for y in range(bh):
        for x in range(bw):
            dx = (x + 0.5) / bw - 0.5
            dy = ((y + 0.5) / bh - 0.42) * min(bh / bw, 1.8) * 0.75
            lamp = min(1.0, max(0.0, 1 - ((dx * dx + dy * dy) ** 0.5 / 0.75) ** 2))
            edge = min(1.0, min(min(x + 0.5, bw - x - 0.5), min(y + 0.5, bh - y - 0.5)) / bw / 0.09)
            v = (0.10 + lamp * 0.80) * (0.55 + 0.45 * edge)
            if _hash(x, y) > 0.985:
                v += 0.12
            v = min(1.0, max(0.0, v)) * steps
            k = int(v)
            if v - k > twill[(y % 4) * 4 + x % 4]:
                k += 1
            pixels += bytes(tones[min(k, steps)])
    raw = tmp / "felt.ppm"
    raw.write_bytes(b"P6 %d %d 255\n" % (bw, bh) + bytes(pixels))
    out = tmp / "felt.png"
    inset, line = int(22 * u), max(2, int(4 * u))
    radius = int(36 * u)
    args = [raw, "-filter", "point", "-resize", f"{bw * block}x{bh * block}!", "-crop", f"{w}x{h}+0+0", "+repage"]
    if pinline:
        args += [
            "-fill", "none", "-stroke", GOLD_DARK, "-strokewidth", str(line),
            "-draw", f"roundrectangle {inset},{inset} {w - 1 - inset},{h - 1 - inset} {radius},{radius}",
        ]
    magick(*args, out)
    return out


def headline(tmp, name, text, size, u):
    """Yellow arcade text with a hard red drop shadow and a gold glow, on transparent."""
    shadow = max(4, int(6 * u)) // 2 * 2
    blur = int(14 * u)
    pad = 3 * blur
    out = tmp / f"{name}.png"
    magick(
        "-background", "none", "+antialias", "-font", ARCADE, "-pointsize", size,
        # 0: the text, padded so the glow isn't clipped.
        "-fill", YELLOW, f"label:{literal(text)}", "-bordercolor", "none", "-border", f"{pad}x{pad}",
        # 1: the hard shadow, 2: the glow, both made from it.
        "(", "-clone", "0", "-fill", RED_DARK, "-colorize", "100", "-repage", f"+{shadow}+{shadow}", ")",
        "(", "-clone", "0", "-fill", GOLD, "-colorize", "100", "-channel", "A", "-evaluate", "multiply", "0.7", "+channel",
        "-blur", f"0x{blur}", ")",
        "-reverse", "-background", "none", "-layers", "merge", "+repage",
        out,
    )
    return out, pad


def marquee(tmp, width, u, name="marquee"):
    """A red band of chasing marquee bulbs, every other one lit, padded for its glow."""
    h = max(14, int(26 * u))
    r = max(3, int(6.5 * u))
    step = int(34 * u)
    pad = int(10 * u)
    count = max(3, (width - 2 * step) // step + 1)
    x0 = (width - (count - 1) * step) // 2
    cy = pad + h // 2
    bulbs = [f"circle {x0 + i * step},{cy} {x0 + i * step + r},{cy}" for i in range(count)]
    band = tmp / f"{name}-band.png"
    magick(
        "-size", f"{width}x{h + 2 * pad}", "xc:none",
        "-fill", RED, "-stroke", RED_DARK, "-strokewidth", max(2, int(3 * u)),
        "-draw", f"roundrectangle 1,{pad} {width - 2},{pad + h - 1} {h // 2},{h // 2}",
        "-stroke", "none", "-fill", YELLOW, "-draw", " ".join(bulbs[::2]),
        band,
    )
    out = tmp / f"{name}.png"
    magick(
        band,
        "(", "+clone", "-fill", GOLD, "-colorize", "100", "-channel", "A", "-evaluate", "multiply", "0.6", "+channel",
        "-blur", f"0x{int(5 * u)}", ")",
        "+swap", "-background", "none", "-layers", "merge", "+repage",
        "-fill", GOLD_DARK, "-draw", " ".join(bulbs[1::2]),
        out,
    )
    return out, pad


def framed(tmp, raw, width, height, u):
    """The capture scaled into width x height, rounded, in an ink bezel with a gold rim."""
    radius, bezel, rim = int(30 * u), max(4, int(10 * u)), max(2, int(4 * u))
    scaled = tmp / "scaled.png"
    magick(raw, "-filter", "Lanczos", "-resize", f"{width - 2 * (bezel + rim)}x{height - 2 * (bezel + rim)}", scaled)
    w, h = size_of(scaled)
    rounded = tmp / "rounded.png"
    magick(
        scaled, "-alpha", "set", "(", "-size", f"{w}x{h}", "xc:black", "-fill", "white",
        "-draw", f"roundrectangle 0,0 {w - 1},{h - 1} {radius},{radius}", ")",
        "-alpha", "off", "-compose", "CopyOpacity", "-composite", rounded,
    )
    out = tmp / "framed.png"
    b = bezel + rim
    W, H = w + 2 * b, h + 2 * b
    magick(
        "-size", f"{W}x{H}", "xc:none",
        "-fill", GOLD, "-draw", f"roundrectangle 0,0 {W - 1},{H - 1} {radius + b},{radius + b}",
        "-fill", INK, "-draw", f"roundrectangle {rim},{rim} {W - 1 - rim},{H - 1 - rim} {radius + bezel},{radius + bezel}",
        rounded, "-geometry", f"+{b}+{b}", "-compose", "over", "-composite", out,
    )
    return out


def shadowed(tmp, image, u, name="shadowed"):
    """[image] over its soft drop shadow; returns the path and the shadow's top-left offset."""
    out = tmp / f"{name}.png"
    sigma, dy = int(18 * u), int(12 * u)
    magick(
        image, "(", "+clone", "-background", "black", "-shadow", f"75x{sigma}+0+{dy}", ")",
        "+swap", "-background", "none", "-layers", "merge", "+repage", out,
    )
    return out, 2 * sigma


def arcade_size(nominal, lines, width):
    """[nominal], a multiple of 8 (Press Start 2P's pixel grid), shrunk by 8s until every line fits."""
    size = max(8, nominal // 8 * 8)
    while size > 8 and max(text_width(ARCADE, size, line) for line in lines) > width:
        size -= 8
    return size


def fit(font, size, text, width):
    w = text_width(font, size, text)
    return size if w <= width else int(size * width / w)


def caption(raw, out, head_text, sub_text):
    with tempfile.TemporaryDirectory() as t:
        tmp = Path(t)
        w, h = size_of(raw)
        u = min(w, h) / 1080  # 1 on a phone; tablets scale everything up
        text_w = w - int(150 * u)
        lines = head_text.split("\n")

        # Spacing follows the nominal two-line headline, so every image in
        # the set lines up; a one-line headline sits centred in that room.
        head_nominal = arcade_size(int(64 * u), ["X" * 13], 10**6)
        head_size = arcade_size(head_nominal, lines, text_w)
        gap = int(24 * u)
        head_y = int(96 * u)
        head_room = 2 * head_nominal + gap
        sub_nominal = int(60 * u)
        sub_size = fit(TERMINAL, sub_nominal, sub_text, text_w)
        sub_y = head_y + head_room + int(30 * u)
        stripe, stripe_pad = marquee(tmp, int(w * 0.8), u)
        stripe_y = sub_y + int(sub_nominal * 1.05) + int(26 * u)
        _, stripe_h = size_of(stripe)
        shot_top = stripe_y + stripe_h - stripe_pad + int(36 * u)
        shot_h = h - shot_top - int(64 * u)
        frame = framed(tmp, raw, w - int(150 * u), shot_h, u)
        _, frame_h = size_of(frame)
        shot_top += (shot_h - frame_h) // 2
        shot, spread = shadowed(tmp, frame, u)

        layers = [felt(tmp, w, h, u)]
        block_h = len(lines) * head_size + (len(lines) - 1) * gap
        y = head_y + (head_room - block_h) // 2
        for i, line in enumerate(lines):
            img, pad = headline(tmp, f"head{i}", line, head_size, u)
            layers += [img, "-gravity", "north", "-geometry", f"+0+{y - pad}", "-composite"]
            y += head_size + gap
        sub = tmp / "sub.png"
        magick("-background", "none", "-fill", CREAM, "-font", TERMINAL, "-pointsize", sub_size, f"label:{literal(sub_text)}", sub)
        layers += [
            sub, "-gravity", "north", "-geometry", f"+0+{sub_y + (sub_nominal - sub_size) // 2}", "-composite",
            stripe, "-gravity", "north", "-geometry", f"+0+{stripe_y - stripe_pad}", "-composite",
            shot, "-gravity", "north", "-geometry", f"+0+{shot_top - spread}", "-composite",
        ]
        out.parent.mkdir(parents=True, exist_ok=True)
        magick(*layers, "-alpha", "off", f"PNG24:{out}")
    optimize(out)
    print(out.relative_to(REPO))


def feature_graphic():
    """1024x500, drawn whole by StoreScreenshotTest with the app's own pixel
    art (FeatureGraphic.kt: logo over a fanned royal flush, tagline, the
    robot chip talking trash); here it is only flattened and optimized."""
    out = IMAGES / "featureGraphic.png"
    src = raw_capture("feature", "feature")
    w, h = size_of(src)
    if (w, h) != (1024, 500):
        sys.exit(f"feature graphic is {w}x{h}, Play needs 1024x500")
    out.parent.mkdir(parents=True, exist_ok=True)
    magick(src, "-alpha", "off", f"PNG24:{out}")
    optimize(out)
    print(out.relative_to(REPO))


def icons():
    """Play's 512x512 icon (full bleed; Play rounds it), and a rounded 192px one for the README."""
    src = raw_capture("icon", "icon512")
    play = IMAGES / "icon.png"
    play.parent.mkdir(parents=True, exist_ok=True)
    magick(src, "-alpha", "off", "-alpha", "set", f"PNG32:{play}")
    optimize(play, lossy=False)
    print(play.relative_to(REPO))
    readme = DOCS / "icon.png"
    readme.parent.mkdir(parents=True, exist_ok=True)
    magick(
        src, "-filter", "Lanczos", "-resize", "192x192", "-alpha", "set",
        "(", "-size", "192x192", "xc:black", "-fill", "white", "-draw", "roundrectangle 0,0 191,191 44,44", ")",
        "-alpha", "off", "-compose", "CopyOpacity", "-composite", f"PNG32:{readme}",
    )
    optimize(readme, lossy=False)
    print(readme.relative_to(REPO))


def docs_shots():
    DOCS.mkdir(parents=True, exist_ok=True)
    for name, (cls, scene) in README_SHOTS.items():
        out = DOCS / f"{name}.png"
        src = raw_capture(cls, scene)
        w, _ = size_of(src)
        # Tablet captures are big; the README shows them at most ~900px wide.
        resize = ["-filter", "Lanczos", "-resize", "50%"] if w > 1300 else []
        magick(src, *resize, "-alpha", "off", f"PNG24:{out}")
        optimize(out)
        print(out.relative_to(REPO))


def optimize(path, lossy=True):
    """Palette PNG via pngquant (pixel art barely notices), then optipng. Both optional."""
    if lossy and PNGQUANT:
        subprocess.run([PNGQUANT, "--force", "--skip-if-larger", "--strip", "--quality", "80-100",
                        "--speed", "3", "--output", str(path), "--", str(path)], check=False)
    if OPTIPNG:
        subprocess.run([OPTIPNG, "-quiet", "-o1", "-strip", "all", "--", str(path)], check=True)


def store(cls):
    out_dir = IMAGES / DIRS[cls]
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*.png"):
        old.unlink()
    for n, (scene, head_text, sub_text) in enumerate(CAPTIONS["phone" if cls == "phone" else "tablet"], 1):
        caption(raw_capture(cls, scene), out_dir / f"{n}_{scene}.png", head_text, sub_text)


def main():
    which = sys.argv[1] if len(sys.argv) == 2 else None
    targets = {"feature": feature_graphic, "icon": icons, "docs": docs_shots}
    if which == "all":
        for cls in DIRS:
            store(cls)
        for f in targets.values():
            f()
    elif which in DIRS:
        store(which)
    elif which in targets:
        targets[which]()
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
