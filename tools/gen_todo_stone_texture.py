#!/usr/bin/env python3
"""Generates the Todo stone entity texture: a small tumbling gray stone on a 16x16 atlas.

Hand-drawn programmatically (no borrowed assets): an irregular pebble silhouette with a
light source top-left, a soft ambient-occlusion rim bottom-right, one crack line and a
few speckle pores. Palette stays within a restrained gray ramp so the stone reads as
plain rock against the violet/clap VFX language of the rest of the kit.

The silhouette is coverage-sampled at 8x8 subsamples per texel (box average, no
resampling filters), so the alpha edge is clean and the shading is per-texel baked.

Usage: python tools/gen_todo_stone_texture.py  (writes src/main/resources/assets/jujutsumod/textures/entity/todo_stone.png)
"""
from pathlib import Path

from PIL import Image

SIZE = 16
SUBS = 8
OUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/jujutsumod/textures/entity/todo_stone.png"

DARK = (48, 50, 53)
LIGHT = (164, 168, 174)
CENTER = (8.0, 8.0)

import math

# Asymmetric radius modulation: a generous bulge toward the lower-left and a flatter
# upper-right make the silhouette read as a natural pebble rather than a rounded tile.
def radius_at(angle: float) -> float:
    return 6.5 * (1.0 + 0.20 * math.cos(angle - 2.35) + 0.06 * math.cos(2.0 * angle + 1.1))


def in_pebble(x: float, y: float) -> bool:
    dx, dy = x - CENTER[0], y - CENTER[1]
    angle = math.atan2(dy, dx)
    return dx * dx + dy * dy <= radius_at(angle) ** 2


def crack_at(x: float, y: float) -> float:
    """Distance to the crack polyline; the dark core and bright lower rim read off it."""
    points = [(4.6, 11.6), (6.6, 9.6), (6.8, 7.4), (8.6, 5.2), (9.9, 3.6)]
    best = 1e9
    for (x0, y0), (x1, y1) in zip(points, points[1:]):
        vx, vy = x1 - x0, y1 - y0
        length_sq = vx * vx + vy * vy
        t = max(0.0, min(1.0, ((x - x0) * vx + (y - y0) * vy) / length_sq))
        px, py = x0 + t * vx, y0 + t * vy
        best = min(best, ((x - px) ** 2 + (y - py) ** 2) ** 0.5)
    return best


def pore_dist(x: float, y: float) -> float:
    """Distance to the nearest speckle pore center, or large."""
    pores = [(3.2, 4.4), (11.4, 10.4), (12.6, 5.0), (4.6, 12.8), (9.0, 7.6)]
    return min(((x - px) ** 2 + (y - py) ** 2) ** 0.5 for px, py in pores)


def smoothstep(edge0: float, edge1: float, value: float) -> float:
    t = max(0.0, min(1.0, (value - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def main() -> None:
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for tex_y in range(SIZE):
        for tex_x in range(SIZE):
            r = g = b = 0
            a = 0
            for sy in range(SUBS):
                for sx in range(SUBS):
                    x = tex_x + (sx + 0.5) / SUBS
                    y = tex_y + (sy + 0.5) / SUBS
                    if not in_pebble(x, y):
                        continue
                    # Distance from the top-left light source, normalized to the pebble's
                    # local radius so the falloff follows the silhouette.
                    dx, dy = x - CENTER[0], y - CENTER[1]
                    angle = math.atan2(dy, dx)
                    local_radius = radius_at(angle)
                    d = math.sqrt((x - 6.1) ** 2 + (y - 5.4) ** 2) / (local_radius * 1.15)
                    # Diffuse falloff plus a small specular cap under the light source.
                    light = max(0.0, 1.0 - d ** 1.7) * 0.82 + 0.30 * math.exp(-d * 3.2)
                    # Ambient occlusion toward the silhouette edge.
                    light *= 1.0 - 0.42 * smoothstep(0.72, 1.0, d)
                    # Bottom-right bias: the occluded side reads darker than the lit side.
                    light *= 1.0 - 0.18 * smoothstep(0.0, 1.0, (angle + math.pi) / (2.0 * math.pi))
                    # Crack: dark core line with a faint bright rim on the lower side.
                    crack = crack_at(x, y)
                    if crack < 0.5:
                        light = min(light, 0.18 + crack * 0.30)
                    elif crack < 1.0 and y > 8.2:
                        light = min(1.0, light + 0.10)
                    # Speckle pores: darker dots that fade at their edges.
                    pore = pore_dist(x, y)
                    light *= 1.0 - 0.45 * smoothstep(0.4, 1.2, pore)
                    col = tuple(round(DARK[i] + (LIGHT[i] - DARK[i]) * min(1.0, light))
                                for i in range(3))
                    r += col[0]
                    g += col[1]
                    b += col[2]
                    a += 255
            if a == 0:
                px[tex_x, tex_y] = (0, 0, 0, 0)
            else:
                n = SUBS * SUBS
                px[tex_x, tex_y] = (r // n, g // n, b // n, a // n)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
