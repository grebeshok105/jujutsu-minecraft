#!/usr/bin/env python3
"""
Minecraft bitmap font atlas from Windows UI TTF (Segoe).

MC 1.21 BitmapProvider:
  scale = height / cellHeight
  advance = round(inkWidth * scale) + 1

Rules that actually look good in-game:
1. CELL == HEIGHT  → scale 1.0 (no squash/warp).
2. Glyphs LEFT-aligned (width measured from cell left edge).
3. Enough pixels per glyph: height 12 looks "144p"; 14–16 is the sweet zone.
4. Super-sample offline then LANCZOS down — FreeType at native 14px is muddy.

height history:
  16 — too large for this UI
  12 — scale-correct but pixelated
  14 — target: smooth enough, not huge
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "src" / "main" / "resources" / "assets" / "jujutsumod"
FONT_JSON = OUT_DIR / "font" / "neon.json"
TEX_DIR = OUT_DIR / "textures" / "font"
TEX_PNG = TEX_DIR / "neon.png"

FONT_CANDIDATES = [
    Path(r"C:\Windows\Fonts\segoeuisl.ttf"),
    Path(r"C:\Windows\Fonts\segoeui.ttf"),
    Path(r"C:\Windows\Fonts\arial.ttf"),
]

CELL = 14
HEIGHT = 14
ASCENT = 11
SS = 4  # work cell = 56px
LEFT_PAD_SS = 3

ROWS: list[str] = [
    " !\"#$%&'()*+,-./",
    "0123456789:;<=>?",
    "@ABCDEFGHIJKLMNO",
    "PQRSTUVWXYZ[\\]^_",
    "`abcdefghijklmno",
    "pqrstuvwxyz{|}~.",
]


def pick_font() -> Path:
    for p in FONT_CANDIDATES:
        if p.is_file():
            return p
    raise SystemExit("No system TTF found")


def render_supersampled(ttf: Path) -> Image.Image:
    work = CELL * SS
    # Fill most of the cell; leave room for descenders (g, y, p, q).
    face = ImageFont.truetype(str(ttf), size=int(work * 0.88))
    cols = max(len(r) for r in ROWS)
    rows_n = len(ROWS)
    big = Image.new("RGBA", (cols * work, rows_n * work), (0, 0, 0, 0))
    draw = ImageDraw.Draw(big)

    band = draw.textbbox((0, 0), "Hg", font=face)
    band_h = band[3] - band[1]
    # Bias slightly up so capitals sit high; g descender still fits.
    top_pad = max(2, int((work - band_h) * 0.28))

    for ry, row in enumerate(ROWS):
        padded = row.ljust(cols)
        for cx, ch in enumerate(padded):
            if ch == " ":
                continue
            bbox = draw.textbbox((0, 0), ch, font=face)
            x = cx * work + LEFT_PAD_SS - bbox[0]
            y = ry * work + top_pad - band[1]
            draw.text((x, y), ch, font=face, fill=(255, 255, 255, 255))

    final = big.resize((cols * CELL, rows_n * CELL), resample=Image.Resampling.LANCZOS)

    # Clean near-zero alpha (stops inflated advances) but keep soft edges.
    px = final.load()
    w, h = final.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 18:
                px[x, y] = (255, 255, 255, 0)
            else:
                px[x, y] = (255, 255, 255, min(255, int(20 + a * 0.92)))
    return final


def write_json(cols: int) -> None:
    chars = [r.ljust(cols) for r in ROWS]
    space = 5  # ~vanilla-ish relative spacing at h=14
    doc = {
        "providers": [
            {"type": "space", "advances": {" ": space}},
            {
                "type": "bitmap",
                "file": "jujutsumod:font/neon.png",
                "height": HEIGHT,
                "ascent": ASCENT,
                "chars": chars,
            },
            {"type": "reference", "id": "minecraft:include/unifont"},
        ]
    }
    FONT_JSON.parent.mkdir(parents=True, exist_ok=True)
    FONT_JSON.write_text(json.dumps(doc, indent=2, ensure_ascii=True) + "\n", encoding="ascii")


def main() -> int:
    assert CELL == HEIGHT, "CELL must equal HEIGHT (scale=1.0)"
    assert ASCENT <= HEIGHT
    ttf = pick_font()
    print(f"source: {ttf}")
    atlas = render_supersampled(ttf)
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    atlas.save(TEX_PNG, format="PNG", optimize=True)
    write_json(max(len(r) for r in ROWS))
    old = FONT_JSON.parent / "neon.ttf"
    if old.exists():
        old.unlink()
    print(f"atlas {atlas.size} bytes={TEX_PNG.stat().st_size}")
    print(f"CELL={CELL} HEIGHT={HEIGHT} ASCENT={ASCENT} scale=1.0 SS={SS}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
