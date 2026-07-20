#!/usr/bin/env python3
"""Extract specific Apple Color Emoji glyphs (CBDT/CBLC embedded PNGs) to PNG files.

Usage: python tools/render_emoji.py
Reads docs/gui/AppleColorEmoji-Windows.ttf (256MB, gitignored) and writes the needed
glyphs to src/main/resources/assets/jujutsumod/textures/gui/dashboard/. Only the small
PNGs are committed, never the font.
"""
import io
import os
import sys

from fontTools.ttLib import TTFont
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT_PATH = os.path.join(ROOT, "docs", "gui", "AppleColorEmoji-Windows.ttf")
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "jujutsumod",
                       "textures", "gui", "dashboard")

# name -> codepoint
GLYPHS = {
    "bust":        0x1F464,  # 👤 sidebar Character + None portrait
    "swords":      0x2694,   # ⚔ sidebar Combat
    "sparkles":    0x2728,   # ✨ sidebar Visuals
    "gear":        0x2699,   # ⚙ sidebar Misc
    "blue_circle": 0x1F535,  # 🔵 Gojo portrait
    "ogre":        0x1F479,  # 👹 Sukuna portrait
    "wolf":        0x1F43A,  # 🐺 Megumi portrait
    "fist":        0x1F44A,  # 👊 Yuji portrait
    "pin":         0x1F4CC,  # 📌 ability Piercing Nail
    "boom":        0x1F4A5,  # 💥 ability Hairpin/Boom
    "link":        0x1F517,  # 🔗 ability Resonance
    "bolt":        0x26A1,   # ⚡ ability Black Flash
}


def find_png_bytes(font, glyph_name):
    """Return the largest embedded PNG for a glyph name across all CBDT strikes."""
    cbdt = font["CBDT"]
    best = None
    best_size = -1
    for strike in cbdt.strikeData:
        if glyph_name not in strike:
            continue
        raw = strike[glyph_name].data
        idx = raw.find(b"\x89PNG\r\n\x1a\n")
        if idx < 0:
            continue
        png = raw[idx:]
        # Peek dimensions from the PNG header (width is bytes 16-20, big-endian).
        w = int.from_bytes(png[16:20], "big")
        if w > best_size:
            best_size = w
            best = png
    return best


def main():
    if not os.path.exists(FONT_PATH):
        print(f"Font not found: {FONT_PATH}", file=sys.stderr)
        return 1
    os.makedirs(OUT_DIR, exist_ok=True)

    print(f"Loading {FONT_PATH} (this may take a moment)...")
    font = TTFont(FONT_PATH, fontNumber=0, lazy=True)
    cmap = font.getBestCmap()

    ok, missing = 0, []
    for name, cp in GLYPHS.items():
        glyph_name = cmap.get(cp)
        if glyph_name is None:
            missing.append((name, cp))
            continue
        png = find_png_bytes(font, glyph_name)
        if png is None:
            missing.append((name, cp))
            continue
        out = os.path.join(OUT_DIR, f"emoji_{name}.png")
        img = Image.open(io.BytesIO(png)).convert("RGBA")
        img.save(out)
        print(f"  {name} U+{cp:04X} -> {os.path.relpath(out, ROOT)} ({img.width}x{img.height})")
        ok += 1

    if missing:
        print("\nMissing glyphs:", file=sys.stderr)
        for name, cp in missing:
            print(f"  {name} U+{cp:04X}", file=sys.stderr)
    print(f"\nDone: {ok} extracted, {len(missing)} missing.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
