#!/usr/bin/env python3
"""
Generate a Minecraft *bitmap* font atlas from a system TTF.

Critical Minecraft bitmap rules (wiki + observed bugs):
1. Texture is split into equal cells by rows/cols of `chars`.
2. Glyph advance width = distance from LEFT edge of cell to rightmost
   non-zero alpha column.  => glyphs MUST be left-aligned (not centered),
   otherwise every letter gets huge left padding baked into advance width
   and text looks like "N o b a r a".
3. `height` should match cell height or a clean scale of it (wiki).
4. White glyphs recolor in-game; transparent = empty.

We bake HD cells (CELL) and set height slightly above vanilla 8 so Segoe
stays readable without looking giant (vanilla~8, previous bad=16, now=11).
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
    Path(r"C:\Windows\Fonts\calibri.ttf"),
]

# Bake high-res, display slightly larger than vanilla 8.
CELL = 24
HEIGHT = 11
ASCENT = 9
PT = 17  # fits in 24px cell with 1px left pad
LEFT_PAD = 1
TOP_BIAS = 1  # nudge down a hair inside the cell

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
    raise SystemExit("No system TTF found among candidates")


def render_atlas(ttf: Path) -> tuple[Image.Image, list[str]]:
    face = ImageFont.truetype(str(ttf), size=PT)
    cols = max(len(r) for r in ROWS)
    rows_n = len(ROWS)
    img = Image.new("RGBA", (cols * CELL, rows_n * CELL), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Shared vertical band from capital H so baseline is consistent.
    h_box = draw.textbbox((0, 0), "Hg", font=face)
    band_top = h_box[1]
    band_h = h_box[3] - h_box[1]

    for ry, row in enumerate(ROWS):
        padded = row.ljust(cols)
        for cx, ch in enumerate(padded):
            if ch == " ":
                continue  # space width comes from space provider
            bbox = draw.textbbox((0, 0), ch, font=face)
            # LEFT-ALIGNED inside cell (required for correct MC auto-width).
            x = cx * CELL + LEFT_PAD - bbox[0]
            # Vertical: place capital band near top of cell with small pad.
            y = ry * CELL + TOP_BIAS - band_top + max(0, (CELL - band_h - TOP_BIAS * 2) // 4)
            draw.text((x, y), ch, font=face, fill=(255, 255, 255, 255))

    return img, [r.ljust(cols) for r in ROWS]


def write_json(chars: list[str]) -> None:
    # Space advance ~ half an average glyph at height 11 (vanilla space is 4 at h=8).
    space_w = 4
    doc = {
        "providers": [
            {
                "type": "space",
                "advances": {
                    " ": space_w,
                },
            },
            {
                "type": "bitmap",
                "file": "jujutsumod:font/neon.png",
                "height": HEIGHT,
                "ascent": ASCENT,
                "chars": chars,
            },
            {
                "type": "reference",
                "id": "minecraft:include/unifont",
            },
        ]
    }
    FONT_JSON.parent.mkdir(parents=True, exist_ok=True)
    FONT_JSON.write_text(
        json.dumps(doc, indent=2, ensure_ascii=True) + "\n",
        encoding="ascii",
    )


def main() -> int:
    ttf = pick_font()
    print(f"source font: {ttf}")
    atlas, chars = render_atlas(ttf)
    TEX_DIR.mkdir(parents=True, exist_ok=True)
    atlas.save(TEX_PNG, format="PNG", optimize=True)
    write_json(chars)
    old_ttf = FONT_JSON.parent / "neon.ttf"
    if old_ttf.exists():
        old_ttf.unlink()
        print(f"removed stale {old_ttf.name}")
    print(f"wrote {TEX_PNG.relative_to(ROOT)} ({TEX_PNG.stat().st_size} bytes) {atlas.size}")
    print(f"wrote {FONT_JSON.relative_to(ROOT)}")
    print(f"cell={CELL} height={HEIGHT} ascent={ASCENT} left_pad={LEFT_PAD}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
