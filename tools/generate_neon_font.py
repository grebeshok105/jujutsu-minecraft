#!/usr/bin/env python3
"""
Generate a Minecraft bitmap font atlas from a Windows UI TTF.

MC 1.21 BitmapProvider scale (from bytecode):
  cellH = textureHeight / rowCount
  scale = height / cellH
  advance = floor(inkWidth * scale + 0.5) + 1

If CELL != height, glyphs are resampled in-game and look squished/blurry.
Rule: CELL == HEIGHT so scale == 1.0 (pixel-perfect).

Glyphs must be LEFT-aligned: advance is measured from the left edge of the
cell to the rightmost non-zero alpha column.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageFilter

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

# 1:1 bake — no in-game vertical squash.
# 12 ≈ halfway between vanilla 8 and the too-large 16.
CELL = 12
HEIGHT = 12
ASCENT = 10

# Super-sample then downscale for cleaner edges than raw 12px FreeType.
SS = 4  # 48px work cells
LEFT_PAD_SS = 2  # becomes ~0.5–1px after downscale

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
    # Point size so capital H fills most of the work cell.
    face = ImageFont.truetype(str(ttf), size=int(work * 0.78))
    cols = max(len(r) for r in ROWS)
    rows_n = len(ROWS)
    big = Image.new("RGBA", (cols * work, rows_n * work), (0, 0, 0, 0))
    draw = ImageDraw.Draw(big)

    # Shared baseline from "Hg" metrics.
    base = draw.textbbox((0, 0), "Hg", font=face)
    # Target: top of H near top pad, descenders of g fit in cell.
    top_pad = int(work * 0.08)

    for ry, row in enumerate(ROWS):
        padded = row.ljust(cols)
        for cx, ch in enumerate(padded):
            if ch == " ":
                continue
            bbox = draw.textbbox((0, 0), ch, font=face)
            # LEFT-ALIGNED
            x = cx * work + LEFT_PAD_SS - bbox[0]
            y = ry * work + top_pad - base[1]
            draw.text((x, y), ch, font=face, fill=(255, 255, 255, 255))

    # High-quality downscale to final CELL size (scale will be 1.0 in MC).
    final = big.resize(
        (cols * CELL, rows_n * CELL),
        resample=Image.Resampling.LANCZOS,
    )
    # Slight alpha crisp: keep AA but kill near-zero noise that inflates width.
    px = final.load()
    w, h = final.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 24:
                px[x, y] = (255, 255, 255, 0)
            else:
                # Boost mid-alpha so thin strokes stay visible after tint
                a2 = min(255, int(a * 1.15))
                px[x, y] = (255, 255, 255, a2)
    return final


def write_json(cols: int) -> None:
    chars = [r.ljust(cols) for r in ROWS]
    # Vanilla space at h=8 is 4; proportional for h=12 → 6.
    space = max(3, HEIGHT // 2)
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
    assert CELL == HEIGHT, "CELL must equal HEIGHT for scale=1.0"
    assert ASCENT <= HEIGHT, "ascent must be <= height"
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
    print(f"CELL={CELL} HEIGHT={HEIGHT} ASCENT={ASCENT} scale=1.0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
