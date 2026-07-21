#!/usr/bin/env python3
"""
Generate a Minecraft *bitmap* font atlas from a system TTF.

Why bitmap (not TTF) — deep research summary (MC 1.21 Font + FreeType):
1. Vanilla UI never uses TTF; default is bitmap (Mojangles) + unifont fallback.
2. TTF provider strips kerning/ligatures (wiki). FreeType AA at GUI scale looks muddy.
3. TTF `file` is relative to assets/<ns>/font/ and MC auto-prefixes `font/` (double-path trap).
4. Bitmap glyphs are pixel-stable, auto-width from rightmost alpha, recolorable if white.
5. Quality path used by real packs: bake TTF offline → PNG atlas → bitmap provider.

Bitmap texture path: assets/<ns>/textures/font/neon.png
JSON: assets/<ns>/font/neon.json  with file "ns:font/neon.png"
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

# 32px source cells, rendered at height 10 (close to vanilla 8, still smoother HD bake).
CELL = 32
HEIGHT = 10
ASCENT = 8
PT = 22  # FreeType point size into the 32px cell

# Pure ASCII only — homogeneous metrics, no fallback tofu for Latin UI.
# 16 columns × 6 rows = 96 glyphs covering 0x20–0x7E fully.
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

    # Pre-measure capital H for vertical centering baseline.
    sample = draw.textbbox((0, 0), "H", font=face)
    sample_h = sample[3] - sample[1]

    for ry, row in enumerate(ROWS):
        padded = row.ljust(cols)
        for cx, ch in enumerate(padded):
            bbox = draw.textbbox((0, 0), ch, font=face)
            gw = max(1, bbox[2] - bbox[0])
            # Horizontal center; vertical align to capital baseline band.
            x = cx * CELL + (CELL - gw) // 2 - bbox[0]
            y = ry * CELL + (CELL - sample_h) // 2 - sample[1]
            # Pure white RGB, alpha from FreeType raster — MC multiplies by text color.
            draw.text((x, y), ch, font=face, fill=(255, 255, 255, 255))

    return img, [r.ljust(cols) for r in ROWS]


def write_json(chars: list[str]) -> None:
    doc = {
        "providers": [
            {
                "type": "space",
                "advances": {
                    " ": 5,
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
    print(f"cell={CELL} height={HEIGHT} ascent={ASCENT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
